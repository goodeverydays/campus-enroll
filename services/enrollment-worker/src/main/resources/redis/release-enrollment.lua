if redis.call('EXISTS', KEYS[1]) == 0 then
  return -1
end

local removed = redis.call('HDEL', KEYS[1], 'student:' .. ARGV[1])
if removed == 0 then
  return -1
end

return redis.call('HINCRBY', KEYS[1], 'remaining', 1)
