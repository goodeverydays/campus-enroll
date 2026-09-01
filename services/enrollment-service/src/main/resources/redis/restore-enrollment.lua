local remaining = redis.call('HGET', KEYS[1], 'remaining')
if not remaining then
    return -1
end

local student_field = 'student:' .. ARGV[1]
if redis.call('HEXISTS', KEYS[1], student_field) == 1 then
    return tonumber(remaining)
end

remaining = redis.call('HINCRBY', KEYS[1], 'remaining', -1)
redis.call('HSET', KEYS[1], student_field, ARGV[2])
return remaining
