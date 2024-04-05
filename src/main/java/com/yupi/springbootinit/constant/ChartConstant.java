package com.yupi.springbootinit.constant;

public interface ChartConstant {
    /**
     * 提取生成的图表的Echarts配置的正则
     */
    String GEN_CHART_REGEX = "\\{(?>[^{}]*(?:\\{[^{}]*}[^{}]*)*)}";

    /**
     * 图表默认名称的前缀
     */
    String DEFAULT_CHART_NAME_PREFIX = "分析图表_";

    /**
     * 图表默认名称的后缀长度
     */
    int DEFAULT_CHART_NAME_SUFFIX_LEN = 10;
}
