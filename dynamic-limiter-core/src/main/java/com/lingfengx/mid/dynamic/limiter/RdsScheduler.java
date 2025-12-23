package com.lingfengx.mid.dynamic.limiter;


import cn.hutool.core.thread.ExecutorBuilder;
import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.MD5;
import com.alibaba.fastjson2.JSON;
import com.lingfengx.mid.dynamic.limiter.args.InvokeParam;
import com.lingfengx.mid.dynamic.limiter.args.MethodAndArgs;
import com.lingfengx.mid.dynamic.limiter.util.RdsLocker;
import com.lingfengx.mid.dynamic.limiter.util.RefUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

/**
 * 基于redis的调度器
 *
 * @author lingfengx
 * @version 1.0
 * @date 2025/09/12 14:44
 */
@Slf4j
public class RdsScheduler {
    private FallbackQueue fallbackQueue;
    private MethodKeyQueue methodKeyQueue;
    private ThreadPoolExecutor executor;
    private RdsLocker rdsLocker;
    private RdsLimiter rdsLimiter;
    private int maxPoolSize = 10;
    private ScheduledExecutorService scheduler;

    public RdsScheduler(FallbackQueue fallbackQueue, MethodKeyQueue methodKeyQueue, RdsLocker rdsLocker, RdsLimiter rdsLimiter) {
        this.fallbackQueue = fallbackQueue;
        this.methodKeyQueue = methodKeyQueue;
        this.rdsLocker = rdsLocker;
        this.rdsLimiter = rdsLimiter;
        this.executor = ExecutorBuilder.create().setCorePoolSize(maxPoolSize).setMaxPoolSize(maxPoolSize)
                .setWorkQueue(new ArrayBlockingQueue<>(100))
                .setThreadFactory(new ThreadFactoryBuilder().setNamePrefix("RdsScheduler-").build())
                .setHandler(new ThreadPoolExecutor.DiscardPolicy())
                .build();
        this.scheduler = new ScheduledThreadPoolExecutor(1);

        scheduler.scheduleAtFixedRate(this::schedule, 20, 2, TimeUnit.SECONDS);
    }

    public void schedule() {
        try {
            //从待执行任务数的从多到少，获取方法key，并执行
            List<String> methodKeys = methodKeyQueue.get(maxPoolSize * 10);
            if (ObjectUtil.isEmpty(methodKeys)) {
                return;
            }
            //多线程并发执行，提高效率
            for (int i = 0; i < maxPoolSize * 2; i++) {
                //随机获取几个方法key，如果对应的fallback队列中有元素，则执行fallback，否则放入再次随机获取方法key
                do {
                    if (methodKeys.isEmpty()) {
                        break;
                    }
                    int r = 0;
                    if (methodKeys.size() > 1) {
                        r = RandomUtil.randomInt(0, methodKeys.size() - 1);
                    }
                    String fallbackQueueName = methodKeys.get(r);
                    //从降级队列中获取待执行的任务
                    List<String> methods = fallbackQueue.get(fallbackQueueName, System.currentTimeMillis(), 3);
                    if (ObjectUtil.isNotEmpty(methods)) {
                        for (String taskId : methods) {
                            //带分布式锁的执行方法
                            execute(taskId, fallbackQueueName);
                        }
                        //有队列执行，则跳出循环
                        break;
                    } else {
                        //没有队列执行，则从方法key队列中删除该key,再次随机获取
                        methodKeys.remove(r);
                    }
                } while (methodKeys.isEmpty());

            }
        } catch (Exception e) {
            log.error("schedule error {}", e.getMessage(), e);
        }
    }

