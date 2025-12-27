package com.lingfengx.mid.dynamic.limiter;

import com.lingfengx.mid.dynamic.limiter.algo.LimiterAlgo;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 信号量+窗口 混合算法动态限流器
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
public class SemaphoreWindowDLimiter implements DLimiter {

    /**
     * 限流 key
     */
    private String key;

    /**
     * 是否需要限流
     */
    private boolean limited = true;

    /**
     * 最大并发许可数
     */
    private int semaphorePermits = 20;

    /**
     * 信号量超时时间（毫秒，防止死锁）
     */
    private long semaphoreTimeout = 60000;

    /**
     * 窗口内最大请求数（0=不限制窗口）
     */
    private int semaphoreWindowLen = 0;

    /**
     * 窗口时间大小（毫秒）
     */
    private long semaphoreWindowTime = 60000;

    @Override
    public LimiterAlgo getAlgo() {
        return LimiterAlgo.SemaphoreWindow;
    }
}
