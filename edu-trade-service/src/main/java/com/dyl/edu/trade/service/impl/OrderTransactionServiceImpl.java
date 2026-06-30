package com.dyl.edu.trade.service.impl;

import com.dyl.edu.trade.entity.OrderEntity;
import com.dyl.edu.trade.mapper.OrderMapper;
import com.dyl.edu.trade.service.OrderTransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单本地事务实现。
 */
@Service
public class OrderTransactionServiceImpl implements OrderTransactionService {

    private final OrderMapper orderMapper;

    public OrderTransactionServiceImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertOrder(OrderEntity order) {
        int affectedRows = orderMapper.insert(order);
        if (affectedRows != 1) {
            throw new IllegalStateException("订单写入行数异常：" + affectedRows);
        }
    }
}
