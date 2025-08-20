package com.lingfengx.mid.dynamic.limiter.util;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.fastjson2.JSON;
import com.lingfengx.mid.dynamic.limiter.BeanUtil;
import com.lingfengx.mid.dynamic.limiter.args.Args;
import com.lingfengx.mid.dynamic.limiter.args.InvokeParam;
import com.lingfengx.mid.dynamic.limiter.args.MethodAndArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
public class RefUtil {


    /**
     * 类+方法+参数类型列表 转换为zset的key 字符串
     */
    public static String transformKey(Class classes, Method method, Class<?>[] parameterTypes) {
        String key = "";
        if (classes != null) {
            key += classes.getName();
        }
        if (method != null) {
            key += "#";
            key += method.getName();
        }
        if (parameterTypes != null && parameterTypes.length > 0) {
            key += "(";
            for (int i = 0; i < parameterTypes.length; i++) {
                key += parameterTypes[i].getName();
                if (i < parameterTypes.length - 1) {
                    key += ",";
                }
            }
            key += ")";
        }
        return key;
    }

    /**
     * 把执行的类+方法+参数 转换为zset的value 字符串
     */
    public static String encode(Class clazz, Method method, Object[] args) {
        MethodAndArgs methodAndArgs = new MethodAndArgs().setClazz(clazz.getName())
                .setMethodName(method.getName()).setMethodStatic(Modifier.isStatic(method.getModifiers()));
        TreeMap<Integer, Args> argsMap = new TreeMap<>();

        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < args.length; i++) {
            Object val = args[i];
            Args args1 = new Args().setClazz(parameterTypes[i].getName());
            if (val == null) {
                args1.setValue(null);
            } else if (val instanceof String) {
                args1.setValue(val.toString());
            } else {
                args1.setValue(JSON.toJSONString(val));
            }
            argsMap.put(i, args1);
        }
        methodAndArgs.setArgs(argsMap);
        methodAndArgs.setLimitedRetryCount(0);
        methodAndArgs.setRealExecuteTime(System.currentTimeMillis());
        return JSON.toJSONString(methodAndArgs);
    }

    public static String encode(MethodAndArgs methodAndArgs) {
        return JSON.toJSONString(methodAndArgs);
    }

    /**
     * 把redis中的value 字符串 转换为执行的类+方法+参数
     */
    public static MethodAndArgs decode(String value) {
        return JSON.to(MethodAndArgs.class, value);
    }

    public static Object getRealObjFromSpringBean(Class clazz) {
        try {
            String simpleName = clazz.getSimpleName();
            String beanName = simpleName.substring(0, 1).toLowerCase() + simpleName.substring(1);
            Object bean = BeanUtil.getBean(clazz);
//            Object bean = SpringUtil.getBean(beanName, clazz);
            Object realObj = AopProxyUtils.getSingletonTarget(bean);
            realObj = realObj == null ? bean : realObj;
            return realObj;
        } catch (Exception e) {
            log.error("getRealObjFromSpringBean error", e);
        }
        return null;
    }

    /**
     * 获取spring代理类中的真实类
     *
     * @param methodAndArgs
     * @return
     * @throws ClassNotFoundException
     */
    public static Class<?> unwrapClass(Object proxy) {
        // 检查是否是代理对象
        if (AopUtils.isAopProxy(proxy)) {
            // 获取目标对象
            return AopUtils.getTargetClass(proxy);
        } else {
            return proxy.getClass();
        }
    }

    public static InvokeParam trans(MethodAndArgs methodAndArgs) throws ClassNotFoundException {
        Map<Integer, Args> argsMap = methodAndArgs.getArgs();

        int size = argsMap.values().size();
        Object[] params = new Object[size];
        for (int i = 0; i < size; i++) {
            Args arg = argsMap.get(i);
            params[i] = JSON.to(Class.forName(arg.getClazz()), arg.getValue());
        }
        Class<?> clazz = Class.forName(methodAndArgs.getClazz());
        String methodName = methodAndArgs.getMethodName();
        boolean methodStatic = methodAndArgs.isMethodStatic();
        Class<?>[] parameterTypes = getParameterTypes(argsMap);
        Method method = ReflectUtil.getMethod(clazz, methodName, parameterTypes);
        return new InvokeParam().setClazz(clazz).setMethod(method).setArgs(params);
    }

    public static Object invoke(MethodAndArgs methodAndArgs) throws ClassNotFoundException {
        Map<Integer, Args> argsMap = methodAndArgs.getArgs();

        int size = argsMap.values().size();
        Object[] params = new Object[size];
        for (int i = 0; i < size; i++) {
            Args arg = argsMap.get(i);
            params[i] = JSON.to(Class.forName(arg.getClazz()), arg.getValue());
        }
        Class<?> clazz = Class.forName(methodAndArgs.getClazz());
        String methodName = methodAndArgs.getMethodName();
        boolean methodStatic = methodAndArgs.isMethodStatic();
        Class<?>[] parameterTypes = getParameterTypes(argsMap);
        return invoke(clazz, methodName, parameterTypes, methodStatic, params);
    }

    //InvokeParam
    public static Object invoke(InvokeParam invokeParam) throws Exception {
        Class<?> clazz = invokeParam.getClazz();
        Method method = invokeParam.getMethod();
        Class<?>[] parameterTypes = method.getParameterTypes();
        boolean methodStatic = Modifier.isStatic(method.getModifiers());
        Object[] params = invokeParam.getArgs();
        if (methodStatic) {
            //静态方法
            //获取静态方法
            try {
                return ReflectUtil.invokeStatic(method, params);
            } catch (Exception e) {
                log.error("ReflectUtil invokeStatic error", e);
                throw e;
            }
        } else {
            try {
                Object realObj = getRealObjFromSpringBean(clazz);
                if (realObj != null) {
                    method = ReflectUtil.getMethod(realObj.getClass(), method.getName(), parameterTypes);
                    return ReflectUtil.invoke(realObj, method, params);
                } else {
                    log.error("getRealObjFromSpringBean error, realObj is null clazz:{}", clazz);
                }
            } catch (Exception e) {
                log.error("SpringUtil invoke error", e);
                throw e;
            }
            //new 实例方法
            try {
                Object bean = ReflectUtil.newInstance(clazz);
                ReflectUtil.invoke(bean, method, params);
            } catch (Exception e) {
                log.error("ReflectUtil invoke error", e);
                throw e;
            }
        }
        return null;
    }

    /**
     * todo 缓存非spring bean的实例对象 用于执行非spring bean的实例方法
     *
     * @param clazz
     * @param methodName
     * @param parameterTypes
     * @param methodStatic
     * @param params
     * @return
     */
    public static Object invoke(Class<?> clazz, String methodName, Class<?>[] parameterTypes, boolean methodStatic, Object... params) {
        Method method = ReflectUtil.getMethod(clazz, methodName, parameterTypes);
        if (methodStatic) {
            //静态方法
            //获取静态方法
            try {
                return ReflectUtil.invokeStatic(method, params);
            } catch (Exception e) {
                log.error("ReflectUtil invokeStatic error", e);
            }
        } else {
            try {
                Object bean = SpringUtil.getBean(clazz);
                Object realObj = AopProxyUtils.getSingletonTarget(bean);
                realObj = realObj == null ? bean : realObj;
                method = ReflectUtil.getMethod(realObj.getClass(), methodName, parameterTypes);
                return ReflectUtil.invoke(bean, method, params);
            } catch (Exception e) {
                log.error("SpringUtil invoke error", e);
            }
            //new 实例方法
            try {
                Object bean = ReflectUtil.newInstance(clazz);
                ReflectUtil.invoke(bean, method, params);
            } catch (Exception e) {
                log.error("ReflectUtil invoke error", e);
            }
        }
        return null;
    }


    public static Class<?>[] getParameterTypes(Map<Integer, Args> args) throws ClassNotFoundException {
        Class<?>[] parameterTypes = new Class<?>[args.size()];
        for (Map.Entry<Integer, Args> entry : args.entrySet()) {
            parameterTypes[entry.getKey()] = Class.forName(entry.getValue().getClazz());
        }
        return parameterTypes;
    }

}
