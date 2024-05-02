package com.yupi.springbootinit.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.springbootinit.model.entity.AlipayInfo;
import com.yupi.springbootinit.service.AlipayInfoService;
import com.yupi.springbootinit.mapper.AlipayInfoMapper;
import org.springframework.stereotype.Service;

/**
* @author D
* @description 针对表【alipay_info(次数订单表)】的数据库操作Service实现
* @createDate 2024-05-02 21:29:38
*/
@Service
public class AlipayInfoServiceImpl extends ServiceImpl<AlipayInfoMapper, AlipayInfo>
    implements AlipayInfoService{

}




