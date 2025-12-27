-- 信号量限流器 Lua 脚本
-- 参数说明：
-- KEYS[1]：信号量的标识
-- ARGV[1]：操作类型 (acquire=获取, release=释放)
-- ARGV[2]：最大并发数（信号量容量）
-- ARGV[3]：请求标识（用于释放时校验）
-- ARGV[4]：超时时间（毫秒），防止请求异常未释放导致死锁
-- ARGV[5]：当前时间戳（毫秒），由客户端传入，避免使用 TIME 命令

local key = KEYS[1]
local action = ARGV[1]
local max_permits = tonumber(ARGV[2])
local request_id = ARGV[3]
local timeout = tonumber(ARGV[4])
local now = tonumber(ARGV[5])

-- 清理过期的请求（防止死锁）
redis.call("zremrangebyscore", key, "-inf", now - timeout)

if action == "acquire" then
    -- 获取信号量
    local current = redis.call("zcard", key)
    if current < max_permits then
        -- 还有空闲槽位，添加请求
        redis.call("zadd", key, now, request_id)
        return 1  -- 获取成功
    else
        return 0  -- 已满，获取失败
    end
elseif action == "release" then
    -- 释放信号量
    redis.call("zrem", key, request_id)
    return 1  -- 释放成功
else
    return -1  -- 未知操作
end
