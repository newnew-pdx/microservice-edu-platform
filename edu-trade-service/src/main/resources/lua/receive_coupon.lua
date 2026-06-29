-- KEYS[1]：优惠券库存；KEYS[2]：已领取用户集合；ARGV[1]：当前用户 ID。
local stock = redis.call('GET', KEYS[1])
if not stock then
    return 3
end

-- 先判断重复领取，使重复请求在库存为零时仍返回准确提示。
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return 2
end

local stockNumber = tonumber(stock)
if not stockNumber or stockNumber <= 0 then
    return 1
end

redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
return 0
