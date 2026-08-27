package com.cc4c.shared;

import java.time.Duration;

public interface BusinessCacheStore {
    String get(String key);

    void set(String key, String value, Duration ttl);

    boolean setIfAbsent(String key, String value, Duration ttl);

    long increment(String key);

    void delete(String key);

    boolean compareAndDelete(String key, String expectedValue);

    long deleteByPrefix(String prefix);
}
