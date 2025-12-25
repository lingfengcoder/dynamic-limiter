package com.lingfengx.mid.dynamic.limiter.algo;

import com.lingfengx.mid.dynamic.limiter.LimiterContext;
import com.lingfengx.mid.dynamic.limiter.util.RedissonInvoker;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 信号量限流器（并发数限制器）
 * 基于 Redis Lua 脚本实现的分布式信号量
 * 
 * 特点：
 * - 精确控制同时执行的请求数
 * - 请求完成后自动释放槽位
 * - 自适应接口执行时间
 */
@Slf4j
public class SemaphoreLimiter extends AbstractLimiter {

    private final static String REDIS_PREFIX = "rdsSemaphore";
    private final static String LUA_PATH = "META-INF/lua/semaphore.lua";
    private final static String ACTION_ACQUIRE = "acquire";
    private final static String ACTION_RELEASE = "release";

    private String semaphoreScriptSha;

    public SemaphoreLimiter(RedissonInvoker redissonInvoker) {
        this.redissonInvoker = redissonInvoker;
        loadSemaphoreScript();
    }

    private void loadSemaphoreScript() {
        semaphoreScriptSha = redissonInvoker.loadScript(LUA_PATH);
        if (semaphoreScriptSha == null) {
            log.error("Failed to load lua script: {}", LUA_PATH);
        }
    }

    @Override
    protected String getPrefix() {
        return REDIS_PREFIX;
    }

    /**
     * 获取信号量（进入执行）
     *
     * @param key        限流key
     * @param maxPermits 最大并发数
     * @param requestId  请求唯一标识（用于释放）
     * @param timeout    超时时间（毫秒），防止死锁
     * @return true=获取成功，false=已达最大并发
     */
    public boolean acquire(String key, int maxPermits, String requestId, long timeout) {
        List<Object> keys = new ArrayList<>(1);
        keys.add(generateKey(key));

        long now = System.currentTimeMillis();
        Object[] args = new Object[5];
        args[0] = ACTION_ACQUIRE;
        args[1] = String.valueOf(maxPermits);
        args[2] = requestId;
        args[3] = String.valueOf(timeout);
        args[4] = String.valueOf(now);

        Object result = redissonInvoker.evalSha(semaphoreScriptSha, keys, args);
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
        List<Object> keys = new ArrayList<>(1);
        keys.add(generateKey(key));

        long now = System.currentTimeMillis();
        Object[] args = new Object[5];
        args[0] = ACTION_RELEASE;
        args[1] = "0";  // maxPermits 释放时不需要
        args[2] = requestId;
        args[3] = "0";  // timeout 释放时不需要
        args[4] = String.valueOf(now);

        redissonInvoker.evalSha(semaphoreScriptSha, keys, args);
    }

    /**
     * 获取信号量（默认超时60秒）
     */
    public boolean acquire(String key, int maxPermits, String requestId) {
        return acquire(key, maxPermits, requestId, 60000);
    }
}
