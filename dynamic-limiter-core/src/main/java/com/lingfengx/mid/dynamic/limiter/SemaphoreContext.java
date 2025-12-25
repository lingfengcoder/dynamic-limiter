package com.lingfengx.mid.dynamic.limiter;

import com.lingfengx.mid.dynamic.limiter.algo.LimiterAlgo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 信号量上下文，用于存储获取信号量后的信息
 * 便于执行完成后释放
 */
@Getter
@Accessors(chain = true)
@AllArgsConstructor
public class SemaphoreContext {
    /**
     * 限流 key
     */
    private final String key;

    /**
     * 请求唯一标识（用于释放时匹配）
     */
    private final String requestId;

    /**
     * 使用的算法类型（用于释放时选择正确的 Limiter）
     */
    private final LimiterAlgo algo;

    /**
     * 兼容旧的构造方法（默认使用 Semaphore 算法）
     */
    public SemaphoreContext(String key, String requestId) {
        this.key = key;
        this.requestId = requestId;
        this.algo = LimiterAlgo.Semaphore;
    }
}
