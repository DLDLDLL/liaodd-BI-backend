package com.yupi.springbootinit.service.impl;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.manager.AiManager;
import com.yupi.springbootinit.manager.RedisLimiterManager;
import com.yupi.springbootinit.model.dto.chart.GenChartByAiRequest;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.enums.ChartStatusEnum;
import com.yupi.springbootinit.model.vo.BiResponse;
import com.yupi.springbootinit.service.ChartService;
import com.yupi.springbootinit.mapper.ChartMapper;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.utils.ExcelUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

import static com.yupi.springbootinit.constant.FileConstant.FILE_MAX_SIZE;
import static com.yupi.springbootinit.constant.FileConstant.VALID_FILE_SUFFIX;

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

        // 2. 校验参数
        checkInput(multipartFile, name, goal);

        // 3. 限流处理
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
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
        boolean saveReslt = save(chart);
        ThrowUtils.throwIf(!saveReslt, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        // 7. 将图表结果返回前端
        BiResponse biResponse = new BiResponse();
        biResponse.setGenChart(genChart);
        biResponse.setGenResult(genResult);
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

        // 2. 校验参数
        checkInput(multipartFile, name, goal);

        // 3. 限流处理
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
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
            System.out.println("runnuing");
            // ① 更新状态为running
            Chart statusChart = new Chart();
            statusChart.setId(chart.getId());
            statusChart.setChartStatus(ChartStatusEnum.RUNNING.getValue());
            boolean statusResult = updateById(statusChart);
            if(!statusResult){
                handleChartUpdateError(chart.getId(),"更新图表运行中状态失败");
                return;
            }
            // ② 调用AI,获取响应结果
            long BIModelId = 1774721525321445378L;
            String chatResult = aiManager.doChat(BIModelId, userInput.toString());
            String[] split = chatResult.split("【【【【【");
            if (split.length < 3) {
//                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 生成错误");
                handleChartUpdateError(chart.getId(),"AI 生成错误");
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
            if(!updateResult){
                handleChartUpdateError(chart.getId(),"更新图表成功状态失败");
            }
        },threadPoolExecutor);

        // 7. 将响应结果返回给前端
        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        return biResponse;
    }

    /**
     * 校验输入的参数
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
     * 处理图表更新失败，将状态设置为 fail
     * @param chartId
     * @param execMessage
     */
    private void handleChartUpdateError(Long chartId,String execMessage){
        Chart chart = new Chart();
        chart.setId(chartId);
        chart.setChartStatus(ChartStatusEnum.FAILED.getValue());
        chart.setExecMessage(execMessage);
        boolean updateResult = updateById(chart);
        if(!updateResult){
            log.error("更新图表失败状态失败"+chartId+","+execMessage);
        }
    }
}




