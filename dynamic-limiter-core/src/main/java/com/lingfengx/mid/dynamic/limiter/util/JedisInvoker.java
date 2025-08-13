package com.lingfengx.mid.dynamic.limiter.util;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
public class JedisInvoker {
    private Supplier<Jedis> jedisSupplier;
    private int db;

    public JedisInvoker(Supplier<Jedis> jedisSupplier, int db) {
        this.jedisSupplier = jedisSupplier;
        this.db = db;
    }

    protected Jedis getJedis() {
        return jedisSupplier.get();
    }

    public <T> T invoke(Function<Jedis, T> function) {
        return invoke(function, null);
    }

    public <T> T invoke(Function<Jedis, T> function, String errMsg) {
        Integer oldDb = null;
        try (Jedis jedis = getJedis()) {
            oldDb = jedis.getDB();
            jedis.select(db);
            try {
                return function.apply(jedis);
            } catch (Exception e) {
                throw e;
            } finally {
                jedis.select(oldDb);
            }
        } catch (Exception e) {
            log.error("errKey:{} msg:{} {}", errMsg, e.getMessage(), ExceptionUtil.getMessage(e, 10));
            throw new RuntimeException(e);
        } finally {

//            log.info("redis-invoke cost:{} clazz:{} {}", System.currentTimeMillis() - start, function.getClass(), function.toString());
        }
    }

    public <T> T invokePipeline(Function<Pipeline, T> function) {
        long start = System.currentTimeMillis();
        Pipeline pipelined = null;
        Integer oldDb = null;
        try (Jedis jedis = getJedis()) {
            oldDb = jedis.getDB();
            jedis.select(db);
            try {
                pipelined = jedis.pipelined();
                return function.apply(pipelined);
            } catch (Exception e) {
                throw e;
            } finally {
                jedis.select(oldDb);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), ExceptionUtil.getMessage(e, 10));
            throw new RuntimeException(e);
        } finally {
//            log.info("redis-invoke cost:{} clazz:{} {}", System.currentTimeMillis() - start, function.getClass(), function.toString());
            if (pipelined != null) {
                try {
                    pipelined.close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