    /**
     * 多线程并发执行，提高效率
     *
     * @param task
     * @param fallbackQueueName
     */
    private void execute(String task, String fallbackQueueName) {
        //带分布式锁的执行方法,保证同一时间只有一个线程执行
        executor.execute(() -> {
            MD5 md5 = MD5.create();
            MethodAndArgs maa = RefUtil.decode(task);
            //获取方法
            InvokeParam invokeParam = null;
            try {
                invokeParam = RefUtil.trans(maa);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            RdsLimit rdsLimit = invokeParam.getMethod().getAnnotation(RdsLimit.class);
            final InvokeParam finalInvokeParam = invokeParam;
            boolean debug = RdsLimiter.getConfigWithSpringMode(rdsLimit).isDebug();
            RdsLocker.setDebugMode(debug);

            //获取被限流次数
            int limitRetryCount = maa.getLimitedRetryCount();
            //获取执行次数
            int funcExecuteCount = maa.getFuncExecuteCount();

            rdsLocker.tryHandleWithLock(md5.digestHex(maa.generateSignature()), md5.digestHex(fallbackQueueName), fallbackQueueName, t -> {
                try {
                    //虽然拿到锁了，但是要判断：1.任务是否存在 2.任务是否没到执行时间(被重入)
                    Double score = fallbackQueue.getScore(fallbackQueueName, task);
                    if (score == null || score > System.currentTimeMillis()) {
                        //任务不存在或未到执行时间
                        if (debug) {
                            log.info("task {} not exist or not expired", task);
                        }
                        return;
                    }
                    //获取对象
                    Object bean = RefUtil.getRealObjFromSpringBean(finalInvokeParam.getClazz());
                    //尝试限流
                    if (this.rdsLimiter.doLimit(bean, finalInvokeParam.getMethod(), finalInvokeParam.getArgs())) {
                        try {
                            maa.setFuncExecuteCount(funcExecuteCount + 1);
                            //执行方法
                            RefUtil.invoke(finalInvokeParam);
                            //限流执行成功，删除被限流的任务
                            fallbackQueue.delete(fallbackQueueName, task);
                        } catch (Exception e) {
                            //获取限流注解
                            this.rdsLimiter.errorBackCall(finalInvokeParam.getMethod(), null, bean, finalInvokeParam.getArgs());
                            //执行失败加入到重试队列
                            addToRetryQueue(maa, fallbackQueueName, rdsLimit, task);
                        } finally {
                            //如果重试了3次后还是失败的，则放弃任务
                            if (maa.getFuncExecuteCount() > rdsLimit.errorRetryCount()) {
                                //限流执行成功，删除被限流的任务
                                fallbackQueue.delete(fallbackQueueName, task);
                                log.error(" retry count exceed {}, discard it task {}", rdsLimit.errorRetryCount(), task);
                                this.rdsLimiter.errorBackCall(finalInvokeParam.getMethod(), null, bean, finalInvokeParam.getArgs());
                            }
                        }
                    } else {
                        //限流执行失败，加入到重试队列
                        addToRetryQueue(maa, fallbackQueueName, rdsLimit, task);
                    }
                } catch (Exception e) {
                    log.error("execute task error {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
                //不自动删除，但是自动释放锁,拿不到锁，则等待1秒,防止过快
            }, false, true, 500);
        });
    }

    /**
     * 加入到重试队列
     *
     * @param maa
     * @param fallbackQueueName
     * @param rdsLimit
     * @param task
     */
    private void addToRetryQueue(MethodAndArgs maa, String fallbackQueueName, RdsLimit rdsLimit, String task) {
        //获取执行窗口失败，则放入降级队列
        maa.setLimitedRetryCount(maa.getLimitedRetryCount() + 1);
        //获取限流注解
        boolean addOK;
        //获取限流配置
        if (rdsLimit.enableFastRetryQueue()) {
            addOK = fallbackQueue.addFastQueue(fallbackQueueName, JSON.toJSONString(maa), maa.getLimitedRetryCount());
        } else {
            addOK = fallbackQueue.addSlowQueue(fallbackQueueName, JSON.toJSONString(maa), maa.getLimitedRetryCount());
        }
        if (addOK) {
            //删除以前的记录
            fallbackQueue.delete(fallbackQueueName, task);
        }
    }
}
