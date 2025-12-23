package com.lingfengx.mid.dynamic.limiter;

import lombok.extern.slf4j.Slf4j;

/**
 * 限流器全局上下文
 * 用于保存全局配置，如命名空间等
 */
@Slf4j
public class LimiterContext {

    /**
     * 命名空间，用于隔离不同应用的降级队列
     * null 或空字符串表示未配置，使用旧格式保持兼容
     */
    private static volatile String namespace = null;

    /**
     * 设置命名空间
     */
    public static void setNamespace(String ns) {
        if (ns != null && !ns.trim().isEmpty()) {
            namespace = ns.trim();
            log.info("[DynamicLimiter] namespace set to: {}", namespace);
        }
    }

    /**
     * 获取命名空间
     */
    public static String getNamespace() {
        return namespace;
    }

    /**
     * 是否启用了命名空间隔离
     */
    public static boolean isNamespaceEnabled() {
        return namespace != null && !namespace.isEmpty();
    }

    /**
     * 生成 Redis Key
     * 启用命名空间时: rds:{namespace}:{key}
     * 未启用时: {key} (保持旧格式兼容)
     */
    public static String buildKey(String key) {
        if (isNamespaceEnabled()) {
            return "rds:" + namespace + ":" + key;
        }
        // 未配置命名空间，使用旧格式保持兼容
        return key;
    }

    /**
     * 生成队列名
     * 启用命名空间时: rds:{namespace}:queue:{queueName}
     * 未启用时: rds:{queueName} (保持旧格式兼容)
     */
    public static String buildQueueKey(String queueName) {
        if (isNamespaceEnabled()) {
            return "rds:" + namespace + ":queue:" + queueName;
        }
        // 未配置命名空间，使用旧格式保持兼容
        return "rds:" + queueName;
    }

    /**
     * 获取旧格式的队列 key（用于读取历史数据）
     * 在启用命名空间后，仍然尝试读取旧格式的数据
     */
    public static String getLegacyQueueKey(String queueName) {
        return "rds:" + queueName;
    }

    /**
     * 获取旧格式的限流 key（用于读取历史数据）
     */
    public static String getLegacyKey(String key) {
        return key;
    }
}
