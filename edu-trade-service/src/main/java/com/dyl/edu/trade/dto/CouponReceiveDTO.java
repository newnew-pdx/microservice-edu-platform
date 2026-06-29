package com.dyl.edu.trade.dto;

/**
 * 优惠券领取业务参数，userId 只能来自 Gateway 透传的可信请求头。
 */
public class CouponReceiveDTO {

    private final Long couponId;
    private final Long userId;

    public CouponReceiveDTO(Long couponId, Long userId) {
        this.couponId = couponId;
        this.userId = userId;
    }

    public Long getCouponId() {
        return couponId;
    }

    public Long getUserId() {
        return userId;
    }
}
