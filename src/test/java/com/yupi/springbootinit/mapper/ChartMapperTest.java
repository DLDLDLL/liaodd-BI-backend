package com.yupi.springbootinit.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class ChartMapperTest {
    @Resource
    private ChartMapper chartMapper;

    @Test
    void queryChartData() {
        long id=1774734414756999169L;
        String sql = String.format("select * from chart_%s", id);
        List<Map<String, Object>> chartData = chartMapper.queryChartData(sql);
        System.out.println(chartData);

    }
}