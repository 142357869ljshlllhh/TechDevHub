-- 04-follow.sql  (follow 库：关注关系表)
CREATE DATABASE IF NOT EXISTS follow CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE follow;

CREATE TABLE IF NOT EXISTS follow_info (
    id              BIGINT       PRIMARY KEY                COMMENT '雪花ID（代码显式写入，非自增）',
    user_id         BIGINT       NOT NULL                   COMMENT '关注者（粉丝）',
    follow_user_id  BIGINT       NOT NULL                   COMMENT '被关注者',
    is_delete       TINYINT      DEFAULT 0,
    UNIQUE KEY unique_follow (user_id, follow_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
