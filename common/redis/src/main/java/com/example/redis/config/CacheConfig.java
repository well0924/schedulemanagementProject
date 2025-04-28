package com.example.redis.config;

import com.example.redis.config.cachekey.CacheKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig {

    // TTL 상수 정의
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
    private static final Duration SCHEDULE_TTL = Duration.ofMinutes(10);
    private static final Duration CATEGORY_TTL = Duration.ofDays(7);
    private static final Duration USER_TTL = Duration.ofMinutes(10);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        // 날짜 타입을 직렬화/역질렬화 하기 위해
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(Object.class)
                .build();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.NON_FINAL);
        GenericJackson2JsonRedisSerializer redisSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        // 기본적인 캐시 세팅
        RedisCacheConfiguration redisCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisSerializer));

        return RedisCacheManager.RedisCacheManagerBuilder
                .fromConnectionFactory(redisConnectionFactory)
                .cacheDefaults(redisCacheConfiguration)
                .build();
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        // Cache Name마다 ttl을 다르게 하기 위해 커스텀 세팅
        return builder -> builder
                .withCacheConfiguration(CacheKey.SCHEDULE.getKey(), createCacheConfig(SCHEDULE_TTL))
                .withCacheConfiguration(CacheKey.CATEGORY.getKey(), createCacheConfig(CATEGORY_TTL))
                .withCacheConfiguration(CacheKey.USER.getKey(), createCacheConfig(USER_TTL))
                .withCacheConfiguration(CacheKey.REFRESH_TOKEN.getKey(), createCacheConfig(REFRESH_TOKEN_TTL));

    }

    // 공통 RedisCacheConfiguration 생성 메서드
    private RedisCacheConfiguration createCacheConfig(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(createRedisSerializer()));
    }

    // Redis Serializer 생성
    private GenericJackson2JsonRedisSerializer createRedisSerializer() {
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(Object.class)
                .build();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.NON_FINAL);

        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }

}
