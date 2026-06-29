package com.dyl.edu.trade.service;

import com.dyl.edu.trade.dto.CouponReceiveDTO;
import com.dyl.edu.trade.vo.CouponReceiveVO;

/**
 * 优惠券业务服务。
 */
public interface CouponService {

    /**
     * 校验优惠券并完成 Redis 原子领取与 MySQL 持久化。
     */
    CouponReceiveVO receive(CouponReceiveDTO receiveDTO);
}
