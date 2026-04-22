package com.example.redis.config.cachekey;

public enum CacheKey {
    USER("user"),
    REFRESH_TOKEN("refreshToken"),
    BLACKLIST("blackList"),
    CATEGORY("category"),
    SCHEDULE("schedule"),
    CHAT_HISTORY("chat_history"), // 대화 이력용 (2시간)
    CHAT_PATTERN("chat_pattern");  // 분석 패턴용 (7일)

    private final String key;

    CacheKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static final String CATEGORY_KEY = "category";
}

