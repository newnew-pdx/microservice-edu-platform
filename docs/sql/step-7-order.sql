-- Step7：Trade Service 课程订单创建初始化脚本。
-- 本阶段只创建待支付订单，不包含真实支付、优惠券抵扣或超时关闭。
USE edu_platform;

CREATE TABLE IF NOT EXISTS edu_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单主键',
    order_no VARCHAR(64) NOT NULL COMMENT '业务订单号',
    user_id BIGINT NOT NULL COMMENT '下单用户 ID，来自 Gateway 可信用户上下文',
    course_id BIGINT NOT NULL COMMENT '课程 ID',
    course_title VARCHAR(200) NOT NULL COMMENT '下单时课程标题快照',
    original_amount INT UNSIGNED NOT NULL COMMENT '课程原价，单位：分',
    discount_amount INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '优惠金额，单位：分，本阶段固定为 0',
    pay_amount INT UNSIGNED NOT NULL COMMENT '应付金额，单位：分',
    status VARCHAR(32) NOT NULL COMMENT '订单状态，本阶段只创建 UNPAID',
    request_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '客户端幂等请求标识',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    paid_at DATETIME NULL COMMENT '支付时间，本阶段为空',
    closed_at DATETIME NULL COMMENT '关闭时间，本阶段为空',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_user_request (user_id, request_id),
    KEY idx_user_id (user_id),
    KEY idx_course_id (course_id),
    KEY idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程订单表';
