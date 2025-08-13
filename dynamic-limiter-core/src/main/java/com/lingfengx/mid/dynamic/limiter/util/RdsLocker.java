package com.lingfengx.mid.dynamic.limiter.util;

import cn.hutool.core.thread.ThreadUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RLock;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class RdsLocker {


    private final static String lockKeyPrefix = "rds:limiter:lock:";
    public static volatile boolean isDebug = false;
    //增强版的threadlocal
    private static final InheritableThreadLocal<Boolean> debugThreadLocal = new InheritableThreadLocal<>();
    protected Redisson redisson;
    protected JedisInvoker jedisInvoker;

    public RdsLocker(Redisson redisson, JedisInvoker jedisInvoker) {
        this.redisson = redisson;
        this.jedisInvoker = jedisInvoker;
    }


    public boolean lock(String taskId, String taskType) {
        RLock lock = redisson.getLock(lockKeyPrefix + taskType + ":" + taskId);
        try {
            //线程不等待，且启用看门狗
            return lock.tryLock(0, -1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void unlock(String taskId, String taskType) {
        RLock lock = redisson.getLock(lockKeyPrefix + taskType + ":" + taskId);
        lock.unlock();
    }

    public static void setDebugMode(boolean isDebug) {
        debugThreadLocal.set(isDebug);
    }

    public void tryHandleWithLock(String taskId, String taskType, String queueName, Consumer<String> taskHandler, boolean autoDel, boolean autoUnlock, long timeout) {
        boolean isDebug = Boolean.TRUE.equals(debugThreadLocal.get());
        if (isDebug) {
            log.info("开始lock，taskId:{} queueName:{}", taskId, queueName);
        }
        boolean getLock = this.lock(taskId, taskType);
        if (getLock) {
            try {
                if (isDebug) {
                    log.info("得到锁，taskId:{} queueName:{}", taskId, queueName);
                }
                taskHandler.accept(taskId);
            } catch (Exception e) {
                log.error("tryHandleWithLock 处理任务失败，任务id:{} queueName:{} err:{}", taskId, queueName, e.getMessage(), e);
            } finally {
                if (autoDel) {
                    //删除任务记录
                    if (isDebug) {
                        log.info("任务完成自动删除任务记录，任务id: {} queueName: {}", taskId, queueName);
                    }
                    this.delTaskFromRedis(queueName, taskId);
                }
                //释放任务锁
                if (autoUnlock) {
                    if (isDebug) {
                        log.info("自动释放锁，任务id:{} taskType: {} queueName: {}", taskId, taskType, queueName);
                    }
                    this.unlock(taskId, taskType);
                }
            }
        } else {
            if (isDebug) {
                log.info("锁定失败，任务id:{} 已被锁定", taskId);
            }
            if (timeout > 0) {
                ThreadUtil.safeSleep(timeout);
            }
        }
        debugThreadLocal.remove();
    }


    protected void delTaskFromRedis(String queueName, String data) {
        jedisInvoker.invoke(jedis -> jedis.zrem(queueName, data));
    }


    protected boolean existTaskInRedis(String queueName, String data) {
        return jedisInvoker.invoke(jedis -> jedis.zscore(queueName, data) != null);
    }

}
