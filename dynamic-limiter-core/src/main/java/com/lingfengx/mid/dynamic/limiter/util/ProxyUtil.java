package com.lingfengx.mid.dynamic.limiter.util;

import org.springframework.aop.framework.Advised;

public class ProxyUtil {


    public static <T> T unwrapProxy(Object jedisSupplier) throws Exception {
        //如果jedisSupplier是代理类，则获取真实对象
        Object target = jedisSupplier;
        Object proxy = ((Advised) jedisSupplier).getTargetSource().getTarget();
        if (proxy != null) {
            if (proxy instanceof Advised) {
                proxy = ((Advised) proxy).getTargetSource().getTarget();
                if (proxy != null) {
                    target = proxy;
                }
            } else {
                target = proxy;
            }
        }
        return (T) target;
    }
}
