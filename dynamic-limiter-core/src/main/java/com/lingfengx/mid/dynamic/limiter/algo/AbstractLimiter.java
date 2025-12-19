package com.lingfengx.mid.dynamic.limiter.algo;

import com.lingfengx.mid.dynamic.limiter.LimiterContext;
import com.lingfengx.mid.dynamic.limiter.util.RedissonInvoker;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScoredSortedSet;
import org.redisson.client.codec.StringCodec;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public abstract class AbstractLimiter implements Limiter {
    protected static String scriptSha;
    protected RedissonInvoker redissonInvoker;

    protected abstract String getPrefix();

    /**
     * 生成带命名空间的限流 key
     */
    protected String generateKey(String key) {
        return LimiterContext.buildKey(getPrefix() + ":" + key);
    }

    public void loadScript(String path) {
        scriptSha = redissonInvoker.loadScript(path);
        if (scriptSha == null) {
            log.error("Failed to load lua script: {}", path);
        }
    }

    @Override
    public boolean rdsLimit(List<String> keys, List<String> args) {
        List<Object> keyList = new ArrayList<>(keys);
        Object[] argArray = args.toArray(new String[0]);
        Object isAccess = redissonInvoker.evalSha(scriptSha, keyList, argArray);
        if (isAccess == null || "0".equals(isAccess.toString())) {
            return false;
        }
        return "1".equals(isAccess.toString());
    }

    /**
     * 释放资源
     *
     * @param key
     * @param value
     */
    public void release(String key, String value) {
        RScoredSortedSet<String> set = redissonInvoker.getClient().getScoredSortedSet(generateKey(key), StringCodec.INSTANCE);
        set.remove(value);
    }
}
