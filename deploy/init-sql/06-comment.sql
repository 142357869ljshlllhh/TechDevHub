-- 06-comment.sql  (comment 库：评论表)
CREATE DATABASE IF NOT EXISTS comment CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE comment;

CREATE TABLE IF NOT EXISTS comment_info (
    id          BIGINT       PRIMARY KEY                   COMMENT '雪花ID（代码显式写入，非自增）',
    content     TEXT         NOT NULL                      COMMENT '评论内容',
    user_id     BIGINT       NOT NULL                      COMMENT '评论用户',
    blog_id     BIGINT       NOT NULL                      COMMENT '所属博客',
    parent_id   BIGINT       DEFAULT NULL                  COMMENT '父评论ID（NULL=一级评论）',
    is_delete   TINYINT      DEFAULT 0,
    INDEX idx_blog_id (blog_id),
    INDEX idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
