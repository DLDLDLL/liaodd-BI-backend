package com.yupi.springbootinit.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.springbootinit.model.dto.order.AiFrequencyOrderQueryRequest;
import com.yupi.springbootinit.model.dto.order.AiFrequencyOrderUpdateRequest;
import com.yupi.springbootinit.model.entity.AiFrequencyOrder;

import javax.servlet.http.HttpServletRequest;

/**
* @author D
* @description 针对表【ai_frequency_order(次数订单表)】的数据库操作Service
* @createDate 2024-05-02 21:29:38
*/
public interface AiFrequencyOrderService extends IService<AiFrequencyOrder> {

    QueryWrapper<AiFrequencyOrder> getOrderQueryWrapper(AiFrequencyOrderQueryRequest orderQueryRequest);

    boolean updateOrderInfo(AiFrequencyOrderUpdateRequest orderUpdateRequest, HttpServletRequest request);
}
