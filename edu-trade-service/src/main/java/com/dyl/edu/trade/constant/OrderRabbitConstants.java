package com.dyl.edu.trade.constant;

/**
 * 订单超时 RabbitMQ 拓扑常量。
 */
public final class OrderRabbitConstants {

    public static final String TIMEOUT_EXCHANGE = "order.timeout.exchange";
    public static final String TIMEOUT_DELAY_QUEUE = "order.timeout.delay.queue";
    public static final String TIMEOUT_ROUTING_KEY = "order.timeout";
    public static final String CLOSE_EXCHANGE = "order.close.exchange";
    public static final String CLOSE_QUEUE = "order.close.queue";
    public static final String CLOSE_ROUTING_KEY = "order.close";

    private OrderRabbitConstants() {
    }
}
