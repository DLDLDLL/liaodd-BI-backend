package com.yupi.springbootinit.service;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.springbootinit.model.dto.chart.ChartQueryRequest;
import com.yupi.springbootinit.model.dto.chart.GenChartByAiRequest;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.model.vo.BiResponse;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author D
* @description 针对表【chart(图表信息表)】的数据库操作Service
* @createDate 2024-03-30 21:20:43
*/
public interface ChartService extends IService<Chart> {


    BiResponse genChartByAi(MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, HttpServletRequest request);

    BiResponse genChartByAiAsync(MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, HttpServletRequest request);

    BiResponse genChartByAiAsyncMq(MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, HttpServletRequest request);

    void handleChartUpdateError(Long chartId, String execMessage);

    Chart getChartByIdCache(long id, HttpServletRequest request);

}
