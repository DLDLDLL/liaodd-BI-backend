package com.yupi.springbootinit.manager;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class RedisLimiterManagerTest {
    @Resource
    RedisLimiterManager redisLimiterManager;
    @Test
    void doRateLimit() {
        String userid="1";
        for (int i = 0; i < 2; i++) {
            redisLimiterManager.doRateLimit(userid);
            System.out.println("成功");
        }
        try {
            sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        for (int i = 0; i < 5; i++) {
            redisLimiterManager.doRateLimit(userid);
            System.out.println("成功");
        }
    }
}