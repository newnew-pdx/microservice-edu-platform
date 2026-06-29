-- MySQL 写入失败时移除本次领取标记，并且只在移除成功时恢复库存，保证补偿幂等。
if redis.call('SREM', KEYS[2], ARGV[1]) == 1 then
    redis.call('INCR', KEYS[1])
    return 1
end
return 0
