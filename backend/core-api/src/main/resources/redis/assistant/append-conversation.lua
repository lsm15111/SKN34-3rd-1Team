-- Append one verified question/answer pair, keep only the latest turns and refresh the idle expiry together.
-- ARGV: userJson, assistantJson, maxEntries, ttlMillis
redis.call('RPUSH', KEYS[1], ARGV[1], ARGV[2])
redis.call('LTRIM', KEYS[1], -tonumber(ARGV[3]), -1)
redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[4]))
return redis.call('LLEN', KEYS[1])
