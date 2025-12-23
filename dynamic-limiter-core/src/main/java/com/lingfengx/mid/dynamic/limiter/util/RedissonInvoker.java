package com.lingfengx.mid.dynamic.limiter.util;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

/**
 * Redisson 执行器，替代 JedisInvoker
 */
@Slf4j
public class RedissonInvoker {
    
    private final RedissonClient redissonClient;

    public RedissonInvoker(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public RedissonClient getClient() {
        return redissonClient;
    }

    /**
     * 执行 Lua 脚本
     *
     * @param scriptSha 脚本 SHA
     * @param keys      键列表
     * @param args      参数列表
     * @return 执行结果
     */
    public Object evalSha(String scriptSha, List<Object> keys, Object... args) {
        try {
            RScript script = redissonClient.getScript(StringCodec.INSTANCE);
            return script.evalSha(RScript.Mode.READ_WRITE, scriptSha, RScript.ReturnType.VALUE, keys, args);
        } catch (Exception e) {
            log.error("evalSha error: {} {}", e.getMessage(), ExceptionUtil.getMessage(e, 10));
            throw new RuntimeException(e);
        }
    }

    /**
     * 加载 Lua 脚本
     *
     * @param path 脚本路径
     * @return 脚本 SHA
     */
    public String loadScript(String path) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            URL url = classLoader.getResource(path);
            if (url != null) {
                try (InputStream inputStream = url.openStream()) {
                    byte[] buffer = new byte[1024];
                    int read;
                    StringBuilder builder = new StringBuilder();
                    while ((read = inputStream.read(buffer)) > 0) {
                        builder.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                    }
                    RScript script = redissonClient.getScript(StringCodec.INSTANCE);
                    return script.scriptLoad(builder.toString());
                }
            }
        } catch (Exception e) {
            log.error("loadScript error: {} {}", e.getMessage(), ExceptionUtil.getMessage(e, 10));
        }
        return null;
    }
}
