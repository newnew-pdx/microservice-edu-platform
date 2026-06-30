package com.dyl.edu.trade.mq.message;

import java.time.LocalDateTime;

/**
 * 订单超时消息，与接口 DTO 和数据库实体分离。
 */
public class OrderTimeoutMessage {

    private String messageId;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private LocalDateTime createdAt;
    private Long timeoutSeconds;

    public OrderTimeoutMessage() {
    }

    public OrderTimeoutMessage(String messageId, Long orderId, String orderNo, Long userId,
                               LocalDateTime createdAt, Long timeoutSeconds) {
        this.messageId = messageId;
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.userId = userId;
        this.createdAt = createdAt;
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
