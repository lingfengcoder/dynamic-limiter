package com.lingfengx.mid.dynamic.limiter;


import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScans;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)

@ImportAutoConfiguration({BeanAutoConfiguration.class, DynamicLimiterBeanPostProcessor.class})
@ComponentScans({@ComponentScan("com.lingfengx.mid.dynamic.limiter"), @ComponentScan("cn.hutool.extra.spring")})
public @interface EnableDynamicLimiter {
    int redisDb() default 0;
}
