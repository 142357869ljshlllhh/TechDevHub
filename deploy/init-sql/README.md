# 数据库初始化 SQL 目录

把建表语句（DDL）放在本目录下，MySQL 容器**首次启动**时会按文件名字母顺序自动执行。

## 已筛选出的 TechDevHub 必需文件（7 个库，仅 `*_info` 表）
> ⚠️ 你原始 SQL 里有一批“影子表”（`user`/`category`/`blog`/`comment`/`blog_like`/`user_follow`
> 带 `role`、`tag_name`、`follow_id`/`following_id` 等列），**代码完全不读它们**，是早期/备选设计。
> 下面只保留代码真正使用的 `*_info` 表，quiz 项目表已排除（那是另一个项目）。

| 文件 | 库名 | 代码实际使用的表 |
|------|------|------------------|
| `01-user.sql`      | `user`     | `user_info(id BIGINT, username, email, password, follower_count, following_count, is_delete, status, create_time)` |
| `02-blog.sql`      | `blog`     | `blog_info(id BIGINT, user_id, title, content, category_id, view/like/comment_count, status, create/update_time, is_delete)` |
| `03-category.sql`  | `category` | `category_info(id INT, category_name UNIQUE, is_delete)` |
| `04-follow.sql`    | `follow`   | `follow_info(id BIGINT, user_id, follow_user_id, is_delete)` |
| `05-like.sql`      | `blog_like`| `blog_like_info(id BIGINT, user_id, blog_id, is_delete)` |
| `06-comment.sql`   | `comment`  | `comment_info(id BIGINT, content, user_id, blog_id, parent_id, is_delete)` |
| `07-ai.sql`        | `ai`       | **无表**（见下方说明），仅建库占位 |

## 两个需要你知道的坑
1. **category_info.id 是 INT（已知待修 bug）**：`CategoryServiceImpl.create()` 用雪花算法生成
   ~1.8e18 的 Long 插进 INT 列会报 "Out of range"。只能通过「预置分类」（小整数 id 手动 INSERT）
   添加分类，后台“新增分类”接口暂时不可用；根治需把 id 改为 BIGINT 并同步改实体/VO/Mapper 的 Long。
2. **follow 模块已修一个 SQL bug**：`FollowMapper.getFollowers()` 原查询 `select follow_id ...
   where user_id = #{id}`，列 `follow_id` 不存在且 WHERE 写反了（`/follows/followers` 要的是“粉丝”，
   应 `select user_id where follow_user_id = #{id}`）。已在代码中修正。

## 部署要求
1. 每个文件**自己建库并使用**：
   ```sql
   CREATE DATABASE IF NOT EXISTS user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   USE user;
   CREATE TABLE IF NOT EXISTS user_info ( ... );
   ```
2. 文件必须是 **UTF-8 无 BOM**。含中文且本机是 gbk 的需先转 UTF-8，否则中文乱码。
3. **只首次生效**：目录挂载到 `/docker-entrypoint-initdb.d`，MySQL 仅在数据目录为空时执行。
   若已初始化过（`mysql_data` 卷有数据），改 SQL 不会重跑，需要重建库：
   ```bash
   docker compose -f deploy/docker-compose.micro.yml down
   docker volume rm techdevhub_mysql_data
   docker compose -f deploy/docker-compose.micro.yml up -d mysql-server
   ```
4. **AI 模块只建库不建表**：代码只在 Redis 存对话记忆，无 MySQL 表；但 `application.yml` 的
   datasource 指向 `jdbc:mysql://.../ai`，库不存在会导致启动连库失败，所以 `07-ai.sql` 仅建库。
