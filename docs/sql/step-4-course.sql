-- Step4 课程基础数据初始化脚本。
-- edu_platform 数据库和 edu_user 用户由 MySQL Docker 环境变量创建。
USE edu_platform;

CREATE TABLE IF NOT EXISTS course (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '课程主键',
    title VARCHAR(200) NOT NULL COMMENT '课程标题',
    description TEXT NULL COMMENT '课程介绍',
    price INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '课程价格，单位为分',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '课程状态：DRAFT、ONLINE、OFFLINE',
    teacher_name VARCHAR(100) NOT NULL COMMENT '讲师名称',
    cover_url VARCHAR(500) NULL COMMENT '课程封面地址',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    KEY idx_course_status_deleted_id (status, deleted, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程基础信息表';

-- 固定 ID 的演示数据使用幂等更新，重复执行可恢复为本阶段约定的数据。
INSERT INTO course
    (id, title, description, price, status, teacher_name, cover_url, deleted)
VALUES
    (1, 'Java 微服务入门课', '从 Spring Boot 基础逐步认识微服务拆分和调用链路。',
     19900, 'ONLINE', '张老师', 'https://example.com/covers/java-microservice.png', 0),
    (2, 'Spring Cloud Alibaba 实战课', '学习 Nacos、OpenFeign 等 Spring Cloud Alibaba 常用组件。',
     29900, 'ONLINE', '李老师', 'https://example.com/covers/spring-cloud-alibaba.png', 0),
    (3, 'Redis 高并发缓存课', '用于后续缓存阶段的课程演示数据，本阶段暂设为下线状态。',
     25900, 'OFFLINE', '王老师', 'https://example.com/covers/redis-cache.png', 0)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    description = VALUES(description),
    price = VALUES(price),
    status = VALUES(status),
    teacher_name = VALUES(teacher_name),
    cover_url = VALUES(cover_url),
    deleted = VALUES(deleted);
