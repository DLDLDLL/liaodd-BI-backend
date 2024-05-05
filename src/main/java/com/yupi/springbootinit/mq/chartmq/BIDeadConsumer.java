package com.yupi.springbootinit.mq.chartmq;

import com.rabbitmq.client.Channel;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.constant.BIMqConstant;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.model.enums.ChartStatusEnum;
import com.yupi.springbootinit.retry.GuavaRetrying;
import com.yupi.springbootinit.service.ChartService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * 处理死信：标记为失败
 */
@Component
public class BIDeadConsumer {
    @Resource
    ChartService chartService;
    @Resource
    GuavaRetrying guavaRetrying;

    @RabbitListener(queues = BIMqConstant.BI_DEAD_QUEUE, ackMode = "MANUAL")
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        // 消息为空则拒绝
        if (StringUtils.isBlank(message)) {
            channel.basicNack(deliveryTag, false, false);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "接受到的消息为空");
        }
        // 获取图表
        Long chartId = Long.parseLong(message);
        Chart chart = chartService.getById(chartId);
        if (chart == null) {
            channel.basicNack(deliveryTag, false, false);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图表为空");
        }
        // 更新状态为fail
        Chart statusChart = new Chart();
        statusChart.setId(chartId);
        statusChart.setChartStatus(ChartStatusEnum.FAILED.getValue());
        boolean statusResult = chartService.updateById(statusChart);
        // 更新失败
        if (!statusResult) {
            // 拒绝消息，重新入队处理
            channel.basicNack(deliveryTag, false, true);
            return;
        }
        // 成功，确认
        channel.basicAck(deliveryTag,false);
    }
}
