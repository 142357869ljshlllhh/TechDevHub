-- 03-category.sql  (category 库：分类表)
CREATE DATABASE IF NOT EXISTS category CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE category;

CREATE TABLE IF NOT EXISTS category_info (
    id            BIGINT       PRIMARY KEY                COMMENT '分类ID，雪花算法生成（Long）',
    category_name VARCHAR(50)  NOT NULL UNIQUE            COMMENT '分类名',
    is_delete     TINYINT      DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 预置数据（按需取消注释；id 走小整数即可，新增分类由后端雪花算法生成 BIGINT）：
-- INSERT IGNORE INTO category_info (id, category_name) VALUES
--   (1,'后端开发'),(2,'前端开发'),(3,'人工智能'),(4,'数据库'),(5,'中间件'),
--   (6,'DevOps'),(7,'算法'),(8,'移动开发'),(9,'云计算'),(10,'其他');
