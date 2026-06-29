package com.dyl.edu.trade.mapper;

import com.dyl.edu.trade.entity.CouponEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 优惠券数据访问接口，SQL 定义在 mapper/CouponMapper.xml。
 */
@Mapper
public interface CouponMapper {

    CouponEntity selectById(@Param("couponId") Long couponId);
}
