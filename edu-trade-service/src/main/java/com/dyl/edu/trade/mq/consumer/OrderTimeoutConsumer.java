package com.dyl.edu.trade.mq.consumer;

import com.dyl.edu.trade.constant.OrderRabbitConstants;
import com.dyl.edu.trade.mq.message.OrderTimeoutMessage;
import com.dyl.edu.trade.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 订单关闭队列消费者，只接收消息并调用订单服务。
 */
@Component
public class OrderTimeoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutConsumer.class);

    private final OrderService orderService;

    public OrderTimeoutConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = OrderRabbitConstants.CLOSE_QUEUE)
    public void consume(OrderTimeoutMessage message) {
        if (message == null || message.getOrderNo() == null || message.getOrderNo().isBlank()) {
            log.error("收到非法订单超时消息，缺少订单号，message={}", message);
            return;
        }

        log.info("收到订单超时消息，messageId={}, orderId={}, orderNo={}, userId={}",
                message.getMessageId(), message.getOrderId(), message.getOrderNo(), message.getUserId());
        try {
            orderService.closeTimeoutOrder(message.getOrderNo());
        } catch (RuntimeException ex) {
            log.error("消费订单超时消息失败，将由 RabbitMQ 重新投递，messageId={}, orderNo={}, error={}",
                    message.getMessageId(), message.getOrderNo(), ex.getMessage(), ex);
            throw ex;
        }
    }
}
