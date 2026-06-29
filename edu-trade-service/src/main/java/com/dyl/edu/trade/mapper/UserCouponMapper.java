package com.dyl.edu.trade.mapper;

import com.dyl.edu.trade.entity.UserCouponEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户优惠券领取记录数据访问接口。
 */
@Mapper
public interface UserCouponMapper {

    int insert(UserCouponEntity userCoupon);
}
