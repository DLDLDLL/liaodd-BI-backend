package com.yupi.springbootinit.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.model.entity.AiFrequency;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.AiFrequencyVO;
import com.yupi.springbootinit.service.AiFrequencyService;
import com.yupi.springbootinit.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/aiFrequency")
@Slf4j
public class AiFrequencyController {
    @Resource
    AiFrequencyService aiFrequencyService;
    @Resource
    UserService userService;

    @GetMapping("/get")
    public BaseResponse<AiFrequencyVO> hasRemainFrequency(HttpServletRequest request){
        User user = userService.getLoginUser(request);
        QueryWrapper<AiFrequency> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId",user.getId());
        AiFrequency aiFrequency = aiFrequencyService.getOne(queryWrapper);
        ThrowUtils.throwIf(aiFrequency==null, ErrorCode.NULL_ERROR,"此id用户不存在");

        Integer remainFrequency = aiFrequency.getRemainFrequency();
        ThrowUtils.throwIf(remainFrequency<1,ErrorCode.PARAMS_ERROR,"调用次数不足,请充值！");

        AiFrequencyVO aiFrequencyVO = new AiFrequencyVO();
        BeanUtils.copyProperties(aiFrequency,aiFrequencyVO);
        return ResultUtils.success(aiFrequencyVO);
    }
}
