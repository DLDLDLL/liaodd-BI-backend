package com.yupi.springbootinit.mq.ordermq;

import com.yupi.springbootinit.constant.BIMqConstant;
import com.yupi.springbootinit.model.entity.AiFrequencyOrder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class OrderMessageProducer {
    @Resource
    RabbitTemplate rabbitTemplate;

    public void sendMessage(Long id) {
        rabbitTemplate.convertAndSend(BIMqConstant.ORDER_EXCHANGE_NAME, BIMqConstant.ORDER_ROUTING_KEY, id);
    }
}
