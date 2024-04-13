package com.yupi.springbootinit.mq;

import com.yupi.springbootinit.constant.BIMqConstant;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 用于创建程序的交换机和队列
 */
@Configuration
public class BIMessageInit {

    /**
     * 声明死信队列和交换机
     */
    @Bean
    Queue BiDeadQueue() {
        return QueueBuilder.durable(BIMqConstant.BI_DEAD_QUEUE).build();
    }

    @Bean
    DirectExchange BiDeadExchange() {
        return new DirectExchange(BIMqConstant.BI_DEAD_EXCHANGE);
    }

    /**
     * 绑定死信交换机和死信队列
     */
    @Bean
    Binding BiDeadBinding(Queue BiDeadQueue, DirectExchange BiDeadExchange) {
        return BindingBuilder.bind(BiDeadQueue).to(BiDeadExchange).with(BIMqConstant.BI_DEAD_ROUTING_KEY);
    }

    /**
     * 声明正常队列和交换机
     * 同时正常队列跟死信交换机绑定
     */
    @Bean
    Queue BiQueue() {
        //信息参数 设置TTL为1min
        Map<String, Object> arg = new HashMap<>();
        arg.put(BIMqConstant.DEAD_LETTER_TTL, BIMqConstant.BI_DEAD_TTL);
        //绑定死信交换机
        arg.put(BIMqConstant.DEAD_LETTER_EXCHANGE_KEY, BIMqConstant.BI_DEAD_EXCHANGE);
        arg.put(BIMqConstant.DEAD_LETTER_ROUTING_KEY, BIMqConstant.BI_DEAD_ROUTING_KEY);
        return QueueBuilder.durable(BIMqConstant.BI_QUEUE_NAME).withArguments(arg).build();
    }

    @Bean
    DirectExchange BiExchange() {
        return new DirectExchange(BIMqConstant.BI_EXCHANGE_NAME);
    }

    /**
     * 绑定正常交换机和死信队列
     */
    @Bean
    Binding BiBinding(Queue BiQueue, DirectExchange BiExchange) {
        return BindingBuilder.bind(BiQueue).to(BiExchange).with(BIMqConstant.BI_ROUTING_KEY);
    }

}
