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
import redis.clients.jedis.Jedis;

import javax.annotation.PostConstruct;
import java.util.function.Supplier;

import static com.lingfengx.mid.dynamic.limiter.util.ProxyUtil.unwrapProxy;

@Slf4j
//@Configuration
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
        return new RdsLimiterAspect(rdsLimiter, fallbackQueue);
    }




}
