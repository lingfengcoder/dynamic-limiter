-- 令牌桶限流器 Lua 脚本
-- 参数说明：
-- KEYS[1]：令牌桶的标识
-- ARGV[1]：请求的令牌数量
-- ARGV[2]：令牌桶的容量
-- ARGV[3]：令牌桶的填充速率（单位：令牌/秒）
-- ARGV[4]：当前时间戳（秒），由客户端传入，避免使用 TIME 命令

-- 参数转换为数字
local permits = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local rate = tonumber(ARGV[3])
local current_time = tonumber(ARGV[4])

-- 检查令牌桶是否存在
if redis.call("exists", KEYS[1]) == 0 then
    -- 初始化令牌桶：满桶状态，时间戳设为当前时间
    redis.call("hset", KEYS[1], "tokens", capacity)
    redis.call("hset", KEYS[1], "last_update", current_time)
end

-- 获取上次令牌生成时间戳和当前令牌数量
local last_update = tonumber(redis.call("hget", KEYS[1], "last_update"))
local tokens = tonumber(redis.call("hget", KEYS[1], "tokens"))

-- 计算时间差并填充令牌
local elapsed = current_time - last_update
if elapsed > 0 then
    -- 计算令牌的增加数量
    local delta = rate * elapsed
    -- 生成新的令牌，不超过容量
    tokens = math.min(capacity, tokens + delta)
    -- 更新上次令牌生成时间戳
    redis.call("hset", KEYS[1], "last_update", current_time)
end

-- 检查是否有足够的令牌
if tokens >= permits then
    -- 消耗令牌
    redis.call("hset", KEYS[1], "tokens", tokens - permits)
    return 1  -- 通过限流
else
    -- 令牌不足，保存当前令牌数（可能已增加）
    redis.call("hset", KEYS[1], "tokens", tokens)
    return 0  -- 被限流
end