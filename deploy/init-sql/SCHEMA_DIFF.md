# 建表语句比对报告（真实库 ↔ init-sql DDL）

> 比对方式：直连本机 MySQL（`root/123456` @ `127.0.0.1:3306`），读取 `information_schema`
> 拉取全部非系统库的表与列定义，与 `deploy/init-sql/*.sql` 逐列比对。

## 一、本机实际存在的库（共 10 个业务/影子库）

| 库名 | 状态 | 说明 |
|------|------|------|
| `user`        | ✅ 代码在用 | `user_info`（4 行真实数据） |
| `blog`        | ✅ 代码在用 | `blog_info`（0 行） |
| `category`    | ✅ 代码在用 | `category_info`（**10 行真实数据**） |
| `comment`     | ✅ 代码在用 | `comment_info`（0 行） |
| `blog_like`   | ✅ 代码在用 | `blog_like_info`（1 行） |
| `ai`          | ✅ 代码在用 | 仅建库；`ai_answer_cache`/`ai_chat_record`/`user_vip_info` 是**孤儿表**（代码用 Redis 存记忆，不读它们） |
| `follow`      | ❌ **不存在** | 代码连 `follow` 库 + `follow_info` 表，但本机无此库（只有 `user_follow` 影子库）。需由 `04-follow.sql` 新建 |
| `user_follow` | 🟡 影子 | `user_follow_info(id, follow_id, following_id, is_delete)` —— 旧设计，代码不读 |
| `techdevhub`  | 🟡 影子 | 一套重复表：`blog`/`blog_like`/`category`/`comment`/`user`/`user_follow`（代码不读） |
| `blog_system_v2` | 🟡 影子 | 另一套重构表：`blog_content`/`blog_summary`/`category`/`comment`/`user_follow`/`users`（代码不读） |
| `quiz`        | 🟡 另一个项目 | `quiz_activity`/`quiz_answer_record`/`quiz_question`，与 TechDevHub 无关 |

**结论**：代码真正读写的是 `user`/`blog`/`category`/`comment`/`blog_like` 这 5 个库里的 `*_info` 表，
加上需要新建的 `follow.follow_info`，以及仅占位不建表的 `ai`。其余（`user_follow`/`techdevhub`/`blog_system_v2`/`quiz`）全部是影子/遗留，已正确排除。

## 二、逐表比对（我的 DDL ↔ 真实表）

### ✅ user.user_info（4 行）— 基本一致
| 列 | 真实 | 我的 DDL | 差异 |
|----|------|----------|------|
| id / username / email / password / follower_count / following_count / is_delete / status / create_time | 同 | 同 | — |
| username, email | 真实为 **NULL 允许** | 我设为 **NOT NULL** | 仅约束更严；代码注册必填，不影响。保持 NOT NULL（数据完整性更好） |

### ⚠️ blog.blog_info（0 行）— 已修正 2 处
| 列 | 真实 | 我的 DDL（修正后） | 差异 |
|----|------|---------------------|------|
| title | `varchar(50)` | 原 `varchar(255)` → **改为 `varchar(50)`** | ✅ 已对齐真实结构 |
| category_id | `int` | 原 `bigint` → **改为 `int`** | ✅ 已对齐（且 `BlogInfo.categoryId` 实体是 `Integer`，必须为 int） |
| 其余列 | 同 | 同 | — |
| update_time | 真实无 `ON UPDATE` | 我加了 `ON UPDATE CURRENT_TIMESTAMP` | 行为增强，无害 |

### ✅ category.category_info（10 行）— 一致
- `id int` / `category_name varchar(50) UNIQUE` / `is_delete tinyint default 0`，与我的 DDL 一致。
- ⚠️ 已知坑：`id` 是 INT，但 `CategoryServiceImpl.create()` 用雪花 Long 插入会超范围 → 后台“新增分类”暂不可用（仅预置分类可用）。

### ✅ comment.comment_info（0 行）— 完全一致
- `id bigint` / `content text` / `user_id bigint` / `blog_id bigint` / `parent_id bigint null` / `is_delete tinyint default 0` + 索引，完全一致。

### ⚠️ blog_like.blog_like_info（1 行）— 基本一致
| 列 | 真实 | 我的 DDL | 差异 |
|----|------|----------|------|
| is_delete | 真实 **NOT NULL 无默认** | 我设 `DEFAULT 0` | 代码 insert 显式带值，无害；DEFAULT 0 更宽松 |
| 唯一约束 | 真实仅 `user_id` 普通索引 | 我加 `UNIQUE KEY unique_like(user_id,blog_id)` | 防重复点赞，更合理 |

### ❌ follow.follow_info — 真实库不存在（我的 DDL 负责新建）
- 代码（`FollowInfo` 实体 + `FollowMapper`）期望：`follow_info(id bigint, user_id bigint, follow_user_id bigint, is_delete tinyint)`。
- 本机**没有 `follow` 库**；唯一的相近表是 `user_follow.user_follow_info(id, follow_id, following_id, is_delete)` —— 旧列名，代码不读。
- `04-follow.sql` 会**新建 `follow` 库 + 正确的 `follow_info` 表**，已顺便修正 `getFollowers()` 的 SQL（原 `select follow_id` 列不存在且 WHERE 写反，见 README）。
- 注意：本机无任何可用的关注关系数据（影子表也都是 0 行），部署后是全新空表，符合预期。

### ai 库 — 仅建库
- 代码只在 Redis 存对话记忆，不读 `ai_answer_cache`/`ai_chat_record`/`user_vip_info`。
- `07-ai.sql` 只建 `ai` 库（datasource 指向它，缺库会启动失败）。这三个孤儿表**未纳入**，如需持久化 AI 对话历史到 MySQL，要另写代码，不属于当前部署。

## 三、本轮对 init-sql 的修正
1. `02-blog.sql`：`title varchar(255)` → `varchar(50)`；`category_id bigint` → `int`（与实体 `Integer` 和 `category_info.id` 类型对齐）。
2. 其余 6 个文件与真实结构一致，无需改动。

## 四、部署提醒
- 部署用 MySQL 是**全新空数据目录**，`init-sql` 首次启动会按 01→07 顺序建 7 库及表。
- 若想把本机**真实数据**（user 4 行、category 10 行、blog_like 1 行）也迁到服务器，需在 `init-sql` 建表后额外 `mysqldump` 这几个表导出再导入（注意本机 client 字符集是 gbk，导出务必 `--default-character-set=utf8mb4`）。
- `follow` 库会从零开始（无历史关注数据），属正常。
