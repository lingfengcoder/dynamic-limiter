package com.lingfengx.mid.dynamic.limiter;

import java.util.function.Function;

public class BeanUtil {

    static Function<Class, Object> getBeanFunc;

    public BeanUtil(Function<Class, Object> getBeanFunc) {
        this.getBeanFunc = getBeanFunc;
    }

    public static <T> T getBean(Class<T> clazz) {
        return (T) getBeanFunc.apply(clazz);
    }
}
