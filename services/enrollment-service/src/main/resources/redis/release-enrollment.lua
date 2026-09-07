if redis.call('EXISTS', KEYS[1]) == 0 then
    return -1
end

local student_field = 'student:' .. ARGV[1]
redis.call('HDEL', KEYS[1], student_field)
return redis.call('HINCRBY', KEYS[1], 'remaining', 1)
