-- 05-like.sql  (blog_like 库：点赞表)
CREATE DATABASE IF NOT EXISTS blog_like CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE blog_like;

CREATE TABLE IF NOT EXISTS blog_like_info (
    id          BIGINT       PRIMARY KEY                   COMMENT '雪花ID（代码显式写入，非自增）',
    user_id     BIGINT       NOT NULL                      COMMENT '点赞用户',
    blog_id     BIGINT       NOT NULL                      COMMENT '被点赞博客',
    is_delete   TINYINT      DEFAULT 0,
    UNIQUE KEY unique_like (user_id, blog_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
