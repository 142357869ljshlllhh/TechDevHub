-- ============================================================
-- 分类审核流：类目表新增审核状态 / 申请人 / 驳回原因
-- 执行环境：生产 MySQL（服务器 47.116.3.250 上的 category 库）
-- 本脚本【幂等】：可重复执行，已存在的列不会重复添加，不会报错。
-- 说明：
--   status: 0 待审 / 1 通过 / 2 驳回
--   历史类目统一置为 1（通过，全员可见）；新增待审项由应用写入 status=0
--   creator_id: 提交申请的普通用户 ID
--   reject_reason: 驳回时必填，通过时清空
-- ============================================================

DROP PROCEDURE IF EXISTS mig_category_audit;
DELIMITER //
CREATE PROCEDURE mig_category_audit()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'category' AND TABLE_NAME = 'category_info' AND COLUMN_NAME = 'status') THEN
    ALTER TABLE category_info
      ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '0待审 1通过 2驳回';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'category' AND TABLE_NAME = 'category_info' AND COLUMN_NAME = 'creator_id') THEN
    ALTER TABLE category_info
      ADD COLUMN creator_id BIGINT NULL COMMENT '申请人用户ID';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'category' AND TABLE_NAME = 'category_info' AND COLUMN_NAME = 'reject_reason') THEN
    ALTER TABLE category_info
      ADD COLUMN reject_reason VARCHAR(255) NULL COMMENT '驳回原因';
  END IF;

  -- 历史类目（无审核概念时的数据）统一置为“通过/可见”，避免上线后老类目消失
  UPDATE category_info SET status = 1 WHERE status IS NULL OR status = 0;
END //
DELIMITER ;
CALL mig_category_audit();
DROP PROCEDURE IF EXISTS mig_category_audit;
