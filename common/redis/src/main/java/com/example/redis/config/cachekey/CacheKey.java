package com.example.redis.config.cachekey;

public enum CacheKey {
    USER("user"),
    REFRESH_TOKEN("refreshToken"),
    BLACKLIST("blackList"),
    CATEGORY("category"),
    SCHEDULE("schedule");

    private final String key;

    CacheKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static final String CATEGORY_KEY = "category";
}

