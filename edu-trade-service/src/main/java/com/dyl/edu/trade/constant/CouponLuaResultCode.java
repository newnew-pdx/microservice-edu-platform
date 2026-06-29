package com.dyl.edu.trade.constant;

/**
 * 优惠券领取 Lua 脚本返回码。
 */
public final class CouponLuaResultCode {

    public static final long SUCCESS = 0L;
    public static final long OUT_OF_STOCK = 1L;
    public static final long ALREADY_RECEIVED = 2L;
    public static final long NOT_INITIALIZED = 3L;

    private CouponLuaResultCode() {
    }
}
