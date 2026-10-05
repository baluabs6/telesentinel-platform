package com.telesentinel.fraud.store;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Test double: ignores expiry, which is fine for single-window assertions. */
public class InMemoryVelocityStore implements VelocityStore {
    private final Map<String, Set<String>> sets = new HashMap<>();
    private final Map<String, Long> counters = new HashMap<>();
    private final Set<String> marks = new HashSet<>();

    public long addDistinct(String key, String member, Duration window) {
        Set<String> s = sets.computeIfAbsent(key, k -> new HashSet<>());
        s.add(member);
        return s.size();
    }
    public long increment(String key, long delta, Duration window) {
        return counters.merge(key, delta, Long::sum);
    }
    public boolean markOnce(String key, Duration ttl) {
        return marks.add(key);
    }
    public void forget(String key) {
        marks.remove(key);
    }
}
