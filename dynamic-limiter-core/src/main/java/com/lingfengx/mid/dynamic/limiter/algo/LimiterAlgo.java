package com.lingfengx.mid.dynamic.limiter.algo;

/**
 * 限流算法
 */
public enum LimiterAlgo {
    SlidingWindow,      // 滑动窗口
    TokenRate,          // 令牌桶
    Semaphore,          // 信号量（并发数限制）
    SemaphoreWindow     // 信号量+窗口（并发数+时间窗口限制）
}
