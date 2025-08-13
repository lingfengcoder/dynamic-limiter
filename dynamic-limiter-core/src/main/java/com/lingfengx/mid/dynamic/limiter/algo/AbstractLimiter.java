package com.lingfengx.mid.dynamic.limiter.algo;

import com.lingfengx.mid.dynamic.limiter.util.ExceptionUtil;
import com.lingfengx.mid.dynamic.limiter.util.JedisInvoker;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisDataException;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
public abstract class AbstractLimiter implements Limiter {
    protected static String scriptLua;
    protected JedisInvoker jedisSupplier;

    protected abstract String getPrefix();

    protected String generateKey(String key) {
        return getPrefix() + ":" + key;
    }


    public void loadScript(String path) {
        jedisSupplier.invoke(jedis -> {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            URL url = classLoader.getResource(path);
            if (url != null) {
                try (InputStream inputStream = url.openStream()) {
                    byte[] buffer = new byte[(int) url.getFile().length()];
                    // 读取文件内容
                    int read = 0;
                    StringBuilder builder = new StringBuilder();
                    while ((read = inputStream.read(buffer)) > 0) {
                        builder.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                    }
                    scriptLua = jedis.scriptLoad(builder.toString());
                } catch (Exception e) {
                    log.error("redis-script-异常 {} {}", e.getMessage(), ExceptionUtil.getMessage(e, 10));
                }
            }
            return null;
        });

    }

    @Override
    public boolean rdsLimit(List<String> keys, List<String> args) {
        Object isAccess = jedisSupplier.invoke(jedis -> jedis.evalsha(scriptLua, keys, args), "redis-script-异常");
        if (isAccess == null || "0".equals(isAccess.toString())) {
            //throw new RuntimeException("redisLimitScript execute error key=" + keys);
            return false;
        }
        return "1".equals(isAccess.toString());
//        return Boolean.parseBoolean(isAccess.toString());
    }

    /**
     * 释放资源
     *
     * @param key
     * @param value
     */
    public void release(String key, String value) {
        jedisSupplier.invoke(jedis -> {
            jedis.zrem(generateKey(key), value);
            return null;
        });
    }
}
