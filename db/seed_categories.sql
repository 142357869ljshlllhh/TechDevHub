-- ============================================================
-- TechDevHub 预置分类种子数据
-- 目标库：category  目标表：category_info
-- 表结构：id INT(非自增,主键) / category_name VARCHAR(50) UNIQUE / is_delete TINYINT
-- 说明：
--   1. id 需显式指定（非自增）；使用 1..N 的小整数，避免与后台
--      create() 生成的雪花ID冲突，也契合 INT 列宽。
--   2. category_name 唯一，重复插入会报错，故用 INSERT IGNORE 保证幂等。
--   3. 本文件可重复执行：已存在的数据会被跳过，不会重复插入。
-- ============================================================
-- 注意：本机 MySQL 连接字符集默认是 gbk，必须先用 SET NAMES utf8mb4
-- 否则中文字段会乱码。建议始终用：
--   mysql -u<user> -p<pass> --default-character-set=utf8mb4 < seed_categories.sql
-- 执行本文件。

SET NAMES utf8mb4;

INSERT IGNORE INTO category.category_info (id, category_name, is_delete) VALUES
(1,  '后端开发',        0),
(2,  '前端开发',        0),
(3,  '人工智能',        0),
(4,  '数据库',          0),
(5,  'DevOps与运维',    0),
(6,  '移动开发',        0),
(7,  '算法与数据结构',  0),
(8,  '计算机基础',      0),
(9,  '面试经验',        0),
(10, '技术杂谈',        0);
