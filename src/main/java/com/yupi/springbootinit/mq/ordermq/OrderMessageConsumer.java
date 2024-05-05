package com.yupi.springbootinit.mq.ordermq;

import com.rabbitmq.client.Channel;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.constant.BIMqConstant;
import com.yupi.springbootinit.exception.BusinessException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 订单死信队列
 */
@Component
public class OrderMessageConsumer {

    @RabbitListener(queues = BIMqConstant.ORDER_QUEUE_NAME, ackMode = "MANUAL")
    public void receiveMessage(Long id, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTags) throws IOException {
        // 消息为空
        if (id == null) {
            channel.basicNack(deliveryTags, false, false);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "接受到的消息为空");
        }

    }
}
