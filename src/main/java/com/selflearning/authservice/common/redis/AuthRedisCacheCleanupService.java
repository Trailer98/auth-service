package com.selflearning.authservice.common.redis;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuthRedisCacheCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AuthRedisCacheCleanupService.class);

    private static final String AUTH_CACHE_PATTERN = "auth:*";
    private static final int SCAN_COUNT = 1_000;
    private static final int DELETE_BATCH_SIZE = 500;

    private final StringRedisTemplate redisTemplate;

    public AuthRedisCacheCleanupService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 应用上下文关闭时清理 auth-service 写入 Redis 的临时缓存。
     *
     * <p>使用 SCAN 分批扫描 auth:*，避免 KEYS 在生产 Redis 上造成阻塞。</p>
     */
    @EventListener(ContextClosedEvent.class)
    public void clearAuthCacheOnShutdown() {
        try {
            long deleted = clearAuthCache();
            log.info("Cleared {} auth Redis cache keys on shutdown", deleted);
        } catch (Exception ex) {
            log.warn("Failed to clear auth Redis cache on shutdown", ex);
        }
    }

    public long clearAuthCache() {
        long deleted = 0;
        List<String> batch = new ArrayList<>(DELETE_BATCH_SIZE);
        ScanOptions options = ScanOptions.scanOptions()
                .match(AUTH_CACHE_PATTERN)
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
}
