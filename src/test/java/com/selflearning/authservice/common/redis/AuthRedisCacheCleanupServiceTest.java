package com.selflearning.authservice.common.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

class AuthRedisCacheCleanupServiceTest {

    private final StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
    private final AuthRedisCacheCleanupService cleanupService = new AuthRedisCacheCleanupService(redisTemplate);

    @Test
    void clearAuthCacheScansAuthKeysAndDeletesMatchedKeys() {
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(new ListCursor(List.of(
                "auth:refresh:token-1",
                "auth:blacklist:jti-1",
                "auth:permission:user-1")));
        when(redisTemplate.delete(any(Collection.class))).thenReturn(3L);

        long deleted = cleanupService.clearAuthCache();

        ArgumentCaptor<ScanOptions> scanOptionsCaptor = ArgumentCaptor.forClass(ScanOptions.class);
        org.mockito.Mockito.verify(redisTemplate).scan(scanOptionsCaptor.capture());
        assertThat(scanOptionsCaptor.getValue().getPattern()).isEqualTo("auth:*");
        assertThat(scanOptionsCaptor.getValue().getCount()).isEqualTo(1_000);
        assertThat(deleted).isEqualTo(3L);
    }

    private static final class ListCursor implements Cursor<String> {

        private final Iterator<String> iterator;
        private long position;
        private boolean closed;

        private ListCursor(List<String> values) {
            this.iterator = values.iterator();
        }

        @Override
        public CursorId getId() {
            return CursorId.of(0);
        }

        @SuppressWarnings("deprecation")
        @Override
        public long getCursorId() {
            return 0;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public long getPosition() {
            return position;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public String next() {
            position++;
            return iterator.next();
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
