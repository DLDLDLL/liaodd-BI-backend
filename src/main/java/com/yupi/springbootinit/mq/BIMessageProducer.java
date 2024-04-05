package com.yupi.springbootinit.mq;

import com.yupi.springbootinit.constant.BIMqConstant;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * MQ生产者
 */
@Component
public class BIMessageProducer {
    @Resource
    RabbitTemplate rabbitTemplate;

    public void sendMessage(String message){
        rabbitTemplate.convertAndSend(BIMqConstant.BI_EXCHANGE_NAME,BIMqConstant.BI_ROUTING_KEY,message);
    }
}
