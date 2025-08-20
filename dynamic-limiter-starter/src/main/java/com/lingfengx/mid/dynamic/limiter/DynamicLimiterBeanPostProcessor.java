package com.lingfengx.mid.dynamic.limiter;


import cn.hutool.core.collection.ConcurrentHashSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 动态配置注解处理器
 */

@Slf4j
@Order(Integer.MAX_VALUE)
@Configuration
public class DynamicLimiterBeanPostProcessor implements BeanPostProcessor {
    private static ApplicationContext applicationContext;
    //<${placeholder},<bean,[key1,key2]>>
    //bean对应的placeholder <bean,placeholder>
    private static final Map<Object, ConcurrentHashSet<String>> beanPlaceHolder = new ConcurrentHashMap<>();


    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {

        Class<?> clazz = AopProxyUtils.ultimateTargetClass(bean);

        //            找到对应的method
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            RdsLimit baseAnnotation = AnnotationUtils.findAnnotation(method, RdsLimit.class);
            if (baseAnnotation != null) {
                //合并子注解属性: 用于扩展其他自定义注解@AliasFor
                RdsLimit mergedAnnotation = AnnotatedElementUtils.getMergedAnnotation(clazz, RdsLimit.class);
                baseAnnotation = mergedAnnotation != null ? mergedAnnotation : baseAnnotation;
                checkParam(baseAnnotation, method);
            }
        }
        return bean;
    }

    /**
     * 检查参数
     */
    private static void checkParam(RdsLimit rdsLimit, Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length > 0) {
            //如果使用内部自动重试队列
            if (Boolean.TRUE.equals(rdsLimit.useFallbackQueue())) {
                for (Class<?> parameterType : parameterTypes) {
                    //不允许用所有的匿名函数
                    if (parameterType.isAnonymousClass()) {
                        throw new IllegalArgumentException("Anonymous class is not allowed in method " + method.getDeclaringClass().getName() + "." + method.getName());
                    }

                    FunctionalInterface annotation = parameterType.getAnnotation(FunctionalInterface.class);
                    if (annotation != null) {
                        throw new IllegalArgumentException("FunctionalInterface is not allowed in method " + method.getDeclaringClass().getName() + "." + method.getName());
                    }
                    //获取 parameterType 的接口类
                    Class<?>[] interfaces = parameterType.getInterfaces();
                    for (Class<?> interfaceClass : interfaces) {
                        FunctionalInterface ann = interfaceClass.getAnnotation(FunctionalInterface.class);
                        if (ann != null) {
                            throw new IllegalArgumentException("FunctionalInterface is not allowed in method " + method.getDeclaringClass().getName() + "." + method.getName());
                        }
                    }

                    if (parameterType.getName().contains("java.util.function.")) {
                        throw new IllegalArgumentException("FunctionalInterface is not allowed in method " + method.getDeclaringClass().getName() + "." + method.getName());
                    }
                    if (parameterType.equals(Supplier.class)) {
                        throw new IllegalArgumentException("Supplier is not allowed in method " + method.getDeclaringClass().getName() + "." + method.getName());
                    }
                }
            }
        }
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }
}
