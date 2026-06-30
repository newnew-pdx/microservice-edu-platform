package com.dyl.edu.trade.service;

import com.dyl.edu.trade.entity.OrderEntity;

/**
 * 订单本地事务服务，将数据库写入与远程课程查询隔离。
 */
public interface OrderTransactionService {

    void insertOrder(OrderEntity order);
}
