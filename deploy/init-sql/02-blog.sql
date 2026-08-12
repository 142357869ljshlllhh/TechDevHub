-- 02-blog.sql  (blog 库：博客表)
CREATE DATABASE IF NOT EXISTS blog CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE blog;

CREATE TABLE IF NOT EXISTS blog_info (
    id              BIGINT       PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    title           VARCHAR(50)  NOT NULL,
    content         TEXT         NOT NULL,
    category_id     BIGINT,
    view_count      INT          DEFAULT 0,
    like_count      INT          DEFAULT 0,
    comment_count   INT          DEFAULT 0,
    status          TINYINT      DEFAULT 0                  COMMENT '审核中0 通过1 下架2',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete       TINYINT      DEFAULT 0,
    INDEX idx_category_id (category_id),
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
