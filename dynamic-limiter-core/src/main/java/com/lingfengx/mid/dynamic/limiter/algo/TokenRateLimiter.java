package com.lingfengx.mid.dynamic.limiter.algo;

import com.lingfengx.mid.dynamic.limiter.util.RedissonInvoker;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 令牌桶限流器
 * 基于 Redis Lua 脚本实现的分布式令牌桶算法
 */
@Slf4j
public class TokenRateLimiter extends AbstractLimiter {

    private final static String REDIS_PREFIX = "rdsTokenLimit";
    private final static String LUA_PATH = "META-INF/lua/token_rate.lua";

    private String tokenScriptSha;

    public TokenRateLimiter(RedissonInvoker redissonInvoker) {
        this.redissonInvoker = redissonInvoker;
        loadTokenScript();
    }

    private void loadTokenScript() {
        tokenScriptSha = redissonInvoker.loadScript(LUA_PATH);
        if (tokenScriptSha == null) {
            log.error("Failed to load lua script: {}", LUA_PATH);
        }
    }

    @Override
    protected String getPrefix() {
        return REDIS_PREFIX;
    }

    /**
     * 令牌桶限流
     *
     * @param key      限流key
     * @param capacity 桶容量（最大令牌数）
     * @param rate     填充速率（令牌/秒）
     * @param permits  本次请求消耗的令牌数
     * @return true=通过限流，false=被限流
     */
    public boolean rdsLimit(String key, int capacity, int rate, int permits) {
        // Lua 脚本参数：
        // KEYS[1]: 令牌桶标识
        // ARGV[1]: 请求的令牌数量
        // ARGV[2]: 桶容量
        // ARGV[3]: 填充速率（令牌/秒）
        // ARGV[4]: 当前时间戳（秒）
        List<Object> keys = new ArrayList<>(1);
        keys.add(generateKey(key));

        long nowSeconds = System.currentTimeMillis() / 1000;
        Object[] args = new Object[4];
        args[0] = String.valueOf(permits);
        args[1] = String.valueOf(capacity);
        args[2] = String.valueOf(rate);
        args[3] = String.valueOf(nowSeconds);

        Object result = redissonInvoker.evalSha(tokenScriptSha, keys, args);
        if (result == null || "0".equals(result.toString())) {
            return false;
        }
        return "1".equals(result.toString());
    }

    /**
     * 令牌桶限流（默认消耗1个令牌）
     *
     * @param key      限流key
     * @param capacity 桶容量
     * @param rate     填充速率（令牌/秒）
     * @return true=通过限流
     */
    public boolean rdsLimit(String key, int capacity, int rate) {
        return rdsLimit(key, capacity, rate, 1);
    }
}
