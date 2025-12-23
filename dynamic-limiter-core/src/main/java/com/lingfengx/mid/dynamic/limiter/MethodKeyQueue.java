package com.lingfengx.mid.dynamic.limiter;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


@Slf4j
public class MethodKeyQueue {

    private final static String QUEUE_NAME = "method_key_queue";

    protected RedissonClient redissonClient;

    public MethodKeyQueue(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 获取带命名空间的队列名
     */
    private String getQueueKey() {
        return LimiterContext.buildQueueKey(QUEUE_NAME);
    }

    /**
     * 获取带命名空间的方法计数器 key
     */
    private String getCounterKey(String methodKey) {
        return LimiterContext.buildQueueKey(QUEUE_NAME + ":counter:" + methodKey);
    }

    /**
     * 添加需要执行降级的任务
     * zset 结构，key为降级的任务的唯一标识，score为任务的执行时间，value为任务的详细信息
     * 降级的任务会按照执行时间进行排序
     *
     * @param methodKey 降级任务的唯一标识
     */
    public boolean increase(String methodKey) {
        try {
            RAtomicLong atomicLong = redissonClient.getAtomicLong(getCounterKey(methodKey));
            long val = atomicLong.incrementAndGet();
            RScoredSortedSet<String> set = redissonClient.getScoredSortedSet(getQueueKey(), StringCodec.INSTANCE);
            set.add(val, methodKey);
            return true;
        } catch (Exception e) {
            log.error("increase methodKey queue error", e);
            return false;
        }
    }

    public void reduce(String methodKey) {
        RAtomicLong atomicLong = redissonClient.getAtomicLong(getCounterKey(methodKey));
        long val = atomicLong.decrementAndGet();
        RScoredSortedSet<String> set = redissonClient.getScoredSortedSet(getQueueKey(), StringCodec.INSTANCE);
        set.add(val, methodKey);
    }

    /**
     * 获取降级的任务key列表
     * 支持读取历史数据：如果启用了命名空间，也会尝试读取旧格式的数据
     */
    public List<String> get(int limit) {
        List<String> result = new ArrayList<>();
        
        // 1. 读取新格式的队列
        RScoredSortedSet<String> set = redissonClient.getScoredSortedSet(getQueueKey(), StringCodec.INSTANCE);
        Collection<String> newData = set.valueRangeReversed(0, limit - 1);
        result.addAll(newData);
        
        // 2. 如果启用了命名空间，也读取旧格式的历史数据
        if (LimiterContext.isNamespaceEnabled()) {
            String legacyKey = LimiterContext.getLegacyQueueKey(QUEUE_NAME);
            RScoredSortedSet<String> legacySet = redissonClient.getScoredSortedSet(legacyKey, StringCodec.INSTANCE);
            Collection<String> legacyData = legacySet.valueRangeReversed(0, limit - 1);
            // 添加不重复的历史数据
            for (String item : legacyData) {
                if (!result.contains(item) && result.size() < limit) {
                    result.add(item);
                }
            }
        }
        
        return result;
    }
}
