package com.lingfengx.mid.dynamic.limiter;

import com.lingfengx.mid.dynamic.limiter.util.JedisInvoker;
import com.lingfengx.mid.dynamic.limiter.util.ProxyUtil;
import com.lingfengx.mid.dynamic.limiter.util.RdsLocker;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import redis.clients.jedis.Jedis;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.lingfengx.mid.dynamic.limiter.util.ProxyUtil.unwrapProxy;

@Slf4j
@Configuration
public class BeanAutoConfiguration {
    @Lazy
    @Autowired
    private RedissonClient redissonClient;

    @PostConstruct
    public void init() {
        // 使用 Redisson
        RBucket<String> bucket = redissonClient.getBucket("test-redisson");
        bucket.set("Hello Redisson!");
        String s = bucket.get();
        log.info("test Redisson value: {}", s);
    }

    @Bean
    public BeanUtil beanUtil(Function<Class, Object> getBeanFunc) {
        return new BeanUtil(getBeanFunc);
    }


    @Bean
    public RdsLimiterAspect rdsLimiterAspect(@Lazy Supplier<Jedis> jedisSupplier, @Lazy Redisson redisson) throws Exception {
        int redisDb = 0;
        EnableDynamicLimiter enableDynamicLimiter = BootConfigProcessor.enableDynamicLimiter;
        if (enableDynamicLimiter != null) {
            redisDb = enableDynamicLimiter.redisDb();
        }
        Supplier<Jedis> unwrapJedisSupplier = ProxyUtil.unwrapProxy(jedisSupplier);
        //设置jedis执行器
        JedisInvoker jedisInvoker = new JedisInvoker(unwrapJedisSupplier, redisDb);
        //设置所有降低方法的队列
        MethodKeyQueue methodKeyQueue = new MethodKeyQueue(jedisInvoker);
        //设置降级队列
        FallbackQueue fallbackQueue = new FallbackQueue(jedisInvoker, methodKeyQueue);
        //设置分布式锁
        RdsLocker rdsLocker = new RdsLocker(redisson, jedisInvoker);
        //设置动态限流器
        DynamicRedisLimiter dynamicRedisLimiter = new DynamicRedisLimiter(jedisInvoker);
        RdsLimiter rdsLimiter = new RdsLimiter(dynamicRedisLimiter, true);
        //设置降级调度器

        RdsScheduler rdsScheduler = new RdsScheduler(fallbackQueue, methodKeyQueue, rdsLocker, rdsLimiter);
        //设置切面
        printLOGO();
        return new RdsLimiterAspect(rdsLimiter, fallbackQueue);
    }


    private void printLOGO() {
        //从resources目录下读取banner.txt文件内容
        try (InputStream inputStream = new ClassPathResource("/banner.txt").getInputStream()) {
            StringBuilder builder = new StringBuilder();
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                char[] buffer = new char[1024];
                int length;
                while ((length = bufferedReader.read(buffer)) != -1) {
                    builder.append(buffer, 0, length);
                }
            }
            log.info(builder.toString());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }


}
