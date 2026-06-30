package com.dyl.edu.trade.config;

import com.dyl.edu.trade.constant.OrderRabbitConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 订单超时交换机、TTL 队列和死信路由配置。
 */
@Configuration
public class RabbitMqConfig {

    @Bean
    public DirectExchange orderTimeoutExchange() {
        return new DirectExchange(OrderRabbitConstants.TIMEOUT_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderTimeoutDelayQueue(@Value("${order.timeout.seconds:30}") long timeoutSeconds) {
        return QueueBuilder.durable(OrderRabbitConstants.TIMEOUT_DELAY_QUEUE)
                .withArgument("x-message-ttl", timeoutSeconds * 1000L)
                .deadLetterExchange(OrderRabbitConstants.CLOSE_EXCHANGE)
                .deadLetterRoutingKey(OrderRabbitConstants.CLOSE_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding orderTimeoutBinding(Queue orderTimeoutDelayQueue,
                                       DirectExchange orderTimeoutExchange) {
        return BindingBuilder.bind(orderTimeoutDelayQueue)
                .to(orderTimeoutExchange)
                .with(OrderRabbitConstants.TIMEOUT_ROUTING_KEY);
    }

    @Bean
    public DirectExchange orderCloseExchange() {
        return new DirectExchange(OrderRabbitConstants.CLOSE_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderCloseQueue() {
        return QueueBuilder.durable(OrderRabbitConstants.CLOSE_QUEUE).build();
    }

    @Bean
    public Binding orderCloseBinding(Queue orderCloseQueue,
                                     DirectExchange orderCloseExchange) {
        return BindingBuilder.bind(orderCloseQueue)
                .to(orderCloseExchange)
                .with(OrderRabbitConstants.CLOSE_ROUTING_KEY);
    }

    /**
     * 使用 JSON 传输独立的 MQ Message 对象。
     */
    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
