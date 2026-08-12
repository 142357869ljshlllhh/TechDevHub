-- 07-ai.sql  (ai 库：AI 模块数据库)
-- 当前 AI 模块（techdevhub-ai）只在 Redis 中存储对话记忆（RedisChatMemoryStore），
-- 代码里没有任何 @Mapper / 实体表（ai_chat_record、ai_answer_cache 等是影子表，未接代码）。
-- 但 application.yml 的 spring.datasource.url 指向 jdbc:mysql://.../ai，
-- 应用启动时必须能连上名为 ai 的库，否则 DataSource 初始化失败、容器起不来。
-- 因此这里只建库、不建表。
CREATE DATABASE IF NOT EXISTS ai CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE ai;
