-- 信号量+窗口 混合限流器 Lua 脚本
-- 同时控制：1. 最大并发数  2. 时间窗口内最大请求数（Acquire时记录）
--
-- 参数说明：
-- KEYS[1]：并发控制 key
-- KEYS[2]：窗口统计 key
-- ARGV[1]：操作类型 (acquire=获取, release=释放)
-- ARGV[2]：最大并发数（信号量容量）
-- ARGV[3]：请求标识（用于释放时校验）
-- ARGV[4]：并发超时时间（毫秒），防止请求异常未释放导致死锁
-- ARGV[5]：当前时间戳（毫秒），由客户端传入
-- ARGV[6]：窗口内最大请求数（0=不限制窗口）
-- ARGV[7]：窗口时间大小（毫秒）

local concurrent_key = KEYS[1]
local window_key = KEYS[2]
local action = ARGV[1]
local max_permits = tonumber(ARGV[2])
local request_id = ARGV[3]
local timeout = tonumber(ARGV[4])
local now = tonumber(ARGV[5])
local window_len = tonumber(ARGV[6])
local window_time = tonumber(ARGV[7])

-- 清理过期的并发请求（防止死锁）
redis.call("zremrangebyscore", concurrent_key, "-inf", now - timeout)

if action == "acquire" then
    -- 检查窗口限制（如果配置了 window_len > 0）
    if window_len > 0 and window_time > 0 then
        -- 清理窗口外的过期记录
        redis.call("zremrangebyscore", window_key, "-inf", now - window_time)
        -- 统计窗口内的请求数
        local window_count = redis.call("zcard", window_key)
        if window_count >= window_len then
            return 0  -- 窗口内请求数已达上限
        end
    end
    
    -- 检查并发限制
    local current = redis.call("zcard", concurrent_key)
    if current >= max_permits then
        return 0  -- 并发已满
    end
    
    -- 获取成功：
    -- 1. 添加到并发 ZSET
    redis.call("zadd", concurrent_key, now, request_id)
    -- 2. 添加到窗口统计 ZSET（如果启用了窗口限制）
    if window_len > 0 and window_time > 0 then
        redis.call("zadd", window_key, now, request_id)
    end
    return 1  -- 获取成功
    
elseif action == "release" then
    -- 释放信号量：从并发 ZSET 中移除
    -- 窗口统计不删除，靠过期清理
    redis.call("zrem", concurrent_key, request_id)
    return 1  -- 释放成功
else
    return -1  -- 未知操作
end
