package com.yupi.springbootinit.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.constant.CommonConstant;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.model.dto.alipayinfo.AlipayInfoQueryRequest;
import com.yupi.springbootinit.model.entity.AlipayInfo;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.PayInfoVO;
import com.yupi.springbootinit.service.AlipayInfoService;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.utils.SqlUtils;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

@RestController
@Slf4j
@RequestMapping("/payInfo")
public class AliPayInfoController {
    @Resource
    UserService userService;
    @Resource
    AlipayInfoService alipayInfoService;

    /**
     * 获取支付订单列表
     *
     * @param request
     * @return
     */
    @GetMapping("/list")
    public BaseResponse<List<PayInfoVO>> getPayInfoList(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        QueryWrapper<AlipayInfo> queryWrapper = new QueryWrapper<>();
        Long userId = loginUser.getId();
        queryWrapper.eq("userId", userId);
        List<AlipayInfo> alipayInfos = alipayInfoService.list(queryWrapper);
        List<PayInfoVO> payInfoVOS = new ArrayList<>();
        for (AlipayInfo alipayInfo : alipayInfos) {
            PayInfoVO payInfoVO = new PayInfoVO();
            BeanUtils.copyProperties(alipayInfo, payInfoVO);
            payInfoVOS.add(payInfoVO);
        }
        return ResultUtils.success(payInfoVOS);
    }

    /**
     * 根据查询条件 分页获取支付订单列表
     *
     * @param alipayInfoQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/byPage")
    @ApiOperation(value = "（管理员）分页获取订单列表")
    public BaseResponse<Page<AlipayInfo>> listPayInfoByPage(@RequestBody AlipayInfoQueryRequest alipayInfoQueryRequest,
                                                            HttpServletRequest request) {
        long current = alipayInfoQueryRequest.getCurrent();
        long size = alipayInfoQueryRequest.getPageSize();
        Page<AlipayInfo> page = alipayInfoService.page(new Page<>(current, size),
                getAliPayQueryWrapper(alipayInfoQueryRequest));
        return ResultUtils.success(page);
    }

    /**
     * 分页获取当前用户的订单
     *
     * @param alipayInfoQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/my/page")
    @ApiOperation(value = "获取个人支付订单")
    public BaseResponse<Page<AlipayInfo>> listMyPayInfoByPage(@RequestBody AlipayInfoQueryRequest alipayInfoQueryRequest,
                                                              HttpServletRequest request) {
        ThrowUtils.throwIf(alipayInfoQueryRequest == null, ErrorCode.PARAMS_ERROR, "请求参数为空");
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        alipayInfoQueryRequest.setUserId(loginUser.getId());
        long current = alipayInfoQueryRequest.getCurrent();
        long size = alipayInfoQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<AlipayInfo> page = alipayInfoService.page(new Page<>(current, size),
                getAliPayQueryWrapper(alipayInfoQueryRequest));
        return ResultUtils.success(page);
    }

    /**
     * 获取查询包装类
     *
     * @param alipayInfoQueryRequest 查询条件
     * @return 查询结果
     */
    private QueryWrapper<AlipayInfo> getAliPayQueryWrapper(AlipayInfoQueryRequest alipayInfoQueryRequest) {
        QueryWrapper<AlipayInfo> queryWrapper = new QueryWrapper<>();
        if (alipayInfoQueryRequest == null) {
            return queryWrapper;
        }
        Long id = alipayInfoQueryRequest.getId();
        Long userId = alipayInfoQueryRequest.getUserId();
        Long orderId = alipayInfoQueryRequest.getOrderId();
        String sortField = alipayInfoQueryRequest.getSortField();
        String sortOrder = alipayInfoQueryRequest.getSortOrder();

        queryWrapper.eq(id != null && id > 0, "id", id)
                .eq(ObjectUtils.isNotEmpty(userId), "userId", userId)
                .eq(ObjectUtils.isNotEmpty(orderId), "orderId", orderId)
                .eq("isDelete", false)
                .orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_DESC), sortField);
        return queryWrapper;
    }
}
