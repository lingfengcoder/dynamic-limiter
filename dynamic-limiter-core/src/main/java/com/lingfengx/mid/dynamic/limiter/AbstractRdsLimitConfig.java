package com.lingfengx.mid.dynamic.limiter;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Setter
@Getter
public class AbstractRdsLimitConfig implements RdsLimitConfig {

    @Value("true")
    private boolean enable;
    //#滑动窗口的大小
    @Value("10")
    private int boxLen;
    //#滑动窗口的时间
    @Value("10000")
    private long boxTime;

    @Value("true")
    private boolean blackEnable;

    @Value("false")
    private boolean whiteEnable;
    /**
     * 租户白名单
     */
    @Value("")
    private List<String> whiteTenantIds;

    @Value("false")
    private boolean debug;


    @Override
    public boolean getEnable() {
        return enable;
    }


    @Override
    public long getTimeout() {
        return 0;
    }

    @Override
    public String getFallBack() {
        return null;
    }

    @Override
    public String getErrorBack() {
        return null;
    }

    @Override
    public Function<Map<String, Object>, String> dynamicKey() {
        return null;
    }

    @Override
    public Function<Map<String, Object>, DLimiter> getDynamicLimiter() {
        return null;
    }


    //白名单是否生效
    @Override
    public boolean whiteEnable() {
        return whiteEnable;
    }

    //黑名单是否生效
    @Override
    public boolean blackEnable() {
        return blackEnable;
    }


    /**
     * 不限流操作
     *
     * @return
     */
    @Override
    public Function<Map<String, Object>, Boolean> whiteListHandler() {
        return map -> {
            //白名单为*时,不限流
            if (whiteTenantIds.contains("*")) {
                return true;
            }
            Object tenantId = findParam(map, "tenantId");
            if (tenantId != null) {
                return whiteTenantIds.contains(tenantId.toString());
            }
            return false;
        };
    }

    /**
     * 黑名单操作:需要限流的
     *
     * @return
     */
    @Override
    public Function<Map<String, Object>, Boolean> blackListHandler() {
        return null;
    }

    @Override
    public boolean isDebug() {
        return debug;
    }


    protected Object findParam(Map<String, Object> map, String key) {
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
}
