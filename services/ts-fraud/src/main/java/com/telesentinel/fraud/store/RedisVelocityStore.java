package com.telesentinel.fraud.store;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Counters are updated by Lua scripts so the update and the expiry happen atomically. The script also
 * repairs a key that somehow has no TTL, so a counter can never live (and fire alerts) forever.
 */
@Component
public class RedisVelocityStore implements VelocityStore {

    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
            local v = redis.call('INCRBY', KEYS[1], ARGV[1])
            if redis.call('TTL', KEYS[1]) < 0 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
            return v
            """, Long.class);

    private static final DefaultRedisScript<Long> ADD_DISTINCT = new DefaultRedisScript<>("""
            redis.call('SADD', KEYS[1], ARGV[1])
            if redis.call('TTL', KEYS[1]) < 0 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
            return redis.call('SCARD', KEYS[1])
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisVelocityStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long addDistinct(String key, String member, Duration window) {
        Long size = redis.execute(ADD_DISTINCT, List.of(key), member, String.valueOf(window.toSeconds()));
        return size == null ? 0 : size;
    }

    @Override
    public long increment(String key, long delta, Duration window) {
        Long total = redis.execute(INCREMENT, List.of(key), String.valueOf(delta), String.valueOf(window.toSeconds()));
        return total == null ? 0 : total;
    }

    @Override
    public boolean markOnce(String key, Duration ttl) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", ttl));
    }

    @Override
    public void forget(String key) {
        redis.delete(key);
    }
}
