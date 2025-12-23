package com.lingfengx.mid.dynamic.limiter;

import org.springframework.context.ApplicationContext;

import java.util.function.Function;

/**
 * Bean 获取工具类
 * 支持两种方式：
 * 1. 通过 ApplicationContext 直接获取（推荐）
 * 2. 通过 Function<Class, Object> 获取（兼容旧版本）
 */
public class BeanUtil {

    private static ApplicationContext applicationContext;
    private static Function<Class, Object> getBeanFunc;

    /**
     * 通过 ApplicationContext 初始化（推荐）
     */
    public BeanUtil(ApplicationContext applicationContext) {
        BeanUtil.applicationContext = applicationContext;
    }

    /**
     * 通过 Function 初始化（兼容旧版本）
     */
    public BeanUtil(Function<Class, Object> getBeanFunc) {
        BeanUtil.getBeanFunc = getBeanFunc;
    }

    /**
     * 获取 Spring Bean
     * 优先使用 ApplicationContext，其次使用 Function
     */
    public static <T> T getBean(Class<T> clazz) {
        if (applicationContext != null) {
            return applicationContext.getBean(clazz);
        }
        if (getBeanFunc != null) {
            return (T) getBeanFunc.apply(clazz);
        }
        throw new IllegalStateException("BeanUtil not initialized. Please ensure BeanAutoConfiguration is loaded.");
    }

    /**
     * 检查是否已初始化
     */
    public static boolean isInitialized() {
        return applicationContext != null || getBeanFunc != null;
    }
}
