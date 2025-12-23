package com.lingfengx.mid.dynamic.limiter;

import cn.hutool.core.util.ArrayUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;


@Slf4j
@Order()
public class BootConfigProcessor implements EnvironmentPostProcessor {
    //启动类
    private static Class<?>[] startClazz = new Class[0];


    public static EnableDynamicLimiter enableDynamicLimiter;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Class<?> mainApplicationClass = application.getMainApplicationClass();
        startClazz = ArrayUtil.append(startClazz, mainApplicationClass);
        for (Class<?> clazz : startClazz) {
            //加载所有注解定义的路径和类
            if (clazz.isAnnotationPresent(EnableDynamicLimiter.class)) {
                enableDynamicLimiter = clazz.getAnnotation(EnableDynamicLimiter.class);
                // 设置命名空间
                String namespace = enableDynamicLimiter.namespace();
                if (namespace != null && !namespace.trim().isEmpty()) {
                    LimiterContext.setNamespace(namespace);
                } else {
                    // 尝试从 spring.application.name 获取
                    String appName = environment.getProperty("spring.application.name");
                    if (appName != null && !appName.trim().isEmpty()) {
                        LimiterContext.setNamespace(appName);
                    }
                }
            }
        }
    }

}
