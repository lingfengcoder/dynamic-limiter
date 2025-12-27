package com.lingfengx.mid.dynamic.limiter;

import com.lingfengx.mid.dynamic.limiter.algo.LimiterAlgo;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 信号量算法动态限流器
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
public class SemaphoreDLimiter implements DLimiter {

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

    @Override
    public LimiterAlgo getAlgo() {
        return LimiterAlgo.Semaphore;
    }
}
