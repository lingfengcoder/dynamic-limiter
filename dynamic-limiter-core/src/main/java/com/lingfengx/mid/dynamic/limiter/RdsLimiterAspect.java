package com.lingfengx.mid.dynamic.limiter;

import cn.hutool.core.util.ReflectUtil;
import com.lingfengx.mid.dynamic.limiter.util.ExceptionUtil;
import com.lingfengx.mid.dynamic.limiter.util.RefUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

import static com.lingfengx.mid.dynamic.limiter.util.RefUtil.unwrapClass;

@Slf4j
@Aspect
public class RdsLimiterAspect {


    private FallbackQueue fallbackQueue;

    private RdsLimiter rdsLimiter;

    public RdsLimiterAspect(RdsLimiter rdsLimiter, FallbackQueue fallbackQueue) {
        this.rdsLimiter = rdsLimiter;
        this.fallbackQueue = fallbackQueue;
    }


    @Around("@annotation(com.lingfengx.mid.dynamic.limiter.RdsLimit)")
    public Object doAround(ProceedingJoinPoint call) throws Throwable {

        long begin = System.currentTimeMillis();
        try {
            MethodSignature signature = (MethodSignature) call.getSignature();
            Object self = call.getThis();
            Object[] args = call.getArgs();
            Method method = signature.getMethod();
            Class<?>[] parameterTypes = method.getParameterTypes();
            {
                RdsLimit rdsLimit = method.getAnnotation(RdsLimit.class);
                RdsLimitConfig config = RdsLimiter.getConfig(rdsLimit, self);
                if (rdsLimiter.doLimit(self, method, args)) {
                    try {
                        //通过限流
                        return call.proceed();
                    } catch (Throwable e) {
                        log.error("[RdsLimit]find error occur {} {}", e.getMessage(), ExceptionUtil.getMessage(e, 10));
                        //call errorBack
                        return rdsLimiter.errorBackCall(method, config, self, args);
                    } finally {
//                        Limiter limiter = dynamicRedisLimiter.switchLimiter(algo);
//                        if (autoRelease && limiter != null) {
//                            //释放资源
//                            limiter.release(key, accessKey);
//                        }
                    }
                } else {
                    //统计失败的个数
                    //call fallBack
                    return fallbackCall(config, self, method, parameterTypes, args);
                }
            }

        } finally {
            long end = System.currentTimeMillis();
            log.info("[rdsLimit] cost {}", (end - begin));
        }
    }


    private Object fallbackCall(RdsLimitConfig config, Object self, Method method, Class<?>[] parameterTypes, Object[] args) {
        try {
            RdsLimit rdsLimit = method.getAnnotation(RdsLimit.class);
            if (rdsLimit.useFallbackQueue()) {
                Class realClass = unwrapClass(self);
                String key = RefUtil.transformKey(realClass, method, parameterTypes);
                String encode = RefUtil.encode(realClass, method, args);
                //添加任务到fallback队列
                boolean add ;
                if (rdsLimit.enableFastRetryQueue()) {
                    add = fallbackQueue.addFastQueue(key, encode, 0);
                } else {
                    add = fallbackQueue.addSlowQueue(key, encode, 0);
                }
                if (!add) {
                    log.error("fallback queue add error {}", key);
                    return rdsLimiter.errorBackCall(method, config, self, args);
                }
            } else {
                return fallbackInvoke(rdsLimit, config, self, parameterTypes, args);
            }
        } catch (Exception e) {
            log.error("[rdsLimit]{}{}", e.getMessage(), ExceptionUtil.getMessage(e, 10));
        }
        return null;
    }

    private Object fallbackInvoke(RdsLimit rdsLimit, RdsLimitConfig config, Object self, Class<?>[] parameterTypes, Object[] args) {
        String fallBack = config.getFallBack();
        if (StringUtils.isEmpty(fallBack)) {
            fallBack = rdsLimit.fallBack();
        }
        if (StringUtils.isEmpty(fallBack)) {
            log.error("[rdsLimit]fallback is null {}", self);
            return null;
        }
        String[] split = fallBack.split("\\.");
        //如果只有一个参数，则执行在当前bean里的方法
        if (split.length == 1) {
            String methodName = split[0];
            Object realObj = AopProxyUtils.getSingletonTarget(self);
            realObj = realObj == null ? self : realObj;
            Method method = ReflectUtil.getMethod(realObj.getClass(), methodName, parameterTypes);
            return ReflectUtil.invoke(realObj, method, args);
        } else {
            //todo 调用 非本类方法
        }
        return null;
    }


}
