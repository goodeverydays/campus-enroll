local remaining = redis.call('HGET', KEYS[1], 'remaining')
if not remaining then
    remaining = tonumber(ARGV[1])
    if not remaining or remaining < 0 then
        return -3
    end
    redis.call('HSET', KEYS[1], 'remaining', remaining)
else
    remaining = tonumber(remaining)
end

local student_field = 'student:' .. ARGV[2]
if redis.call('HEXISTS', KEYS[1], student_field) == 1 then
    return -1
end

if remaining <= 0 then
    return -2
end

remaining = redis.call('HINCRBY', KEYS[1], 'remaining', -1)
redis.call('HSET', KEYS[1], student_field, ARGV[3])
return remaining
