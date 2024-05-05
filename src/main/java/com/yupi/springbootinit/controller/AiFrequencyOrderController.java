package com.yupi.springbootinit.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.springbootinit.annotation.AuthCheck;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.DeleteRequest;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.constant.CommonConstant;
import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.model.dto.order.AiFrequencyOrderCancelRequest;
import com.yupi.springbootinit.model.dto.order.AiFrequencyOrderQueryRequest;
import com.yupi.springbootinit.model.dto.order.AiFrequencyOrderUpdateRequest;
import com.yupi.springbootinit.model.entity.AiFrequencyOrder;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.enums.PayOrderEnum;
import com.yupi.springbootinit.model.vo.AiFrequencyOrderVO;
import com.yupi.springbootinit.mq.ordermq.OrderMessageProducer;
import com.yupi.springbootinit.service.AiFrequencyOrderService;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.utils.SqlUtils;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/order")
@Slf4j
public class AiFrequencyOrderController {
    @Resource
    UserService userService;
    @Resource
    AiFrequencyOrderService aiFrequencyOrderService;
    @Resource
    OrderMessageProducer orderMessageProducer;

    final Double price = 0.1;

    /**
     * 下单
     *
     * @param total
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Boolean> addOrder(long total, HttpServletRequest request) {
        // 校验
        ThrowUtils.throwIf(total < 0, ErrorCode.PARAMS_ERROR, "请输入正确的次数");
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        // 保存订单信息
        Long userId = loginUser.getId();
        double totalAmount = total * price;
        AiFrequencyOrder aiFrequencyOrder = new AiFrequencyOrder();
        aiFrequencyOrder.setUserId(userId);
        aiFrequencyOrder.setPrice(price); //单价
        aiFrequencyOrder.setTotalAmount(totalAmount); //总金额
        aiFrequencyOrder.setPurchaseQuantity(total); //总数量
        boolean save = aiFrequencyOrderService.save(aiFrequencyOrder);
        ThrowUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR);

        // 发送到延迟队列
        orderMessageProducer.sendMessage(aiFrequencyOrder.getId());
        return ResultUtils.success(true);
    }

    /**
     * 获取订单列表
     *
     * @param request
     * @return
     */
    @GetMapping("/list")
    public BaseResponse<List<AiFrequencyOrderVO>> getOrderList(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        long userId = loginUser.getId();
        QueryWrapper<AiFrequencyOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("userId", userId);
        List<AiFrequencyOrder> frequencyOrderList = aiFrequencyOrderService.list(wrapper);
        List<AiFrequencyOrderVO> frequencyOrderVOList = new ArrayList<>();
        for (AiFrequencyOrder frequencyOrder : frequencyOrderList) {
            AiFrequencyOrderVO frequencyOrderVO = new AiFrequencyOrderVO();
            BeanUtils.copyProperties(frequencyOrder, frequencyOrderVO);
            frequencyOrderVOList.add(frequencyOrderVO);
        }
        return ResultUtils.success(frequencyOrderVOList);
    }

    /**
     * 分页获取订单列表
     *
     * @param orderQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/byPage")
    @ApiOperation(value = "（管理员）分页获取订单列表")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AiFrequencyOrder>> listOrderByPage(@RequestBody AiFrequencyOrderQueryRequest orderQueryRequest,
                                                                HttpServletRequest request) {
        long current = orderQueryRequest.getCurrent();
        long size = orderQueryRequest.getPageSize();
        Page<AiFrequencyOrder> orderPage = aiFrequencyOrderService.page(new Page<>(current, size),
                aiFrequencyOrderService.getOrderQueryWrapper(orderQueryRequest));
        return ResultUtils.success(orderPage);
    }

    /**
     * 分页获取当前用户的订单
     *
     * @param aiFrequencyOrderQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
    @ApiOperation(value = "获取个人订单")
    public BaseResponse<Page<AiFrequencyOrder>> listMyOrderByPage(@RequestBody AiFrequencyOrderQueryRequest aiFrequencyOrderQueryRequest,
                                                                  HttpServletRequest request) {
        ThrowUtils.throwIf(aiFrequencyOrderQueryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        aiFrequencyOrderQueryRequest.setUserId(loginUser.getId());
        long current = aiFrequencyOrderQueryRequest.getCurrent();
        long size = aiFrequencyOrderQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<AiFrequencyOrder> chartPage = aiFrequencyOrderService.page(new Page<>(current, size),
                getQueryWrapper(aiFrequencyOrderQueryRequest));
        return ResultUtils.success(chartPage);
    }

    /**
     * 删除订单
     *
     * @param deleteRequest
     * @return
     */
    @PostMapping("/delete")
    @ApiOperation(value = "删除订单")
    public BaseResponse<Boolean> deleteOrder(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean deleteResult = aiFrequencyOrderService.removeById(deleteRequest.getId());//逻辑删除
        ThrowUtils.throwIf(!deleteResult, ErrorCode.OPERATION_ERROR, "删除失败");
        return ResultUtils.success(true);
    }

    /**
     * 取消订单
     *
     * @param cancelRequest
     * @return
     */
    @PostMapping("/cancel")
    @ApiOperation(value = "取消订单")
    public BaseResponse<Boolean> cancelOrder(@RequestBody AiFrequencyOrderCancelRequest cancelRequest, HttpServletRequest request) {
        if (cancelRequest == null || cancelRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        Long id = cancelRequest.getId();
        Long userId = cancelRequest.getUserId();
        ThrowUtils.throwIf(id < 0 || userId < 0, ErrorCode.PARAMS_ERROR);

        AiFrequencyOrder order = new AiFrequencyOrder();
        BeanUtils.copyProperties(cancelRequest, order);
        order.setOrderStatus(Integer.valueOf(PayOrderEnum.CANCEL_ORDER.getValue()));
        boolean result = aiFrequencyOrderService.updateById(order);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        return ResultUtils.success(true);
    }

    /**
     * 修改订单
     *
     * @param orderUpdateRequest
     * @param request
     * @return
     */
    @PostMapping("/update")
    @ApiOperation(value = "修改订单信息")
    public BaseResponse<Boolean> updateOrder(@RequestBody AiFrequencyOrderUpdateRequest orderUpdateRequest,
                                             HttpServletRequest request) {
        if (orderUpdateRequest == null || orderUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        AiFrequencyOrder aiFrequencyOrder = new AiFrequencyOrder();
        BeanUtils.copyProperties(orderUpdateRequest, aiFrequencyOrder);
        boolean result = aiFrequencyOrderService.updateOrderInfo(orderUpdateRequest, request);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 获取查询包装类
     *
     * @param aiFrequencyOrderQueryRequest 图表查询条件
     * @return 查询结果
     */
    private QueryWrapper<AiFrequencyOrder> getQueryWrapper(AiFrequencyOrderQueryRequest aiFrequencyOrderQueryRequest) {
        QueryWrapper<AiFrequencyOrder> queryWrapper = new QueryWrapper<>();
        if (aiFrequencyOrderQueryRequest == null) {
            return queryWrapper;
        }

        Long id = aiFrequencyOrderQueryRequest.getId();
        Long userId = aiFrequencyOrderQueryRequest.getUserId();
        String sortField = aiFrequencyOrderQueryRequest.getSortField();
        String sortOrder = aiFrequencyOrderQueryRequest.getSortOrder();

        // 根据前端传来条件进行拼接查询条件
        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_DESC),
                sortField);
        return queryWrapper;
    }

}
