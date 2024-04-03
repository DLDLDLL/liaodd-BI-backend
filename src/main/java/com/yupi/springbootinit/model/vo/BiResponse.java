package com.yupi.springbootinit.model.vo;

import lombok.Data;

/**
 * Bi 生成图表的返回结果
 */
@Data
public class BiResponse {
    private Long chartId;
    private String genChart;
    private String genResult;
}
