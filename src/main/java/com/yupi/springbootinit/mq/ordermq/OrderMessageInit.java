package com.yupi.springbootinit.mq.ordermq;

import com.yupi.springbootinit.constant.BIMqConstant;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 订单延迟队列（使用ttl和死信队列实现）
 */
@Configuration
public class OrderMessageInit {

    /**
     * 声明死信队列和交换机
     */
    @Bean
    Queue OrderDeadQueue() {
        return QueueBuilder.durable(BIMqConstant.ORDER_DEAD_QUEUE).build();
    }

    @Bean
    DirectExchange OrderDeadExchange() {
        return new DirectExchange(BIMqConstant.ORDER_DEAD_EXCHANGE);
    }

    /**
     * 绑定死信交换机和死信队列
     */
    @Bean
    Binding OrderDeadBinding(Queue OrderDeadQueue, DirectExchange OrderDeadExchange) {
        return BindingBuilder.bind(OrderDeadQueue).to(OrderDeadExchange).with(BIMqConstant.ORDER_DEAD_ROUTING_KEY);
    }

    /**
     * 声明正常队列和交换机
     * 同时正常队列跟死信交换机绑定
     */
    @Bean
    Queue OrderQueue() {
        //信息参数 设置TTL为10min
        Map<String, Object> arg = new HashMap<>();
        arg.put(BIMqConstant.DEAD_LETTER_TTL, BIMqConstant.ORDER_DEAD_TTL);
        //绑定死信交换机
        arg.put(BIMqConstant.DEAD_LETTER_EXCHANGE_KEY, BIMqConstant.ORDER_DEAD_EXCHANGE);
        arg.put(BIMqConstant.DEAD_LETTER_ROUTING_KEY, BIMqConstant.ORDER_DEAD_ROUTING_KEY);
        return QueueBuilder.durable(BIMqConstant.ORDER_QUEUE_NAME).withArguments(arg).build();
    }

    @Bean
    DirectExchange OrderExchange() {
        return new DirectExchange(BIMqConstant.ORDER_EXCHANGE_NAME);
    }

    /**
     * 绑定正常交换机和正常队列
     */
    @Bean
    Binding OrderBinding(Queue OrderQueue, DirectExchange OrderExchange) {
        return BindingBuilder.bind(OrderQueue).to(OrderExchange).with(BIMqConstant.ORDER_ROUTING_KEY);
    }
}
