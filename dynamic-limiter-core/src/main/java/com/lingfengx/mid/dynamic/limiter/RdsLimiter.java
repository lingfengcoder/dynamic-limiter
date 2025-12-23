package com.lingfengx.mid.dynamic.limiter;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.lingfengx.mid.dynamic.limiter.algo.Limiter;
import com.lingfengx.mid.dynamic.limiter.algo.LimiterAlgo;
import com.lingfengx.mid.dynamic.limiter.algo.SlidingWindowLimiter;
import com.lingfengx.mid.dynamic.limiter.util.ExceptionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class RdsLimiter {

    public static final String KEY_PREFIX = "#RdsLimitPrefix";
    public static final String KEY_DEFAULT_LIMIT_KEY = "#RdsDefaultLimitKey";
    private static final ExpressionParser parser = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    private DynamicRedisLimiter dynamicRedisLimiter;
    public static boolean isSpringContext = true;

    public RdsLimiter(DynamicRedisLimiter dynamicRedisLimiter, boolean isSpringContext) {
        this.dynamicRedisLimiter = dynamicRedisLimiter;
        RdsLimiter.isSpringContext = isSpringContext;
    }

    public boolean doLimit(Object self, Method method, Object[] args) {
        RdsLimit rdsLimit = method.getAnnotation(RdsLimit.class);
        LimiterAlgo algo = rdsLimit.algo();
        RdsLimitConfig config = getConfig(rdsLimit, self);
        boolean enable = getEnable(rdsLimit, config);
        if (enable) {
            //&& !jump(config)
            //获取静态的key
            String key = getKey(rdsLimit, config);
            //解析key支持spel
            Map<String, Object> params = parseKey(key, method, args);
            //黑白名单检测是否跳过限流
            if (whiteBlackCheck(params, config)) {
                return true;
            }
            //动态获取redis的key
            DLimiter dLimiter = getLimiter(config, params);

            key = getRealKey(config, key, params);
            int boxLen = getBoxLen(rdsLimit, config);
            long boxTime = getBoxTime(rdsLimit, config);
            //优先使用dLimiter
            if (dLimiter != null) {
                if (!dLimiter.isLimited()) {
                    //不需要限流
                    return true;
                }
                key = dLimiter.getKey();
                boxLen = dLimiter.getBoxLen();
                boxTime = dLimiter.getBoxTime();
            }
            boolean success = false;
            String accessKey = null;
            Limiter limiter = null;
            try {
                accessKey = UUID.randomUUID().toString();
                limiter = dynamicRedisLimiter.switchLimiter(algo);
                if (limiter instanceof SlidingWindowLimiter) {
                    success = ((SlidingWindowLimiter) limiter).rdsLimit(key, boxLen, boxTime, accessKey);
                } else {
                    throw new RuntimeException("not support limiter");
                }
            } catch (Exception e) {
                //限流异常的就直接走限流失败
                log.error("[RdsLimit]find dynamicRedisLimiter error occur {} {}", e.getMessage(), ExceptionUtil.getMessage(e, 10));
            }
            //通过限流
            return success;
        }
        return true;
    }

    /**
     * 执行失败回调方法
     *
     * @return
     */
    public Object errorBackCall(Method method, RdsLimitConfig config, Object self, Object[] args) {
        try {
            RdsLimit rdsLimit = method.getAnnotation(RdsLimit.class);
            if (config == null) {
                config = RdsLimiter.getConfig(rdsLimit, self);
                if (config == null) {
                    log.error("[RdsLimit]config is null{} {}", self.getClass().getName(), method.getName());
                    return null;
                }
            }
            String errorBack = config.getErrorBack();
            if (StringUtils.isEmpty(errorBack)) {
                errorBack = rdsLimit.errorBack();
            }
            if (StringUtils.isEmpty(errorBack)) {
                log.error("[rdsLimit]errorBack is null{}", self);
                return null;
            }
            String[] split = errorBack.split("\\.");
            //如果只有一个参数，则执行在当前bean里的方法
            if (split.length == 1) {
                return ReflectUtil.invoke(self, split[0], args);
            } else {
                //todo 调用 非本类方法
            }
        } catch (Exception e) {
            log.error("[errorBackCall]{}{}", e.getMessage(), ExceptionUtil.getMessage(e, 10));
        }
        return null;
    }

    private String getRealKey(RdsLimitConfig config, String key, Map<String, Object> params) {
        String dKey = null;
        if (config != null && config.dynamicKey() != null) {
            dKey = config.dynamicKey().apply(params);
        }
        dKey = StringUtils.hasLength(dKey) ? dKey : params.get(KEY_DEFAULT_LIMIT_KEY).toString();
        key = StringUtils.hasLength(dKey) ? dKey : key;
        return key;
    }


    private DLimiter getLimiter(RdsLimitConfig config, Map<String, Object> params) {
        if (config == null || config.getDynamicLimiter() == null) {
            return null;
        }
        return config.getDynamicLimiter().apply(params);
    }

    /**
     * 黑白名单检测
     *
     * @param params
     * @param config
     * @return true 跳过限流 false 不跳过限流
     */
    private boolean whiteBlackCheck(Map<String, Object> params, RdsLimitConfig config) {
        if (config == null) {
            return false;
        }
        if (config.whiteEnable() && config.whiteListHandler() != null) {
            if (config.whiteListHandler().apply(params)) {
                //白名单跳过限流
                return true;
            }
        }
        //黑名单
        if (config.blackEnable() && config.blackListHandler() != null) {
            if (config.blackListHandler().apply(params)) {
                return false;
            } else {
                //不在黑名单内，跳过限流
                return true;
            }
        }
        //默认不跳过
        return false;
    }

    public static RdsLimitConfig getConfigWithSpringMode(RdsLimit rdsLimit) {
        Class<? extends RdsLimitConfig> clazz = rdsLimit.configBean();
        String dynamicConfig = rdsLimit.dynamicConfig();
        if (clazz == NoneClass.class && !StringUtils.hasLength(dynamicConfig)) {
            throw new RuntimeException("RdsLimit.config must be not null!");
        }
        if (isSpringContext) {
            Class<? extends RdsLimitConfig> aClass = rdsLimit.configBean();
            String simpleName = aClass.getSimpleName();
            //首字母小写 simpleName
            String beanName = simpleName.substring(0, 1).toLowerCase() + simpleName.substring(1);
            RdsLimitConfig bean = SpringUtil.getBean(beanName, RdsLimitConfig.class);
            if (bean != null) {
                return bean;
            }
        }
        throw new RuntimeException("not spring-mode do not support getConfigWithSpringMode");
    }

    public static RdsLimitConfig getConfig(RdsLimit rdsLimit, Object self) {
        Class<? extends RdsLimitConfig> clazz = rdsLimit.configBean();
        String dynamicConfig = rdsLimit.dynamicConfig();
        if (clazz == NoneClass.class && !StringUtils.hasLength(dynamicConfig)) {
            throw new RuntimeException("RdsLimit.config must be not null!");
        }
        if (isSpringContext) {
            Class<? extends RdsLimitConfig> aClass = rdsLimit.configBean();
            String simpleName = aClass.getSimpleName();
            //首字母小写 simpleName
            String beanName = simpleName.substring(0, 1).toLowerCase() + simpleName.substring(1);
            RdsLimitConfig bean = SpringUtil.getBean(beanName, RdsLimitConfig.class);
            if (bean != null) {
                return bean;
            }
        }
        Object invoke = ReflectUtil.invoke(self, dynamicConfig, new Object[0]);
        return invoke != null ? (RdsLimitConfig) invoke : null;
    }

    private boolean getEnable(RdsLimit rdsLimit, RdsLimitConfig config) {
        //通过配置config的方式获取
        // Boolean configData = readOtherConfig(rdsLimit, rdsLimit.DEnable());
        //通过直接指定的方式获取 beanName.key
        return config == null ? rdsLimit.enable() : config.getEnable();
    }

    private String getKey(RdsLimit rdsLimit, RdsLimitConfig config) {
        //通过配置config的方式获取
        // Boolean configData = readOtherConfig(rdsLimit, rdsLimit.DEnable());
        //通过直接指定的方式获取 beanName.key
        return rdsLimit.key();
    }

    private long getBoxTime(RdsLimit rdsLimit, RdsLimitConfig config) {
        //通过配置config的方式获取
        //  Integer configData = readOtherConfig(rdsLimit, rdsLimit.DBoxTime());
        //通过直接指定的方式获取 beanName.key
        return config == null ?  rdsLimit.boxTime() :  config.getBoxTime();
    }


    private int getBoxLen(RdsLimit rdsLimit, RdsLimitConfig config) {
        // Integer configData = readOtherConfig(rdsLimit, rdsLimit.DBoxLen());
        return config == null ? rdsLimit.boxLen() : config.getBoxLen();
    }

    private long getTimeout(RdsLimit rdsLimit, RdsLimitConfig config) {
        //Integer configData = readOtherConfig(rdsLimit, rdsLimit.DTimeout());
        return config == null ? rdsLimit.timeout() : config.getTimeout();
    }


    /**
     * 解析 key -spel
     *
     * @param key    prefix(#tenantId,#projectId)
     * @param method
     * @param args
     * @return prefix+tenantId+projectId
     */
    private Map<String, Object> parseKey(String key, Method method, Object[] args) {
        Map<String, Object> result = new LinkedHashMap<>(args.length);
        //解析出的tenantId和projectId字符串
        int sIdx = key.indexOf("(");
        int eIdx = key.indexOf(")");
        if (sIdx < 0) {
            result.put(KEY_PREFIX, key);
            result.put(KEY_DEFAULT_LIMIT_KEY, key);
            return result;
        }
        String prefix = sIdx > 0 ? key.substring(0, sIdx) : "";
        result.put(KEY_PREFIX, prefix);
        String evalSpel = key.substring(sIdx + 1, eIdx);
        // evalSpel = evalSpel.replace("#", "");
        String[] spels = evalSpel.split(",");
        StringBuilder builder = new StringBuilder(prefix);
        //解析spel
        String[] params = parameterNameDiscoverer.getParameterNames(method);//解析参数名
        if (params == null || params.length == 0) {
            return result;
        }
        EvaluationContext context = new StandardEvaluationContext();//el解析需要的上下文对象
        for (int i = 0; i < params.length; i++) {
            context.setVariable(params[i], args[i]);//所有参数都作为原材料扔进去
        }
        for (String spel : spels) {
            Expression expression = parser.parseExpression(spel);
            String value = expression.getValue(context, String.class);
            result.put(spel.replace("#", ""), value);
            if (StringUtils.hasLength(value)) {
                builder.append(":").append(value);
            }
        }
        //默认的动态key= prefix(#tenantId,#projectId)
        result.put(KEY_DEFAULT_LIMIT_KEY, builder.toString());
        return result;
    }

}
