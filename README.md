# TechDevHub

> 面向计算机专业学生的技术社区 + AI Agent 平台

## 项目简介

TechDevHub 旨在解决高校课程体系与就业实际脱节的问题，为学生提供一个**分享技术经验、学习路线和职业规划**的社区平台，并内置带工具执行能力的社区 AI 助手（发文章、查内容、管草稿）、RAG 站内问答与 AI 内容审核。

项目由三部分组成：

| 部分 | 位置 | 技术栈 |
|---|---|---|
| Java 主站（本仓库） | 9 个 Maven 模块的微服务 | Java 17 + Spring Boot 3.5 + Spring Cloud 2025 + MyBatis + MySQL + Redis |
| AI Agent 服务 | `techdevhub-agent/`（Python 项目，独立仓库开发中，后期并入本仓库） | Python 3.12 + FastAPI + LangGraph 1.2 + Chroma + Redis |
| Web 前端 | `TechDevHub-Front`（独立仓库） | Vue 3.5 + Vite 5 + Pinia（无 UI 库） |

---

## 系统架构

```
浏览器
  │
nginx (80/443, 静态前端 + 反向代理)
  ▼
gateway (8090, 统一路由 + JWT 鉴权)
  ├── user      (8081)  注册/登录/个人信息/注销（软删）
  ├── blog      (8082)  文章/草稿/点赞计数/热榜/RAG索引管理
  ├── category  (8083)  文章分类
  ├── follow    (8084)  关注/粉丝（注销用户派生过滤）
  ├── like      (8085)  点赞关系
  ├── comment   (8086)  评论
  └── ai        (8088)  AI 代理层（唯一的 Python 出口）
                    │ HTTP/JSON + SSE，服务间 X-Internal-Token 门禁
                    ▼
        techdevhub-agent (FastAPI, 8000)
          ├── DashScope qwen-plus / text-embedding-v3
          ├── Redis db2（会话记忆 + 结果缓存）
          └── Chroma（文章向量知识库）

MySQL：user / blog / category / follow / blog_like / comment / ai 七库分离
Redis db0：Java 业务缓存（详情缓存/热榜 ZSet/布隆过滤器/计数器）
```

服务间通信：OpenFeign + 内部密钥头（`AI_INTERNAL_TOKEN`）；管理操作另有 `X-Admin-Token`。Python 侧业务数据进出全部经 Java 内部端点（`/api/v1/internal/**`），**AI 服务不连业务库**。

---

## 仓库结构

```
TechDevHub/
├── techdevhub-common        # 公共模块：JWT、全局异常、统一返回体、雪花ID、Result
├── techdevhub-gateway       # 网关：路由转发、统一鉴权
├── techdevhub-user          # 用户：注册/登录/资料/注销（软删保数据）
├── techdevhub-blog          # 文章：发布/草稿/审核状态机/热榜/RAG 索引生命周期
├── techdevhub-category      # 分类
├── techdevhub-follow        # 关注/粉丝（注销用户派生过滤、批量资料聚合）
├── techdevhub-like          # 点赞关系
├── techdevhub-comment       # 评论
├── techdevhub-ai            # AI 代理：转发 Python agent，会话历史落 MySQL
├── techdevhub-agent/        # 🐍 Python AI 服务（后期并入；开发仓库见 docs 索引）
├── deploy/                  # 生产部署（不进 git）：docker-compose.micro.yml、nginx、Dockerfile
├── db/                      # 增量表结构（blog_moderation、ai 会话表）
├── docs/                    # Java↔AI 集成契约、审核状态机、缺陷分析
└── scripts/                 # e2e-smoke.sh 双端联调冒烟
```

---

## 核心功能

### 社区（Java 主站）

- **用户**：注册/登录（JWT + BCrypt）、资料/密码管理、注销（软删 + 用户名释放，社交关系与内容保留，展示层按账号状态派生过滤——支持将来账号找回）
- **文章**：发布/编辑/删除（逻辑删）、草稿（独立状态档，见下方状态机）、分页/分类/关键词检索、热榜 Top10（Redis ZSet：浏览×1 + 点赞×2 + 评论×3）
- **互动**：点赞/评论仅对已上架文章开放（后端计数端点强校验 + 前端隐藏，双层防御）
- **关注**：关注/取关、粉丝与关注列表（批量资料聚合，无 N+1）、计数与列表同口径过滤已注销用户
- **管理后台**：文章审核（AI 重审 ≤50 篇/批）、用户封禁、类目管理、向量索引状态/重建

### 文章状态机

```
保存草稿(3) ──发布──▶ 审核中(0) ──AI审核通过──▶ 已上架(1)
   ▲                    │                          │
   │                    │ AI驳回/下架               │ 编辑（改回 0 重审）
   └── publish_draft ◀──┘──▶ 已驳回(2) ◀───────────┘
草稿与审核中在数据库分档存储；非 status=1 的文章仅作者可见且不进缓存
```

- 发布（含 AI 代发 `publish_draft`）统一走"先发后审"闭环，审核通过自动上架并 RAG 入库；编辑即出库旧向量语料，重审通过整篇重建

### AI 能力（Python agent + ai 代理）

