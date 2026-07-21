package com.selflearning.authservice.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selflearning.authservice.auth.config.AuthPermissionCacheProperties;
import com.selflearning.authservice.auth.response.AuthContextResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PermissionContextCacheService {

    private static final Logger log = LoggerFactory.getLogger(PermissionContextCacheService.class);

    private static final String USER_CONTEXT_PREFIX = "auth:user-context:";
    private static final int SCAN_COUNT = 500;
    private static final int DELETE_BATCH_SIZE = 500;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthPermissionCacheProperties properties;

    public PermissionContextCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            AuthPermissionCacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Optional<AuthContextResponse> get(Long userId, String applicationCode) {
        String key = key(userId, applicationCode);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached == null) {
            log.info("Permission context cache miss: userId={}, applicationCode={}", userId, applicationCode);
            return Optional.empty();
        }
        try {
            AuthContextResponse response = objectMapper.readValue(cached, AuthContextResponse.class);
            log.info("Permission context cache hit: userId={}, applicationCode={}", userId, applicationCode);
            return Optional.of(response);
        } catch (JsonProcessingException ex) {
            redisTemplate.delete(key);
            log.warn("Invalid permission context cache removed: userId={}, applicationCode={}", userId, applicationCode, ex);
            return Optional.empty();
        }
    }

    public void put(AuthContextResponse response) {
        String key = key(response.userId(), response.applicationCode());
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(response), properties.permissionCacheTtl());
            log.info(
                    "Permission context cache written: userId={}, applicationCode={}, ttl={}",
                    response.userId(),
                    response.applicationCode(),
                    properties.permissionCacheTtl());
        } catch (JsonProcessingException ex) {
            log.warn(
                    "Failed to serialize permission context cache: userId={}, applicationCode={}",
                    response.userId(),
                    response.applicationCode(),
                    ex);
        }
    }

    public void evictUserApplication(Long userId, String applicationCode) {
        Boolean deleted = redisTemplate.delete(key(userId, applicationCode));
        log.info(
                "Permission context cache evicted: userId={}, applicationCode={}, affectedUsers=1, deleted={}",
                userId,
                applicationCode,
                Boolean.TRUE.equals(deleted) ? 1 : 0);
    }

    public void evictUsersApplication(Collection<Long> userIds, String applicationCode) {
        if (userIds == null || userIds.isEmpty()) {
            log.info("Permission context cache eviction skipped: applicationCode={}, affectedUsers=0", applicationCode);
            return;
        }
        List<String> keys = userIds.stream()
                .distinct()
                .map(userId -> key(userId, applicationCode))
                .toList();
        Long deleted = redisTemplate.delete(keys);
        log.info(
                "Permission context cache evicted by users: applicationCode={}, affectedUsers={}, deleted={}",
                applicationCode,
                keys.size(),
                deleted == null ? 0 : deleted);
    }

    public void evictUserAllApplications(Long userId) {
        String pattern = USER_CONTEXT_PREFIX + userId + ":*";
        long deleted = deleteByScan(pattern);
        log.info("Permission context cache evicted by user: userId={}, deleted={}", userId, deleted);
    }

    private long deleteByScan(String pattern) {
        long deleted = 0;
        List<String> batch = new ArrayList<>(DELETE_BATCH_SIZE);
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(SCAN_COUNT)
                .build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= DELETE_BATCH_SIZE) {
                    deleted += deleteBatch(batch);
                }
            }
        }
        if (!batch.isEmpty()) {
            deleted += deleteBatch(batch);
        }
        return deleted;
    }

    private long deleteBatch(List<String> keys) {
        Long deleted = redisTemplate.delete(keys);
        keys.clear();
        return deleted == null ? 0 : deleted;
    }

    private String key(Long userId, String applicationCode) {
        return USER_CONTEXT_PREFIX + userId + ":" + applicationCode;
    }
}
