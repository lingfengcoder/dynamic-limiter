package com.lingfengx.mid.dynamic.limiter;

import com.lingfengx.mid.dynamic.limiter.algo.Limiter;
import com.lingfengx.mid.dynamic.limiter.algo.LimiterAlgo;
import com.lingfengx.mid.dynamic.limiter.algo.SlidingWindowLimiter;
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
        // 限流器
        SlidingWindowLimiter slidingWindowLimiter = new SlidingWindowLimiter(redissonInvoker);
        limiterMap.put(LimiterAlgo.SlidingWindow, slidingWindowLimiter);
    }

    public Limiter switchLimiter(LimiterAlgo limiterAlgo) {
        return limiterMap.get(limiterAlgo);
    }
}
