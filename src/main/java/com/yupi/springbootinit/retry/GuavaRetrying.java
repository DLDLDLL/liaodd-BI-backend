package com.yupi.springbootinit.retry;

import com.github.rholder.retry.*;
import com.yupi.springbootinit.manager.AiManager;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.service.ChartService;
import com.yupi.springbootinit.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.yupi.springbootinit.constant.CommonConstant.BI_Model_ID;

@Component
@Slf4j
public class GuavaRetrying {
    @Resource
    private AiManager aiManager;
    @Resource
    private ChartService chartService;

    /**
     * AI生成错误，重试
     *
     * @param userInput
     * @return
     */
    public String retryDoChart(String userInput) {

        String aiResult = null;

        // 定义重试器
        Retryer<String> retryer = RetryerBuilder.<String>newBuilder()
                .retryIfResult(Objects::isNull) // 1. 如果结果为空则重试
                .retryIfResult(result -> { // 2. 生成的结果有误则重试（result是执行方法doChat时的返回值）
                    String[] splits = result.split("【【【【【");
                    if (splits.length < 3) {
                        return true;
                    }
                    String genChart = splits[1].trim(); // 生成的代码
                    if (StringUtils.isValidJson(genChart)) {
                        // 格式正确，不重试
                        return false;
                    }
                    return true;
                })
                .retryIfExceptionOfType(IOException.class) // 3. 发生IO异常则重试
                .retryIfRuntimeException() // 4. 发生运行时异常则重试
                // 初始等待时间，即第一次重试和第二次重试之间的等待时间为 3 秒。
                // 等待时间的增量，意味着每次重试后的等待时间会在前一次的基础上增加 2 秒。
                .withWaitStrategy(WaitStrategies.incrementingWait(3, TimeUnit.SECONDS, 2, TimeUnit.SECONDS))
                .withStopStrategy(StopStrategies.stopAfterAttempt(3)) // 允许执行3次（首次执行 + 最多重试2次）
                .withRetryListener(new MyRetryListener()) // 添加自定义的重试监听器
                .build();

        // 定义重试方法：重新调用AI生成
        Callable<String> doChat = () -> {
            return aiManager.doChat(BI_Model_ID, userInput);
        };

        // 业务逻辑
        try {
            aiResult = retryer.call(doChat);// 执行需要重试机制的方法
        } catch (RetryException | ExecutionException e) { // 重试次数超过阈值或被强制中断
            log.error("AI生成重试失败");
            e.printStackTrace();
        }

        //返回重试结果
        return aiResult;
    }

    /**
     * 重试更新图表
     *
     * @param chart
     * @return
     */
    public Boolean retryUpdateChart(Chart chart) {

        Boolean updateResult = null;

        // 定义重试器
        Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
                .retryIfResult(Objects::isNull) // 1. 如果结果为空则重试
                .retryIfResult(result -> !result) // 2. 生成的结果有误则重试（result是执行方法doChat时的返回值）
                // 初始等待时间，即第一次重试和第二次重试之间的等待时间为 3 秒。
                // 等待时间的增量，意味着每次重试后的等待时间会在前一次的基础上增加 2 秒。
                .withWaitStrategy(WaitStrategies.incrementingWait(3, TimeUnit.SECONDS, 2, TimeUnit.SECONDS))
                .withStopStrategy(StopStrategies.stopAfterAttempt(3)) // 允许执行3次（首次执行 + 最多重试2次）
                .withRetryListener(new MyRetryListener()) // 添加自定义的重试监听器
                .build();

        // 重试方法
        Callable<Boolean> updateChart = () -> {
            return chartService.updateById(chart);
        };

        // 业务逻辑
        try {
            updateResult = retryer.call(updateChart);// 执行需要重试机制的方法
        } catch (RetryException | ExecutionException e) { // 重试次数超过阈值或被强制中断
            log.error("更新图表重试失败");
            e.printStackTrace();
        }

        //返回重试结果
        return updateResult;
    }
}
