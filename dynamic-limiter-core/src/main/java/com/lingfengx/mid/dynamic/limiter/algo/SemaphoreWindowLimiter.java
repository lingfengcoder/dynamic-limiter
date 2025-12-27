package com.lingfengx.mid.dynamic.limiter.algo;

import com.lingfengx.mid.dynamic.limiter.util.RedissonInvoker;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 信号量+窗口 混合限流器
 * 基于 Redis Lua 脚本实现，同时控制：
 * 1. 最大并发数（信号量）
 * 2. 时间窗口内最大请求数（滑动窗口）
 * 
 * 特点：
 * - 精确控制同时执行的请求数
 * - 同时限制时间窗口内的总请求数
 * - 请求完成后自动释放并发槽位
 * - 窗口统计在 Acquire 时记录
 */
@Slf4j
public class SemaphoreWindowLimiter extends AbstractLimiter {

    private final static String REDIS_PREFIX = "rdsSemaphoreWindow";
    private final static String LUA_PATH = "META-INF/lua/semaphore_window.lua";
    private final static String ACTION_ACQUIRE = "acquire";
    private final static String ACTION_RELEASE = "release";

    private String scriptSha;

    public SemaphoreWindowLimiter(RedissonInvoker redissonInvoker) {
        this.redissonInvoker = redissonInvoker;
        loadScript();
    }

    private void loadScript() {
        scriptSha = redissonInvoker.loadScript(LUA_PATH);
        if (scriptSha == null) {
            log.error("Failed to load lua script: {}", LUA_PATH);
        }
    }

    @Override
    protected String getPrefix() {
        return REDIS_PREFIX;
    }

    /**
     * 获取信号量+窗口限制
     *
     * @param key            限流key
     * @param maxPermits     最大并发数
     * @param requestId      请求唯一标识（用于释放）
     * @param timeout        并发超时时间（毫秒），防止死锁
     * @param windowLen      窗口内最大请求数（0=不限制窗口）
     * @param windowTime     窗口时间大小（毫秒）
     * @return true=获取成功，false=被限流
     */
    public boolean acquire(String key, int maxPermits, String requestId, 
                          long timeout, int windowLen, long windowTime) {
        List<Object> keys = new ArrayList<>(2);
        keys.add(generateKey(key));                    // 并发控制 key
        keys.add(generateKey(key) + ":window");        // 窗口统计 key

        long now = System.currentTimeMillis();
        Object[] args = new Object[7];
        args[0] = ACTION_ACQUIRE;
        args[1] = String.valueOf(maxPermits);
        args[2] = requestId;
        args[3] = String.valueOf(timeout);
        args[4] = String.valueOf(now);
        args[5] = String.valueOf(windowLen);
        args[6] = String.valueOf(windowTime);

        Object result = redissonInvoker.evalSha(scriptSha, keys, args);
        if (result == null || "0".equals(result.toString())) {
            return false;
        }
        return "1".equals(result.toString());
    }

    /**
     * 释放信号量（执行完成）
     *
     * @param key       限流key
     * @param requestId 请求唯一标识
     */
    public void releaseSemaphore(String key, String requestId) {
        List<Object> keys = new ArrayList<>(2);
        keys.add(generateKey(key));
        keys.add(generateKey(key) + ":window");

        long now = System.currentTimeMillis();
        Object[] args = new Object[7];
        args[0] = ACTION_RELEASE;
        args[1] = "0";  // maxPermits 释放时不需要
        args[2] = requestId;
        args[3] = "0";  // timeout 释放时不需要
        args[4] = String.valueOf(now);
        args[5] = "0";  // windowLen 释放时不需要
        args[6] = "0";  // windowTime 释放时不需要

        redissonInvoker.evalSha(scriptSha, keys, args);
    }

    /**
     * 获取信号量（仅并发控制，不启用窗口限制）
     */
    public boolean acquire(String key, int maxPermits, String requestId, long timeout) {
        return acquire(key, maxPermits, requestId, timeout, 0, 0);
    }
}
