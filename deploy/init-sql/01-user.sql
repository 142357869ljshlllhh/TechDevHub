-- 01-user.sql  (user 库：用户表)
CREATE DATABASE IF NOT EXISTS user CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE user;

CREATE TABLE IF NOT EXISTS user_info (
    id              BIGINT       PRIMARY KEY                COMMENT '雪花ID（代码显式写入，非自增）',
    username        VARCHAR(50)  NOT NULL UNIQUE            COMMENT '昵称',
    email           VARCHAR(100) NOT NULL UNIQUE            COMMENT '邮箱',
    password        VARCHAR(255) NOT NULL                   COMMENT '加密后密码',
    follower_count  INT          DEFAULT 0                  COMMENT '粉丝数',
    following_count INT          DEFAULT 0                  COMMENT '关注数',
    is_delete       TINYINT      DEFAULT 0,
    status          TINYINT      DEFAULT 0                  COMMENT '0普通用户 1管理员',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
