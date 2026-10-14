package com.tushar27x.insightshub.ratelimit;

import com.tushar27x.insightshub.ratelimit.RateLimitProperties.Policy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Counts hits in memory and never resets a window: enough to test "the (n+1)th call is refused". */
public class InMemoryRateLimiter implements RateLimiter {

    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    public final List<String> keys = new CopyOnWriteArrayList<>();

    @Override
    public Decision tryAcquire(String key, Policy policy) {
        keys.add(key);
        int count = counts.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
        return new Decision(count <= policy.requests(), policy.requests(),
                Math.max(0, policy.requests() - count), policy.per());
    }

    public void reset() {
        counts.clear();
        keys.clear();
    }
}
