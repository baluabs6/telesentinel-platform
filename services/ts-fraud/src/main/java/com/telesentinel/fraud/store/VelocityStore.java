package com.telesentinel.fraud.store;

import java.time.Duration;

/** Windowed counters for fraud rules. Redis in production, in-memory in tests. */
public interface VelocityStore {
    /** Adds member to a set that expires after window; returns distinct member count. */
    long addDistinct(String key, String member, Duration window);

    /** Adds delta to a counter that expires after window; returns the new total. */
    long increment(String key, long delta, Duration window);

    /** True only for the first caller within ttl (used for idempotency and alert suppression). */
    boolean markOnce(String key, Duration ttl);

    /** Releases a markOnce key so a retry can run again. */
    void forget(String key);
}
