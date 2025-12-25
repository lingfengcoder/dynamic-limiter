package com.lingfengx.mid.dynamic.limiter;

import com.lingfengx.mid.dynamic.limiter.algo.LimiterAlgo;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RdsLimit {

    //开关
    boolean enable() default true;

    String key() default "rdslimit";

    //窗口大小
    int boxLen() default -1;

    TimeUnit unit() default TimeUnit.MILLISECONDS;

    //窗口时间
    long boxTime() default 5L;

    //获取限流的超时时间
    long timeout() default 10;

    //被限流处理
    String fallBack() default "";

    //限流异常处理
    String errorBack() default "";

    //在spring容器中的配置类
    Class<? extends RdsLimitConfig> configBean() default NoneClass.class;

    /**
     * 动态配置的类
     */
    String dynamicConfig() default "";

    //任务完成是否主动释放
    boolean autoRelease() default false;

    //限流算法
    LimiterAlgo algo() default LimiterAlgo.SlidingWindow;

    //令牌桶容量（令牌桶算法专用）
    int tokenCapacity() default 100;

    //令牌填充速率（令牌/秒，令牌桶算法专用）
    int tokenRate() default 10;

    //每次请求消耗的令牌数（令牌桶算法专用）
    int tokenPermits() default 1;

    //最大并发许可数（信号量算法专用）
    int semaphorePermits() default 20;

    //信号量超时时间（毫秒，防止死锁，信号量算法专用）
    long semaphoreTimeout() default 60000;

    //窗口内最大请求数（信号量+窗口算法专用，0=不限制）
    int semaphoreWindowLen() default 0;

    //窗口时间大小（毫秒，信号量+窗口算法专用）
    long semaphoreWindowTime() default 60000;

    //是否开启内部降级队列，开启后，限流异常时，会将请求放入降级队列，随后被调度执行
    boolean useFallbackQueue() default false;

    //是否开启快速重试队列
    boolean enableFastRetryQueue() default false;

    //方法执行失败后，自动重试的次数
    int errorRetryCount() default 0;
}
