package com.yupi.springbootinit.constant;

public interface ChartConstant {
    /**
     * 提取生成的图表的Echarts配置的正则
     */
    String GEN_CHART_REGEX = "\\{(?>[^{}]*(?:\\{[^{}]*}[^{}]*)*)}";
}
