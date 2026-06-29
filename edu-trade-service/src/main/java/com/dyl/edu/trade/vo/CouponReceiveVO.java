package com.dyl.edu.trade.vo;

import java.time.LocalDateTime;

/**
 * 优惠券领取成功返回对象。
 */
public class CouponReceiveVO {

    private final Long couponId;
    private final Long userId;
    private final String status;
    private final LocalDateTime receivedAt;

    public CouponReceiveVO(Long couponId, Long userId, String status, LocalDateTime receivedAt) {
        this.couponId = couponId;
        this.userId = userId;
        this.status = status;
        this.receivedAt = receivedAt;
    }

    public Long getCouponId() {
        return couponId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }
}
