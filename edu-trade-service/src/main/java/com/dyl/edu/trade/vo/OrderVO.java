package com.dyl.edu.trade.vo;

import java.time.LocalDateTime;

/**
 * 订单创建及幂等命中的统一返回对象。
 */
public class OrderVO {

    private final Long orderId;
    private final String orderNo;
    private final Long userId;
    private final Long courseId;
    private final String courseTitle;
    private final Integer originalAmount;
    private final Integer discountAmount;
    private final Integer payAmount;
    private final String status;
    private final String requestId;
    private final LocalDateTime createdAt;

    public OrderVO(Long orderId, String orderNo, Long userId, Long courseId, String courseTitle,
                   Integer originalAmount, Integer discountAmount, Integer payAmount,
                   String status, String requestId, LocalDateTime createdAt) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.userId = userId;
        this.courseId = courseId;
        this.courseTitle = courseTitle;
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        this.payAmount = payAmount;
        this.status = status;
        this.requestId = requestId;
        this.createdAt = createdAt;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public Integer getOriginalAmount() {
        return originalAmount;
    }

    public Integer getDiscountAmount() {
        return discountAmount;
    }

    public Integer getPayAmount() {
        return payAmount;
    }

    public String getStatus() {
        return status;
    }

    public String getRequestId() {
        return requestId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
