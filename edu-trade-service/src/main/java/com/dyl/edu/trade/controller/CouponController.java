package com.dyl.edu.trade.controller;

import com.dyl.edu.common.exception.BizException;
import com.dyl.edu.common.result.Result;
import com.dyl.edu.trade.dto.CouponReceiveDTO;
import com.dyl.edu.trade.service.CouponService;
import com.dyl.edu.trade.vo.CouponReceiveVO;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 优惠券接口，只负责参数接收、校验和统一返回。
 */
@RestController
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @PostMapping("/trade/coupon/receive/{couponId}")
    public Result<CouponReceiveVO> receive(@PathVariable("couponId") Long couponId,
                                           @RequestHeader("X-User-Id") String userIdHeader) {
        if (couponId == null || couponId <= 0) {
            throw new BizException(400, "优惠券 ID 不合法");
        }

        Long userId;
        try {
            userId = Long.valueOf(userIdHeader);
        } catch (NumberFormatException ex) {
            throw new BizException(400, "用户信息不合法");
        }
        if (userId <= 0) {
            throw new BizException(400, "用户信息不合法");
        }

        CouponReceiveDTO receiveDTO = new CouponReceiveDTO(couponId, userId);
        return Result.success(couponService.receive(receiveDTO));
    }
}
