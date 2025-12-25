package com.lingfengx.mid.dynamic.limiter;

import com.lingfengx.mid.dynamic.limiter.algo.LimiterAlgo;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 滑动窗口算法动态限流器
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
public class SlidingWindowDLimiter implements DLimiter {

    /**
     * 限流 key
     */
    private String key;

    /**
     * 是否需要限流
     */
    private boolean limited = true;

    /**
     * 窗口内最大请求数
     */
    private int boxLen;

    /**
     * 时间窗口大小（毫秒）
     */
    private long boxTime;

    @Override
    public LimiterAlgo getAlgo() {
        return LimiterAlgo.SlidingWindow;
    }
}
