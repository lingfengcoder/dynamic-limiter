package com.lingfengx.mid.dynamic.limiter;

import com.lingfengx.mid.dynamic.limiter.algo.LimiterAlgo;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 令牌桶算法动态限流器
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
public class TokenRateDLimiter implements DLimiter {

    /**
     * 限流 key
     */
    private String key;

    /**
     * 是否需要限流
     */
    private boolean limited = true;

    /**
     * 令牌桶容量
     */
    private int tokenCapacity = 100;

    /**
     * 令牌填充速率（令牌/秒）
     */
    private int tokenRate = 10;

    /**
     * 每次请求消耗的令牌数
     */
    private int tokenPermits = 1;

    @Override
    public LimiterAlgo getAlgo() {
        return LimiterAlgo.TokenRate;
    }
}
