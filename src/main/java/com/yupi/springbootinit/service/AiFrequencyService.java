package com.yupi.springbootinit.service;

import com.yupi.springbootinit.model.entity.AiFrequency;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author D
* @description 针对表【ai_frequency(ai调用次数表)】的数据库操作Service
* @createDate 2024-04-05 20:00:24
*/
public interface AiFrequencyService extends IService<AiFrequency> {
    public boolean invokeAutoDecrease(long userId);
    public boolean hasFrequency(long userId);
}
