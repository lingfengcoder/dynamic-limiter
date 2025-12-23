package com.lingfengx.mid.dynamic.limiter;


import org.springframework.context.annotation.Import;

import java.lang.annotation.*;


@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(BeanAutoConfiguration.class)
public @interface EnableDynamicLimiter {
    
    /**
     * 命名空间，用于隔离不同应用的降级队列
     * 建议使用应用名称，如 "order-service"
     */
    String namespace() default "";
    
    /**
     * Redis 数据库索引（已废弃，使用 Redisson 配置）
     */
    @Deprecated
    int redisDb() default 0;
}
