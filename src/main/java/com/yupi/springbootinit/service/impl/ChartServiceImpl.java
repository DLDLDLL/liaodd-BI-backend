package com.yupi.springbootinit.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.constant.ChartConstant;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.manager.AiManager;
import com.yupi.springbootinit.manager.RedisLimiterManager;
import com.yupi.springbootinit.model.dto.chart.ChartQueryRequest;
import com.yupi.springbootinit.model.dto.chart.GenChartByAiRequest;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.enums.ChartStatusEnum;
import com.yupi.springbootinit.model.vo.BiResponse;
import com.yupi.springbootinit.mq.BIMessageProducer;
import com.yupi.springbootinit.retry.GuavaRetrying;
import com.yupi.springbootinit.service.AiFrequencyService;
import com.yupi.springbootinit.service.ChartService;
import com.yupi.springbootinit.mapper.ChartMapper;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.utils.ExcelUtils;
import com.yupi.springbootinit.websocket.WebSocketServer;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RMap;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.yupi.springbootinit.constant.FileConstant.FILE_MAX_SIZE;
import static com.yupi.springbootinit.constant.FileConstant.VALID_FILE_SUFFIX;
import static com.yupi.springbootinit.constant.RedisConstant.*;
import static com.yupi.springbootinit.utils.ChartUtils.genDefaultChartName;

/**
 * @author D
 * @description 针对表【chart(图表信息表)】的数据库操作Service实现
 * @createDate 2024-03-30 21:20:43
 */
