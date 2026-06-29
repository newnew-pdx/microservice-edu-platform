package com.dyl.edu.trade.service.impl;

import com.dyl.edu.common.exception.BizException;
import com.dyl.edu.trade.constant.CouponLuaResultCode;
import com.dyl.edu.trade.constant.CouponRedisKeys;
import com.dyl.edu.trade.dto.CouponReceiveDTO;
import com.dyl.edu.trade.entity.CouponEntity;
import com.dyl.edu.trade.entity.UserCouponEntity;
import com.dyl.edu.trade.mapper.CouponMapper;
import com.dyl.edu.trade.mapper.UserCouponMapper;
import com.dyl.edu.trade.service.CouponService;
import com.dyl.edu.trade.vo.CouponReceiveVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠券领取服务实现。
 */
@Service
public class CouponServiceImpl implements CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponServiceImpl.class);
    private static final String COUPON_STATUS_ONGOING = "ONGOING";
    private static final String USER_COUPON_STATUS_UNUSED = "UNUSED";

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> receiveCouponScript;
    private final DefaultRedisScript<Long> rollbackCouponScript;

    public CouponServiceImpl(CouponMapper couponMapper,
                             UserCouponMapper userCouponMapper,
                             StringRedisTemplate stringRedisTemplate,
                             @Qualifier("receiveCouponScript") DefaultRedisScript<Long> receiveCouponScript,
                             @Qualifier("rollbackCouponScript") DefaultRedisScript<Long> rollbackCouponScript) {
        this.couponMapper = couponMapper;
        this.userCouponMapper = userCouponMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.receiveCouponScript = receiveCouponScript;
        this.rollbackCouponScript = rollbackCouponScript;
    }

    @Override
    public CouponReceiveVO receive(CouponReceiveDTO receiveDTO) {
        Long couponId = receiveDTO.getCouponId();
        Long userId = receiveDTO.getUserId();
        log.info("开始领取优惠券，couponId={}, userId={}", couponId, userId);

        CouponEntity coupon = queryAndValidateCoupon(couponId);
        List<String> keys = List.of(
                CouponRedisKeys.stockKey(couponId),
                CouponRedisKeys.receivedKey(couponId));

        Long luaResult;
        try {
            luaResult = stringRedisTemplate.execute(
                    receiveCouponScript, keys, String.valueOf(userId));
        } catch (RuntimeException ex) {
            log.error("执行优惠券领取 Lua 脚本异常，couponId={}, userId={}, error={}",
                    couponId, userId, ex.getMessage(), ex);
            throw new BizException(503, "优惠券领取服务暂不可用，请稍后重试");
        }

        log.info("优惠券领取 Lua 执行完成，couponId={}, userId={}, 返回码={}",
                couponId, userId, luaResult);
        handleLuaResult(luaResult, couponId, userId);

        LocalDateTime receivedAt = LocalDateTime.now();
        UserCouponEntity userCoupon = buildUserCoupon(couponId, userId, receivedAt);
        try {
            int affectedRows = userCouponMapper.insert(userCoupon);
            if (affectedRows != 1) {
                throw new IllegalStateException("用户优惠券写入行数异常：" + affectedRows);
            }
            log.info("MySQL 写入优惠券领取记录成功，couponId={}, userId={}", couponId, userId);
        } catch (DuplicateKeyException ex) {
            log.warn("MySQL 唯一索引冲突，用户已领取优惠券，couponId={}, userId={}", couponId, userId);
            rollbackRedis(keys, userId, couponId);
            throw new BizException(40902, "优惠券已领取");
        } catch (DataAccessException | IllegalStateException ex) {
            log.error("MySQL 写入优惠券领取记录失败，couponId={}, userId={}, error={}",
                    couponId, userId, ex.getMessage(), ex);
            rollbackRedis(keys, userId, couponId);
            throw new BizException(503, "优惠券领取失败，请稍后重试");
        }

        return new CouponReceiveVO(couponId, userId, USER_COUPON_STATUS_UNUSED, receivedAt);
    }

    private CouponEntity queryAndValidateCoupon(Long couponId) {
        CouponEntity coupon;
        try {
            coupon = couponMapper.selectById(couponId);
        } catch (DataAccessException ex) {
            log.error("查询优惠券基础信息失败，couponId={}, error={}", couponId, ex.getMessage(), ex);
            throw new BizException(503, "优惠券服务暂不可用，请稍后重试");
        }

        if (coupon == null) {
            log.warn("优惠券不存在，couponId={}", couponId);
            throw new BizException(404, "优惠券不存在");
        }
        if (!COUPON_STATUS_ONGOING.equals(coupon.getStatus())) {
            log.warn("优惠券状态不可领取，couponId={}, status={}", couponId, coupon.getStatus());
            throw new BizException(40904, "优惠券当前不可领取");
        }

        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStartTime() == null || coupon.getEndTime() == null) {
            log.error("优惠券活动时间配置不完整，couponId={}", couponId);
            throw new BizException(503, "优惠券活动配置异常");
        }
        if (now.isBefore(coupon.getStartTime())) {
            log.warn("优惠券活动尚未开始，couponId={}, startTime={}", couponId, coupon.getStartTime());
            throw new BizException(40903, "优惠券活动尚未开始");
        }
        if (now.isAfter(coupon.getEndTime())) {
            log.warn("优惠券活动已经结束，couponId={}, endTime={}", couponId, coupon.getEndTime());
            throw new BizException(40904, "优惠券活动已经结束");
        }
        return coupon;
    }

    private void handleLuaResult(Long luaResult, Long couponId, Long userId) {
        if (luaResult == null) {
            log.error("优惠券领取 Lua 返回空结果，couponId={}, userId={}", couponId, userId);
            throw new BizException(503, "优惠券领取服务异常");
        }
        if (luaResult == CouponLuaResultCode.SUCCESS) {
            return;
        }
        if (luaResult == CouponLuaResultCode.OUT_OF_STOCK) {
            log.warn("优惠券库存不足，couponId={}, userId={}", couponId, userId);
            throw new BizException(40901, "优惠券库存不足");
        }
        if (luaResult == CouponLuaResultCode.ALREADY_RECEIVED) {
            log.warn("用户重复领取优惠券，couponId={}, userId={}", couponId, userId);
            throw new BizException(40902, "优惠券已领取");
        }
        if (luaResult == CouponLuaResultCode.NOT_INITIALIZED) {
            log.warn("优惠券 Redis 库存未初始化，couponId={}, userId={}", couponId, userId);
            throw new BizException(40905, "优惠券库存未初始化");
        }
        log.error("优惠券领取 Lua 返回未知结果，couponId={}, userId={}, 返回码={}",
                couponId, userId, luaResult);
        throw new BizException(503, "优惠券领取服务异常");
    }

    private UserCouponEntity buildUserCoupon(Long couponId, Long userId, LocalDateTime receivedAt) {
        UserCouponEntity userCoupon = new UserCouponEntity();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(couponId);
        userCoupon.setStatus(USER_COUPON_STATUS_UNUSED);
        userCoupon.setReceivedAt(receivedAt);
        userCoupon.setCreatedAt(receivedAt);
        userCoupon.setUpdatedAt(receivedAt);
        userCoupon.setDeleted(0);
        return userCoupon;
    }

    /**
     * MySQL 写入失败后进行尽力补偿；补偿失败时保留错误日志供人工核对。
     */
    private void rollbackRedis(List<String> keys, Long userId, Long couponId) {
        try {
            Long rollbackResult = stringRedisTemplate.execute(
                    rollbackCouponScript, keys, String.valueOf(userId));
            if (Long.valueOf(1L).equals(rollbackResult)) {
                log.info("Redis 优惠券领取补偿成功，已恢复库存并移除用户标记，couponId={}, userId={}",
                        couponId, userId);
            } else {
                log.warn("Redis 优惠券领取补偿未执行，用户标记可能已不存在，couponId={}, userId={}, 返回码={}",
                        couponId, userId, rollbackResult);
            }
        } catch (RuntimeException ex) {
            log.error("Redis 优惠券领取补偿失败，请人工核对 Redis 与 MySQL，couponId={}, userId={}, error={}",
                    couponId, userId, ex.getMessage(), ex);
        }
    }
}
