package com.lingfengx.mid.dynamic.limiter;

import com.lingfengx.mid.dynamic.limiter.util.JedisInvoker;
import lombok.extern.slf4j.Slf4j;

import java.util.List;


@Slf4j
public class MethodKeyQueue {


    private final static String QUEUE_NAME = "rds:method_key_queue:";


    protected JedisInvoker jedisInvoker;

    public MethodKeyQueue(JedisInvoker jedisInvoker) {
        this.jedisInvoker = jedisInvoker;
    }

    private String getQueueName(String methodKey) {
        return QUEUE_NAME + methodKey;
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
            return jedisInvoker.invoke(jedis -> {
                Long val = jedis.incr(getQueueName(methodKey));
                jedis.zadd(QUEUE_NAME, val, methodKey);
                return true;
            });
        } catch (Exception e) {
            log.error("increase methodKey queue error", e);
            return false;
        }
    }

    public void reduce(String methodKey) {
        jedisInvoker.invoke(jedis -> {
            Long val = jedis.decr(getQueueName(methodKey));
            jedis.zadd(QUEUE_NAME, val, methodKey);
            return true;
        });
    }

    /**
     * 获取降级的任务key列表
     */
    public List<String> get(int limit) {
        return jedisInvoker.invoke(jedis -> jedis.zrevrangeByScore(QUEUE_NAME, "+inf", "-inf", 0, limit));
    }

}