| 模块 | 能力 | 设计要点 |
|---|---|---|
| 社区助手 Agent | LangGraph ReAct 循环，带工具执行 | 读工具自动执行（用户身份由编排层注入，模型不可见）；写操作（创建草稿/发布文章/发布草稿）human-in-the-loop 两跳确认，身份服务端强制覆盖 |
| 内容审核 | 规则引擎 + LLM 双层，三态 verdict | 规则层拦截高频违规不烧 token；故障挂起 → 管理端批量重审 |
| RAG 问答 | 递归分块 → 向量+BM25 双通道召回 → RRF 融合 | 带文章引用生成，低分诚实拒答，引用校验做幻觉监控 |
| 多轮对话 | 滑动窗口 + LLM 摘要压缩记忆 | "先落痕再调 LLM"；会话标题 LLM 首轮生成；历史 MySQL 持久化，支持删除 |
| SSE 流式 | ping/delta/tool_call/tool_result/[DONE] 帧协议 | 泵线程 + 队列实现静默期心跳；工具调用过程实时透出到前端 |

三条设计宪法：**fail-hard**（失败抛结构化错误，绝不 200+兜底）、**AI 无状态**（不连业务库）、**错误码两段式 `{MODULE}_{NATURE}`**（Java 按前缀路由重试/熔断）。

---

## 快速启动

### 环境要求

- JDK 17+、Maven 3.6+
- Python 3.12（AI agent）
- Node 18+（前端）
- MySQL 8.0+、Redis 7+

### 1. Java 主站

```bash
# 各模块 application.yml 配置 MySQL/Redis 连接（详见各模块 resources）
mvn clean package -DskipTests
# 推荐启动顺序：gateway → user → blog → category → follow → like → comment → ai
mvn -pl techdevhub-gateway spring-boot:run
```

- JWT 密钥：环境变量 `TECHDEVHUB_JWT_SECRETKEY`（本地缺省有默认值）
- 服务间门禁：`AI_INTERNAL_TOKEN`（ai/blog 模块需一致）

### 2. Python AI agent（`techdevhub-agent/`）

```bash
python -m venv .venv
.venv/Scripts/pip install -r requirements.txt   # Linux/Mac: .venv/bin/pip
cp .env.example .env                            # 默认 fake 后端，无需 API key 即可跑通
.venv/Scripts/python -m uvicorn app.main:app --port 8000
.venv/Scripts/python -m pytest test/ -q         # 72 用例全绿
```

- 切真实模型：`.env` 设 `LLM_PROVIDER=openai` + `LLM_API_KEY`（DashScope 兼容协议）
- 详细设计文档见 `techdevhub-agent/README.md` 与 `techdevhub-agent/docs/`

### 3. 前端（TechDevHub-Front 仓库）

```bash
npm install && npm run dev    # 开发模式，API 代理到本地网关
npm run build                 # 产物部署到 nginx（或 Java 仓库 deploy/frontend-dist/）
```

---

## 生产部署

阿里云轻量服务器，`deploy/docker-compose.micro.yml` 一键编排：

```
nginx(80/443) → gateway → 8 个业务服务 → agent-service → DashScope
全部内部端口绑 127.0.0.1；敏感配置走 .env（权限 600）
```

发版流程与踩坑清单见 `docs/` 下部署文档（关键纪律：`mvn clean package`、compose 镜像 tag 服务器侧 grep 验证、前端产物原地覆盖避免 nginx inode 陷阱）。

---

## 数据库设计

| 库 | 主要表 | 说明 |
|---|---|---|
| user | user_info | 用户（软删：is_delete + 用户名/邮箱加注销标记防唯一冲突） |
| blog | blog_info, blog_moderation | 文章（status 状态机）；AI 审核留痕（verdict/review_reason） |
| category | category_info | 分类 |
| follow | follow_info | 关注关系（注销期间派生过滤，数据保留支持找回） |
| blog_like | blog_like_info | 点赞关系（user_id+blog_id 唯一） |
| comment | comment_info | 评论（两级，父评论归属校验） |
| ai | chat_conversation, chat_transcript, rag_index_status | 会话注册表（LLM 生成标题）/ 对话明细（用户可见历史）/ 向量语料生命周期 |

---

## API 文档

各 Java 模块启动后访问 `http://localhost:{port}/swagger-ui.html`（SpringDoc OpenAPI 3）。
Python agent 启动后访问 `http://localhost:8000/docs`（FastAPI 自动生成）。

| 服务 | 端口 |
|---|---|
| gateway | 8090 |
| user / blog / category | 8081 / 8082 / 8083 |
| follow / like / comment | 8084 / 8085 / 8086 |
| ai（Python 代理） | 8088 |
| techdevhub-agent | 8000 |

---

## 相关文档

| 文档 | 内容 |
|---|---|
| [docs/Java与AI服务集成方案.md](docs/Java与AI服务集成方案.md) | Java↔AI 集成契约、审核状态机、重试分工 |
| [techdevhub-agent/README.md](techdevhub-agent/README.md) | AI 服务架构、能力总览、端点清单、测试策略 |
| [techdevhub-agent/docs/decisions.md](techdevhub-agent/docs/decisions.md) | AI 服务设计决策档案（每个"为什么"） |
| [techdevhub-agent/docs/java_integration_deploy.md](techdevhub-agent/docs/java_integration_deploy.md) | 集成部署 runbook |
| [scripts/e2e-smoke.sh](scripts/e2e-smoke.sh) | Java↔Python 双端联调冒烟 |

## Roadmap

- [x] 社区核心功能（文章/互动/关注/管理后台）
- [x] 前端（Vue 3）
- [x] AI 助手 Agent / RAG / 审核 / 会话历史
- [x] 草稿体系（独立状态档 + AI 代发走服务端原文）
- [ ] 账号找回（注销软删数据已就绪，恢复流程待设计）
- [ ] 通知模块（发布文章通知粉丝）
- [ ] 搜索增强（Elasticsearch）
- [ ] 图片/头像上传
