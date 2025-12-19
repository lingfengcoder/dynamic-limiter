package com.lingfengx.mid.dynamic.limiter;


import cn.hutool.core.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.util.*;

/**
 * 降级的队列 降级的请求将会被放入此队列，等待后续处理
 */
@Slf4j
public class FallbackQueue {

    private static final String FALLBACK_QUEUE_PREFIX = "fallback:";

    protected RedissonClient redissonClient;
    protected MethodKeyQueue methodKeyQueue;

    public FallbackQueue(RedissonClient redissonClient, MethodKeyQueue methodKeyQueue) {
        this.redissonClient = redissonClient;
        this.methodKeyQueue = methodKeyQueue;
    }

    /**
     * 获取带命名空间的队列 key
     */
    private String buildFallbackKey(String key) {
        return LimiterContext.buildQueueKey(FALLBACK_QUEUE_PREFIX + key);
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
            String fallbackKey = buildFallbackKey(key);
            //预期执行的时间 score
            long nextTime = System.currentTimeMillis() + RandomUtil.randomLong(500, 3000);
            if (retryCount < timeBuckets.length) {
                nextTime = nextTime + timeBuckets[retryCount];
            } else {
                nextTime = nextTime + timeBuckets[retryCount % timeBuckets.length];
            }
            // 增加key的待执行任务数(原子性)
            methodKeyQueue.increase(fallbackKey);
            RScoredSortedSet<String> set = redissonClient.getScoredSortedSet(fallbackKey, StringCodec.INSTANCE);
            set.add(nextTime, value);
            return true;
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
     *
     * 注意：传入的 fallbackKey 是完整的 Redis key（从 MethodKeyQueue 获取），不需要再添加前缀
     */
    public List<String> get(String fallbackKey, long time, int limit) {
        // fallbackKey 已经是完整的 Redis key，直接使用
        RScoredSortedSet<String> set = redissonClient.getScoredSortedSet(fallbackKey, StringCodec.INSTANCE);
        Collection<String> result = set.valueRange(0, true, time, true, 0, limit);
        return new ArrayList<>(result);
    }

    /**
     * 判断是否存在
     * 注意：传入的 fallbackKey 是完整的 Redis key（从 MethodKeyQueue 获取）
     */
    public Double getScore(String fallbackKey, String value) {
        RScoredSortedSet<String> set = redissonClient.getScoredSortedSet(fallbackKey, StringCodec.INSTANCE);
        return set.getScore(value);
    }

    /**
     * 删除降级的任务
     * 注意：传入的 fallbackKey 是完整的 Redis key（从 MethodKeyQueue 获取）
     */
    public boolean delete(String fallbackKey, String... values) {
        try {
            // 减少key的待执行任务数
            methodKeyQueue.reduce(fallbackKey);
            RScoredSortedSet<String> set = redissonClient.getScoredSortedSet(fallbackKey, StringCodec.INSTANCE);
            set.removeAll(Arrays.asList(values));
            return true;
        } catch (Exception e) {
            log.error("delete fallback task error, key:{}, values:{}", fallbackKey, Arrays.toString(values), e);
            return false;
        }
    }


}
