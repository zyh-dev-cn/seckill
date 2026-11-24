-- KEYS[1] = 库存 key, KEYS[2] = 已购用户集合 key
-- ARGV[1] = userId
-- 返回: 1 抢到, 0 库存不足, -1 库存未预热, -2 重复下单

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return -2
end

local stock = redis.call('GET', KEYS[1])
if not stock then
    return -1
end

if tonumber(stock) <= 0 then
    return 0
end

redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
return 1
