package com.lingfengx.mid.dynamic.limiter;

import com.lingfengx.mid.dynamic.limiter.algo.LimiterAlgo;

/**
 * 动态限流器接口
 * 不同算法实现各自的子类
 */
public interface DLimiter {

    /**
     * 获取限流 key
     */
    String getKey();

    /**
     * 是否需要限流
     */
    boolean isLimited();

    /**
     * 获取限流算法类型
     */
    LimiterAlgo getAlgo();
}
