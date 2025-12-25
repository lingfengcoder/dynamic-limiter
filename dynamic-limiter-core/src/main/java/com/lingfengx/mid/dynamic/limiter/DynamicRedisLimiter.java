package com.lingfengx.mid.dynamic.limiter;

import com.lingfengx.mid.dynamic.limiter.algo.Limiter;
import com.lingfengx.mid.dynamic.limiter.algo.LimiterAlgo;
import com.lingfengx.mid.dynamic.limiter.algo.SlidingWindowLimiter;
import com.lingfengx.mid.dynamic.limiter.algo.TokenRateLimiter;
import com.lingfengx.mid.dynamic.limiter.algo.SemaphoreLimiter;
import com.lingfengx.mid.dynamic.limiter.algo.SemaphoreWindowLimiter;
import com.lingfengx.mid.dynamic.limiter.util.RedissonInvoker;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 * 基于redis的动态限流
 */
@Slf4j
public class DynamicRedisLimiter {

    private final static ConcurrentMap<LimiterAlgo, Limiter> limiterMap = new ConcurrentHashMap<>();

    private DynamicRedisLimiter() {
    }

    public DynamicRedisLimiter(RedissonClient redissonClient) {
        // 创建 RedissonInvoker
        RedissonInvoker redissonInvoker = new RedissonInvoker(redissonClient);
        // 滑动窗口限流器
        SlidingWindowLimiter slidingWindowLimiter = new SlidingWindowLimiter(redissonInvoker);
        limiterMap.put(LimiterAlgo.SlidingWindow, slidingWindowLimiter);
        // 令牌桶限流器
        TokenRateLimiter tokenRateLimiter = new TokenRateLimiter(redissonInvoker);
        limiterMap.put(LimiterAlgo.TokenRate, tokenRateLimiter);
        // 信号量限流器（并发数限制）
        SemaphoreLimiter semaphoreLimiter = new SemaphoreLimiter(redissonInvoker);
        limiterMap.put(LimiterAlgo.Semaphore, semaphoreLimiter);
        // 信号量+窗口限流器（并发数+时间窗口限制）
        SemaphoreWindowLimiter semaphoreWindowLimiter = new SemaphoreWindowLimiter(redissonInvoker);
        limiterMap.put(LimiterAlgo.SemaphoreWindow, semaphoreWindowLimiter);
    }

    public Limiter getLimiter(LimiterAlgo limiterAlgo) {
        return limiterMap.get(limiterAlgo);
    }
}
