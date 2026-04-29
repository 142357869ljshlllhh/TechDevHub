# TechDevHub

> 面向计算机专业学生的技术社区与成长交流平台

## 项目简介

TechDevHub 旨在解决高校课程体系与就业实际脱节的问题，为学生提供一个**分享技术经验、学习路线和职业规划**的社区平台。

**目标用户**：计算机专业学生  
**项目类型**：微服务架构技术社区  
**技术栈**：Java 17 + Spring Boot + MyBatis + MySQL + Redis + Spring Cloud Alibaba

---

## 系统架构

```
TechDevHub (多模块 Maven 项目)
├── techdevhub-common       # 公共模块：JWT、全局异常、统一返回体、雪花ID
├── techdevhub-gateway     # 网关层：路由转发、统一鉴权
├── techdevhub-user        # 用户模块：注册、登录、个人信息管理
├── techdevhub-blog        # 博客模块：发布、修改、删除、点赞、分页查询、热榜
├── techdevhub-category    # 分类模块：文章分类管理
├── techdevhub-comment     # 评论模块：发布评论、删除评论、评论点赞
├── techdevhub-like        # 点赞模块：博客点赞、统计
├── techdevhub-follow      # 关注模块：关注/取关、粉丝列表
└── techdevhub-ai         # AI 模块：基于 LangChain4j 的智能问答与咨询
```

---

## 核心功能

### 用户模块
- 注册 / 登录（JWT 认证，密码 BCrypt 加密）
- 修改个人信息 / 修改密码
- 删除账号
- 关注 / 取消关注

### 博客模块
- 发布 / 修改 / 删除博客（逻辑删除）
- 点赞 / 取消点赞
- 分页查询（支持按分类、关键词、用户过滤）
- 热榜 Top 10（基于 ZSet 实时计算）
- 浏览量统计（Redis 计数器 + 定时刷库）

### 评论模块
- 发布 / 删除评论
- 评论点赞

### AI 模块
- 基于 LangChain4j + Redis 聊天记忆的智能助手
- 技术咨询、学习路线推荐

### 管理员模块
- 文章分类管理（增删改）
- 用户管理（查看、封禁）
- 博客状态审核（待审核 / 发布 / 下架）

---

## 技术亮点

| 技术点 | 实现方式 |
|--------|---------|
| 认证鉴权 | JWT + 自定义拦截器 + `@IgnoreToken` 注解 |
| 缓存设计 | Redis 多级缓存：详情缓存、用户信息缓存、布隆过滤器防穿透 |
| 热榜计算 | Redis ZSet，score = 浏览*1 + 点赞*2 + 评论*3 |
| 缓存击穿保护 | Redis 分布式锁（`setIfAbsent` + Lua 脚本释放） |
| 异步处理 | Spring 线程池（`ThreadPoolTaskExecutor`）+ `CompletableFuture` 并行查 |
| ID 生成 | 雪花算法（`SnowflakeIdGenerator`） |
| 服务通信 | OpenFeign RPC + Nacos 服务发现 |
| AI 能力 | LangChain4j + RedisChatMemoryStore 持久化对话记忆 |
| API 文档 | SpringDoc OpenAPI 3 自动生成 |

---

## 快速启动

### 环境要求
- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+
- Nacos 2.x（可选，单机版可关闭）

### 配置说明

每个模块的 `src/main/resources/application.yml` 需要配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/techdevhub?useSSL=false&serverTimezone=UTC
    username: root
    password: your_password
  data:
    redis:
      host: localhost
      port: 6379

jwt:
  secret: your_jwt_secret
  expiration: 86400000
```

### 启动顺序

```bash
# 1. 启动 Nacos（如使用）
startup.cmd -m standalone   # Windows

# 2. 依次启动各模块（推荐顺序）
cd techdevhub-common && mvn install
cd techdevhub-gateway && mvn spring-boot:run
cd techdevhub-user && mvn spring-boot:run
cd techdevhub-blog && mvn spring-boot:run
# ... 其他模块
```

---

## 数据库设计

| 表名 | 说明 |
|------|------|
| user | 用户表（id, username, password, email, status...） |
| blog_info | 博客表（id, user_id, title, content, category_id, status, is_delete...） |
| category_info | 分类表（id, name, description...） |
| comment_info | 评论表（id, blog_id, user_id, content, parent_id...） |
| blog_like_info | 点赞表（id, blog_id, user_id...） |
| follow_info | 关注表（id, user_id, follow_user_id...） |

**关系模型**：
- 用户 : 博客 = 1 : N
- 博客 : 评论 = 1 : N
- 用户 : 点赞 = N : N（多对多）
- 用户 : 关注 = N : N（自关联）

---

## API 文档

启动后访问：
```
http://localhost:{port}/swagger-ui.html
```

各模块端口（默认）：
- gateway: 8080
- user: 8081
- blog: 8082
- category: 8083
- comment: 8084
- like: 8085
- follow: 8086
- ai: 8087

---

## 项目结构示例（blog 模块）

```
techdevhub-blog/
├── controller/BlogController.java      # REST 接口
├── service/BlogService.java           # 业务接口
├── service/impl/BlogServiceImpl.java  # 实现（缓存、计数、热榜）
├── mapper/BlogMapper.java             # MyBatis Mapper
├── dto/                              # 请求 DTO
├── vo/                               # 响应 VO
├── config/ThreadPoolConfig.java       # 线程池配置
└── client/UserProfileClient.java     # Feign 调用用户服务
```

---

## 待完善功能

- [ ] 前端部分
- [ ] 抢答功能（抢答成功可获取 AI 助手 VIP 一周）
- [ ] 通知模块（发布博客后通知粉丝）
- [ ] 搜索功能（Elasticsearch 集成）
- [ ] 头像与图片上传
---

