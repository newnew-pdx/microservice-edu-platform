package com.dyl.edu.trade.service;

import com.dyl.edu.trade.dto.OrderCreateRequest;
import com.dyl.edu.trade.vo.OrderVO;

/**
 * 订单业务服务。
 */
public interface OrderService {

    /**
     * 查询课程并创建待支付订单，相同用户和 requestId 重复请求时返回已有订单。
     */
    OrderVO createOrder(Long userId, OrderCreateRequest request);

    /**
     * 查询数据库实时状态，并尝试关闭仍为待支付状态的超时订单。
     */
    void closeTimeoutOrder(String orderNo);
}
