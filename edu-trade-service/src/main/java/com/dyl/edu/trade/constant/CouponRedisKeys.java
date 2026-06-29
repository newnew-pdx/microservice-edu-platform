package com.dyl.edu.trade.constant;

/**
 * 优惠券 Redis key 生成规则。
 */
public final class CouponRedisKeys {

    private static final String STOCK_PREFIX = "coupon:stock:";
    private static final String RECEIVED_PREFIX = "coupon:received:";

    private CouponRedisKeys() {
    }

    public static String stockKey(Long couponId) {
        return STOCK_PREFIX + couponId;
    }

    public static String receivedKey(Long couponId) {
        return RECEIVED_PREFIX + couponId;
    }
}
