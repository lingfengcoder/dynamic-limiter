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
                    return new DLimiter().setKey(key.toString()).setBoxLen(interval).setBoxTime(boxTime).setLimited(true);
                }
            }
        }
        //不需要限流
        return new DLimiter().setLimited(false);
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
