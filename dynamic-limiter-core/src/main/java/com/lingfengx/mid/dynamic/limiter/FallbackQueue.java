package com.lingfengx.mid.dynamic.limiter;


import cn.hutool.core.util.RandomUtil;
import com.lingfengx.mid.dynamic.limiter.util.JedisInvoker;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 降级的队列 降级的请求将会被放入此队列，等待后续处理
 */
@Slf4j
public class FallbackQueue {

    protected JedisInvoker jedisInvoker;
    protected MethodKeyQueue methodKeyQueue;

    public FallbackQueue(JedisInvoker jedisInvoker, MethodKeyQueue methodKeyQueue) {
        this.jedisInvoker = jedisInvoker;
        this.methodKeyQueue = methodKeyQueue;
    }

    //5s 10s 30s 1m 5m 10m 20m 30m 1h 慢速队列
    public static final long[] TIME_BUCKETS_LONG = {5000, 10000, 30000, 60000, 300000, 600000, 1200000, 1800000, 3600000};
    // 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 快速队列
    public static final long[] TIME_BUCKETS_SHORT = {5000, 10000, 30000, 60000, 120000, 180000, 240000, 300000, 360000, 420000, 480000, 540000, 600000};

    /**
     * 添加降级的任务
     * zset 结构，key为降级的任务的唯一标识，score为任务的执行时间，value为任务的详细信息
     *
     * @param key   降级的任务的唯一标识
     * @param value 任务的详细信息
     */
    public boolean addFastQueue(String key, String value, int retryCount) {
        return add(key, value, retryCount, TIME_BUCKETS_SHORT);
    }

    /**
     * 添加降级的任务
     * zset 结构，key为降级的任务的唯一标识，score为任务的执行时间，value为任务的详细信息
     *
     * @param key   降级的任务的唯一标识
     * @param value 任务的详细信息
     */
    public boolean addSlowQueue(String key, String value, int retryCount) {
        return add(key, value, retryCount, TIME_BUCKETS_LONG);
    }

    private boolean add(String key, String value, int retryCount, long[] timeBuckets) {
        try {
            return jedisInvoker.invoke(jedis -> {
                //预期执行的时间 score
                long nextTime = System.currentTimeMillis() + RandomUtil.randomLong(500, 3000);
                if (retryCount < timeBuckets.length) {
                    nextTime = nextTime + timeBuckets[retryCount];
                } else {
                    nextTime = nextTime + timeBuckets[retryCount % timeBuckets.length];
                }
                // 增加key的待执行任务数(原子性)
                methodKeyQueue.increase(key);
                jedis.zadd(key, nextTime, value);
                return true;
            });
        } catch (Exception e) {
            log.error("add fallback task error, key:{}, value:{}", key, value, e);
            return false;
        }
    }


    /**
     * 获取降级的任务
     * zset 结构，key为降级的任务的唯一标识，score为任务的执行时间，value为任务的详细信息
     * 降级的任务会按照执行时间进行排序
     * 1. score 最小的任务将被获取
     * 2. 如果 score 相同，则按照 value 字母顺序进行排序
     */
    public List<String> get(String key, long time, int limit) {
        return jedisInvoker.invoke(jedis -> jedis.zrangeByScore(key, 0, time, 0, limit));
    }

    /**
     * 判断是否存在
     */
    public Double getScore(String key, String value) {
        return jedisInvoker.invoke(jedis -> jedis.zscore(key, value));
    }

    /**
     * 删除降级的任务
     */
    public boolean delete(String key, String... values) {
        try {
            return jedisInvoker.invoke(jedis -> {
                // 减少key的待执行任务数
                methodKeyQueue.reduce(key);
                jedis.zrem(key, values);
                return true;
            });
        } catch (Exception e) {
            log.error("delete fallback task error, key:{}, values:{}", key, Arrays.toString(values), e);
            return false;
        }
    }


}