@Service
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart> implements ChartService {


    @Resource
    private UserService userService;
    @Resource
    private RedisLimiterManager redisLimiterManager;
    @Resource
    private AiManager aiManager;
    @Resource
    private ThreadPoolExecutor threadPoolExecutor;
    @Resource
    private BIMessageProducer biMessageProducer;
    @Resource
    private AiFrequencyService aiFrequencyService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private WebSocketServer webSocketServer;
    @Resource
    private GuavaRetrying guavaRetrying;

    /**
     * AI生成图表（同步）
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @Override
    public BiResponse genChartByAi(MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        // 1. 获取参数
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        if (StringUtils.isBlank(name)) {
            name = genDefaultChartName();
        }

        // 查询是否有查询次数
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        boolean hasFrequency = aiFrequencyService.hasFrequency(userId);
        ThrowUtils.throwIf(!hasFrequency, ErrorCode.PARAMS_ERROR, "剩余次数不足，请先充值！");

        // 2. 校验参数
        checkInput(multipartFile, name, goal);

        // 3. 限流处理
        redisLimiterManager.doRateLimit("genChartByAi_" + userId);

        // 4. 构造用户输入
        StringBuilder userInput = new StringBuilder();
        // 4.1 需求：目标 或 目标+类型
        userInput.append("分析需求：").append("\n");
        String userGoal = goal;
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += "，请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        // 4.2 原始数据
        userInput.append("原始数据：").append("\n");
        // 4.3 转csv，数据提取和压缩
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");

        // 5. 调用AI,获取响应结果
        long BIModelId = 1774721525321445378L;
        String chatResult = aiManager.doChat(BIModelId, userInput.toString());
        String[] split = chatResult.split("【【【【【");
        if (split.length < 3) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 生成错误");
        }

        // 6. 处理响应结果，将图表信息保存到数据库
        String genChart = split[1].trim();
        String genResult = split[2].trim();
//        String validGenChart = ChartUtils.getValidGenChart(genChart);
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setGenChart(genChart);
        chart.setGetResult(genResult);
        chart.setUserId(userId);
        chart.setChartStatus(ChartStatusEnum.SUCCEED.getValue());
        boolean saveReslt = save(chart);
        ThrowUtils.throwIf(!saveReslt, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        // 7. 将图表结果返回前端
        BiResponse biResponse = new BiResponse();
        biResponse.setGenChart(genChart);
        biResponse.setGenResult(genResult);

        // 调用次数-1
        boolean invokeAutoDecrease = aiFrequencyService.invokeAutoDecrease(loginUser.getId());
        ThrowUtils.throwIf(!invokeAutoDecrease, ErrorCode.PARAMS_ERROR, "次数减一失败");

        return biResponse;
    }

    /**
     * AI生成图表（异步）
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    public BiResponse genChartByAiAsync(MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        // 1. 获取参数
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        if (StringUtils.isBlank(name)) {
            name = genDefaultChartName();
        }

        // 查询是否有查询次数
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        boolean hasFrequency = aiFrequencyService.hasFrequency(userId);
        ThrowUtils.throwIf(!hasFrequency, ErrorCode.PARAMS_ERROR, "剩余次数不足，请先充值！");


        // 2. 校验参数
        checkInput(multipartFile, name, goal);

        // 3. 限流处理
        redisLimiterManager.doRateLimit("genChartByAi_" + userId);

        // 4. 构造用户输入
        StringBuilder userInput = new StringBuilder();
        // 4.1 需求：目标 或 目标+类型
        userInput.append("分析需求：").append("\n");
        String userGoal = goal;
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += "，请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        // 4.2 原始数据
        userInput.append("原始数据：").append("\n");
        // 4.3 转csv，数据提取和压缩
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");

        // 5. 先插入到数据库（除了生成的图表和结论）
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setUserId(userId);
        chart.setChartStatus(ChartStatusEnum.WAIT.getValue());
        boolean saveResult = save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        // 6.提交任务：调用AI,生成响应结果,更新数据库
        CompletableFuture.runAsync(() -> {
            // ① 更新状态为running
            Chart statusChart = new Chart();
            statusChart.setId(chart.getId());
            statusChart.setChartStatus(ChartStatusEnum.RUNNING.getValue());
            boolean statusResult = updateById(statusChart);
            if (!statusResult) {
                handleChartUpdateError(chart.getId(), "更新图表运行中状态失败");
                return;
            }
            // ② 调用AI,获取响应结果
            long BIModelId = 1774721525321445378L;
            String chatResult = aiManager.doChat(BIModelId, userInput.toString());
            String[] split = chatResult.split("【【【【【");
            if (split.length < 3) {
//                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 生成错误");
                handleChartUpdateError(chart.getId(), "AI 生成错误");
                return;
            }
            String genChart = split[1].trim();
            String genResult = split[2].trim();
            // ③ 更新数据库
            Chart updateChart = new Chart();
            updateChart.setId(chart.getId());
            updateChart.setGenChart(genChart);
            updateChart.setGetResult(genResult);
            updateChart.setChartStatus(ChartStatusEnum.SUCCEED.getValue());
            boolean updateResult = updateById(updateChart);
            if (!updateResult) {
                handleChartUpdateError(chart.getId(), "更新图表成功状态失败");
            }
            // 推送消息
            try {
                webSocketServer.sendMessage("您的[" + chart.getName() + "]生成成功 , 前往 我的图表 进行查看",
                        new HashSet<>(Arrays.asList(chart.getUserId().toString())));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, threadPoolExecutor);

        // 7. 将响应结果返回给前端
        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());

        // 调用次数-1
        boolean invokeAutoDecrease = aiFrequencyService.invokeAutoDecrease(loginUser.getId());
        ThrowUtils.throwIf(!invokeAutoDecrease, ErrorCode.PARAMS_ERROR, "次数减一失败");

        return biResponse;
    }

    /**
     * AI生成图表（消息队列异步）
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    public BiResponse genChartByAiAsyncMq(MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        // 1. 获取参数
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        if (StringUtils.isBlank(name)) {
            name = genDefaultChartName();
        }
        // 查询是否有查询次数
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        boolean hasFrequency = aiFrequencyService.hasFrequency(userId);
        ThrowUtils.throwIf(!hasFrequency, ErrorCode.PARAMS_ERROR, "剩余次数不足，请先充值！");

        // 2. 校验参数
        checkInput(multipartFile, name, goal);

        // 3. 限流处理
        redisLimiterManager.doRateLimit("genChartByAi_" + userId);

        // 4. 转csv，数据提取和压缩
        String csvData = ExcelUtils.excelToCsv(multipartFile);

        // 5. 先插入到数据库（除了生成的图表和结论）
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setUserId(userId);
        chart.setChartStatus(ChartStatusEnum.WAIT.getValue());
        boolean saveResult = save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        // 6.发送到消息队列：调用AI,生成响应结果,更新数据库
        biMessageProducer.sendMessage(String.valueOf(chart.getId()));

        // 7. 将响应结果返回给前端
        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());

        // 调用次数-1
        boolean invokeAutoDecrease = aiFrequencyService.invokeAutoDecrease(loginUser.getId());
        ThrowUtils.throwIf(!invokeAutoDecrease, ErrorCode.PARAMS_ERROR, "次数减一失败");

        return biResponse;
    }


    /**
     * 校验输入的参数
     *
     * @param multipartFile
     * @param name
     * @param goal
     */
    private void checkInput(MultipartFile multipartFile, String name, String goal) {
        // 校验目标和名称
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        // 校验文件
        long fileSize = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        // 校验文件大小
        ThrowUtils.throwIf(fileSize > FILE_MAX_SIZE, ErrorCode.PARAMS_ERROR, "文件大小超过 1M");
        // 校验文件后缀
        String suffix = FileUtil.getSuffix(originalFilename);
        ThrowUtils.throwIf(!VALID_FILE_SUFFIX.contains(suffix), ErrorCode.PARAMS_ERROR, "不支持该类型文件");
    }

    /**
     * 处理图表更新失败，将状态设置为 fail（使用guava retry）
     *
     * @param chartId
     * @param execMessage
     */
    public void handleChartUpdateError(Long chartId, String execMessage) {
        Chart chart = new Chart();
        chart.setId(chartId);
        chart.setChartStatus(ChartStatusEnum.FAILED.getValue());
        chart.setExecMessage(execMessage);
        Boolean updateResult = guavaRetrying.retryUpdateChart(chart);
        if (updateResult==null) {
            log.error("更新图表失败状态失败" + chartId + "," + execMessage);
        }
    }

    /**
     * 根据id获取图表（缓存）
     *
     * @param id
     * @param request
     * @return
     */
    @Override
    public Chart getChartByIdCache(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 从缓存中获取
        String chartKey = CHART_ID_KEY + id;
        String chartStr = stringRedisTemplate.opsForValue().get(chartKey);
        // 缓存命中，直接返回
        if (StringUtils.isNotBlank(chartStr)) {
            return JSONUtil.toBean(chartStr, Chart.class);
        }
        // 未命中，从数据库获取并存入缓存
        Chart chart = getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        stringRedisTemplate.opsForValue().set(chartKey, JSONUtil.toJsonStr(chart), CHART_TTL, TimeUnit.MINUTES);
        return chart;
    }

}




