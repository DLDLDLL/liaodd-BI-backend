package com.yupi.springbootinit.mq.ordermq;

import com.rabbitmq.client.Channel;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.constant.BIMqConstant;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.model.entity.AiFrequencyOrder;
import com.yupi.springbootinit.model.enums.PayOrderEnum;
import com.yupi.springbootinit.service.AiFrequencyOrderService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * 订单死信队列
 */
@Component
public class OrderMessageConsumer {
    @Resource
    AiFrequencyOrderService aiFrequencyOrderService;

    @RabbitListener(queues = BIMqConstant.ORDER_DEAD_QUEUE, ackMode = "MANUAL")
    public void receiveMessage(Long id, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTags) throws IOException {
        // 消息为空
        if (id == null) {
            channel.basicNack(deliveryTags, false, false);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "接受到的消息为空");
        }

        AiFrequencyOrder aiFrequencyOrder = aiFrequencyOrderService.getById(id);
        Integer status = aiFrequencyOrder.getOrderStatus();
        // 还未支付，更新订单状态
        if (!status.equals(Integer.valueOf(PayOrderEnum.COMPLETE.getValue()))) {
            aiFrequencyOrder.setOrderStatus(Integer.valueOf(PayOrderEnum.TIMEOUT_ORDER.getValue()));
            boolean update = aiFrequencyOrderService.updateById(aiFrequencyOrder);
            if (!update) {
                channel.basicNack(deliveryTags, false, false);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新订单失败");
            }
        }
        channel.basicAck(deliveryTags, false);

    }
}
