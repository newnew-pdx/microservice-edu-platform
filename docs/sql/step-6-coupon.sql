-- Step6：Trade Service 优惠券领取初始化脚本。
-- 只初始化 MySQL 表和本地演示优惠券；Redis 库存需要按文档手动初始化。

CREATE TABLE IF NOT EXISTS coupon (
    id BIGINT NOT NULL COMMENT '优惠券 ID',
    name VARCHAR(100) NOT NULL COMMENT '优惠券名称',
    total_stock INT UNSIGNED NOT NULL COMMENT '初始总库存',
    stock INT UNSIGNED NOT NULL COMMENT 'MySQL 库存基准值',
    amount INT UNSIGNED NOT NULL COMMENT '优惠金额，单位：分',
    status VARCHAR(20) NOT NULL COMMENT 'NOT_STARTED/ONGOING/ENDED/DISABLED',
    start_time DATETIME NOT NULL COMMENT '领取开始时间',
    end_time DATETIME NOT NULL COMMENT '领取结束时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    KEY idx_coupon_status_deleted_time (status, deleted, start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券活动表';

CREATE TABLE IF NOT EXISTS user_coupon (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '领取记录 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    coupon_id BIGINT NOT NULL COMMENT '优惠券 ID',
    status VARCHAR(20) NOT NULL COMMENT 'UNUSED/USED/EXPIRED',
    received_at DATETIME NOT NULL COMMENT '领取时间',
    used_at DATETIME NULL COMMENT '使用时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_coupon_user (coupon_id, user_id),
    KEY idx_user_coupon_user_status (user_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户优惠券领取记录表';

-- 本地演示优惠券的有效期覆盖当前学习阶段；重复执行不会重置已经变化的 stock。
INSERT INTO coupon
    (id, name, total_stock, stock, amount, status, start_time, end_time, deleted)
VALUES
    (1, '新用户满减券', 5, 5, 5000, 'ONGOING',
     '2025-01-01 00:00:00', '2035-12-31 23:59:59', 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    total_stock = VALUES(total_stock),
    amount = VALUES(amount),
    status = VALUES(status),
    start_time = VALUES(start_time),
    end_time = VALUES(end_time),
    deleted = VALUES(deleted);
