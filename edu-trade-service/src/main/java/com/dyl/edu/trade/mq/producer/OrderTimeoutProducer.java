package com.dyl.edu.trade.mq.producer;

import com.dyl.edu.trade.constant.OrderRabbitConstants;
import com.dyl.edu.trade.mq.message.OrderTimeoutMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 订单超时消息生产者，只负责向延迟交换机发送消息。
 */
@Component
public class OrderTimeoutProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public OrderTimeoutProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void send(OrderTimeoutMessage message) {
        log.info("开始发送订单超时消息，messageId={}, orderId={}, orderNo={}, timeoutSeconds={}",
                message.getMessageId(), message.getOrderId(), message.getOrderNo(), message.getTimeoutSeconds());
        rabbitTemplate.convertAndSend(
                OrderRabbitConstants.TIMEOUT_EXCHANGE,
                OrderRabbitConstants.TIMEOUT_ROUTING_KEY,
                message,
                rabbitMessage -> {
                    rabbitMessage.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return rabbitMessage;
                });
        log.info("订单超时消息发送完成，messageId={}, orderNo={}",
                message.getMessageId(), message.getOrderNo());
    }
}
