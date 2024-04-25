package com.yupi.springbootinit.mq;

import com.rabbitmq.client.Channel;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.constant.BIMqConstant;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.manager.AiManager;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.model.enums.ChartStatusEnum;
import com.yupi.springbootinit.service.ChartService;
import com.yupi.springbootinit.utils.ExcelUtils;
import com.yupi.springbootinit.websocket.WebSocketServer;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

import static com.yupi.springbootinit.constant.CommonConstant.BI_Model_ID;

/**
 * MQ消费者
 */
@Component
public class BIMessageConsumer {
    @Resource
    ChartService chartService;
    @Resource
    AiManager aiManager;
    @Resource
    WebSocketServer webSocketServer;

    // binds:
    // 声明队列: 名称、可持久化
    // 声明交换机: 名称、类型为默认direct
    // 使用key 绑定正常队列和交换机
    // ackMode:
    // 设置为手动确认
    @RabbitListener(queues = BIMqConstant.BI_QUEUE_NAME,ackMode = "MANUAL")
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        // 消息为空则拒绝
        if(StringUtils.isBlank(message)){
            channel.basicNack(deliveryTag,false,false);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "接受到的消息为空");
        }
        // 获取图表
        Long chartId=Long.parseLong(message);
        Chart chart = chartService.getById(chartId);
        if(chart==null){
            channel.basicNack(deliveryTag,false,false);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图表为空");
        }
        // ① 更新状态为running
        Chart statusChart = new Chart();
        statusChart.setId(chartId);
        statusChart.setChartStatus(ChartStatusEnum.RUNNING.getValue());
        boolean statusResult = chartService.updateById(statusChart);
            // 更新失败
        if (!statusResult) {
            // 拒绝消息
            channel.basicNack(deliveryTag,false,false);
            // 更新状态为fail
            chartService.handleChartUpdateError(chartId, "更新图表运行中状态失败");
            return;
        }
        // ② 调用AI,获取响应结果
        String chatResult = aiManager.doChat(BI_Model_ID, buildUserInput(chart));
        String[] split = chatResult.split("【【【【【");
        if (split.length < 3) {
            channel.basicNack(deliveryTag,false,false);
            chartService.handleChartUpdateError(chartId, "AI 生成错误");
            return;
        }
        String genChart = split[1].trim();
        String genResult = split[2].trim();
        // ③ 更新数据库,更新状态为succeed
        Chart updateChart = new Chart();
        updateChart.setId(chartId);
        updateChart.setGenChart(genChart);
        updateChart.setGetResult(genResult);
        updateChart.setChartStatus(ChartStatusEnum.SUCCEED.getValue());
        boolean updateResult = chartService.updateById(updateChart);
            // 更新失败
        if (!updateResult) {
            // 拒绝消息
            channel.basicNack(deliveryTag,false,false);
            // 更新状态为fail
            chartService.handleChartUpdateError(chartId, "更新图表成功状态失败");
        }
        // 推送消息
        webSocketServer.sendMessage("您的[" + chart.getName() + "]生成成功, 前往 我的图表 进行查看",
                new HashSet<>(Arrays.asList(chart.getUserId().toString())));
        // 成功，确认消息
        channel.basicAck(deliveryTag,false);
    }

    /**
     * 构造用户输入
     * @param chart
     * @return
     */
    private String buildUserInput(Chart chart){
        String userGoal = chart.getGoal();
        String csvData=chart.getChartData();
        String chartType=chart.getChartType();

        // 1 需求：目标 或 目标+类型
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：").append("\n");
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += "，请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        // 2 原始数据
        userInput.append("原始数据：").append("\n");
        userInput.append(csvData).append("\n");

        return userInput.toString();
    }

}
