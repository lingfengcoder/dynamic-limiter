package com.lingfengx.mid.dynamic.limiter;

import com.lingfengx.mid.dynamic.limiter.util.RdsLocker;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

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
    public DynamicLimiterBeanPostProcessor dynamicLimiterBeanPostProcessor() {
        return new DynamicLimiterBeanPostProcessor();
    }

    /**
     * 默认的 Bean 获取函数
     * 用户可以通过自定义 Function<Class, Object> Bean 来覆盖
     * 
     * 关键：通过方法参数注入 ApplicationContext，而不是通过 Aware 接口
     * 这样可以确保在 @Bean 方法执行时 ApplicationContext 已经可用
     */
    @Bean
    @ConditionalOnMissingBean(Function.class)
    public Function<Class, Object> defaultGetBeanFunc(ApplicationContext applicationContext) {
        return applicationContext::getBean;
    }

    /**
     * BeanUtil 工具类
     * 通过方法参数注入 ApplicationContext，确保获取到正确的容器
     */
    @Bean
    public BeanUtil beanUtil(ApplicationContext applicationContext) {
        return new BeanUtil(applicationContext);
    }


    @Bean
    public RdsLimiterAspect rdsLimiterAspect(@Lazy RedissonClient redissonClient) {
        //设置所有降低方法的队列
        MethodKeyQueue methodKeyQueue = new MethodKeyQueue(redissonClient);
        //设置降级队列
        FallbackQueue fallbackQueue = new FallbackQueue(redissonClient, methodKeyQueue);
        //设置分布式锁
        RdsLocker rdsLocker = new RdsLocker(redissonClient);
        //设置动态限流器
        DynamicRedisLimiter dynamicRedisLimiter = new DynamicRedisLimiter(redissonClient);
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
