local signed = redis.call('GETBIT', KEYS[1], ARGV[1])
if signed == 1 then
    return 1
end

redis.call('SETBIT', KEYS[1], ARGV[1], 1)
redis.call('ZINCRBY', KEYS[2], ARGV[3], ARGV[2])
return 0
