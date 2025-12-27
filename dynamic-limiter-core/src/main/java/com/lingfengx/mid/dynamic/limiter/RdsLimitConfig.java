package com.lingfengx.mid.dynamic.limiter;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface RdsLimitConfig {
    boolean getEnable();

    //窗口大小
    int getBoxLen();// default -1;

    //窗口时间
    long getBoxTime();// default 5L;

    //令牌桶容量（令牌桶算法专用）
    default int getTokenCapacity() {
        return 100;
    }

    //令牌填充速率（令牌/秒，令牌桶算法专用）
    default int getTokenRate() {
        return 10;
    }

    //每次请求消耗的令牌数（令牌桶算法专用）
    default int getTokenPermits() {
        return 1;
    }

    //最大并发许可数（信号量算法专用）
    default int getSemaphorePermits() {
        return 20;
    }

    //信号量超时时间（毫秒，信号量算法专用）
    default long getSemaphoreTimeout() {
        return 60000;
    }

    //窗口内最大请求数（信号量+窗口算法专用，0=不限制）
    default int getSemaphoreWindowLen() {
        return 0;
    }

    //窗口时间大小（毫秒，信号量+窗口算法专用）
    default long getSemaphoreWindowTime() {
        return 60000;
    }

    //获取限流的超时时间
    long getTimeout();

    //被限流处理
    String getFallBack();

    //限流异常处理
    String getErrorBack();

    Function<Map<String, Object>, String> dynamicKey();

    boolean whiteEnable();

    boolean blackEnable();

    Function<Map<String, Object>, Boolean> whiteListHandler();

    Function<Map<String, Object>, Boolean> blackListHandler();

    boolean isDebug();

    Function<Map<String, Object>, DLimiter> getDynamicLimiter();

    static Object findParam(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) {
            //模糊匹配租户,比如: mediaInfo.tenantId
            for (String k : map.keySet()) {
                if (k.contains("." + key)) {
                    return map.get(k);
                }
            }
        }
        return val;
    }

    //解析blackListPerMinute
    static DLimiter parseDLimiter(int boxTime, List<Map<String, Integer>> blackListPerMinute, Object... param) {
        if (blackListPerMinute == null || blackListPerMinute.isEmpty()) {
            return null;
        }
        for (Map<String, Integer> item : blackListPerMinute) {
            boolean notMatch = false;
            Map.Entry<String, Integer> next = item.entrySet().iterator().next();
            if (next != null) {
                //每分钟的通过次数
                Integer interval = next.getValue();
                String[] split = next.getKey().split("\\|");
                int length = param.length;
                StringBuilder key = new StringBuilder();
                for (int i = 0; i < split.length; i++) {
                    split[i] = split[i].trim();
                    if (length > i) {
                        if ("*".equals(split[i]) || split[i].equals(param[i])) {
                            key.append(split[i]).append("|");
                        } else {
                            notMatch = true;
                            break;
                        }
                    }
                }
                //如果全部匹配成功
                if (!notMatch && !key.toString().isEmpty()) {
                    return new SlidingWindowDLimiter().setKey(key.toString()).setBoxLen(interval).setBoxTime(boxTime).setLimited(true);
                }
            }
        }
        //不需要限流
        return new SlidingWindowDLimiter().setLimited(false);
    }


    static boolean matchRule(List<String> ruleList, Object... param) {
        if (ruleList == null || ruleList.isEmpty()) {
            return false;
        }
        for (String next : ruleList) {
            boolean notMatch = false;

            if (next != null) {
                //每分钟的通过次数
                String[] split = next.split("\\|");
                int length = param.length;
                StringBuilder key = new StringBuilder();
                for (int i = 0; i < split.length; i++) {
                    split[i] = split[i].trim();
                    if (length > i) {
                        if ("*".equals(split[i]) || split[i].equals(param[i])) {
                            key.append(split[i]).append("|");
                        } else {
                            notMatch = true;
                            break;
                        }
                    }
                }
                //如果全部匹配成功
                if (!notMatch && !key.toString().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }
}
