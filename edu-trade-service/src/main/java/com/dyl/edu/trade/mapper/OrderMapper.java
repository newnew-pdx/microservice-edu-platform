package com.dyl.edu.trade.mapper;

import com.dyl.edu.trade.entity.OrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 订单数据访问接口。
 */
@Mapper
public interface OrderMapper {

    OrderEntity selectByUserIdAndRequestId(@Param("userId") Long userId,
                                           @Param("requestId") String requestId);

    int insert(OrderEntity order);
}
