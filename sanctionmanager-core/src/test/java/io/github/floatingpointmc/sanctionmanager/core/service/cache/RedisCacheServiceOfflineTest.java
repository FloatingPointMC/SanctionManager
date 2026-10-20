package io.github.floatingpointmc.sanctionmanager.core.service.cache;

import io.github.floatingpointmc.sanctionmanager.api.credential.Credential;
import io.github.floatingpointmc.sanctionmanager.api.credential.CredentialProvider;
import io.github.floatingpointmc.sanctionmanager.api.exceptions.persistence.ConnectionException;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import io.github.floatingpointmc.sanctionmanager.api.model.QueryOptions;
import io.github.floatingpointmc.sanctionmanager.core.config.cache.CacheConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.cache.RedisConfig;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of {@link RedisCacheService} that do not need a Redis server.
 */
public class RedisCacheServiceOfflineTest {
    static final CredentialProvider NO_AUTH = consumer -> {
        try (Credential credential = new Credential("", new char[0])) {
            consumer.accept(credential);
        }
    };

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static EntryPersisted entry() {
        return new EntryPersisted(1, 1, EntryType.of("ban"), UUID.randomUUID(), UUID.randomUUID(), null,
                new Date(), null, null, null, null, false, null, 0);
    }

    @Test
    public void testRedisConfigEnablesCache() {
        RedisConfig redisConfig = new RedisConfig("127.0.0.1", 6379, NO_AUTH);
        CacheConfig config = CacheConfig.redis(redisConfig);
        assertTrue(config.isEnabled());
        assertTrue(config.isUsingRedis());
        assertSame(redisConfig, config.getRedisConfig());
    }

    @Test
    public void testConstructorDoesNotConnect() throws IOException {
        RedisCacheService service = new RedisCacheService(new RedisConfig("127.0.0.1", closedPort(), NO_AUTH));
        service.close();
    }

    @Test
    public void testConstructorValidation() {
        assertThrows(NullPointerException.class, () -> new RedisCacheService(null));
        RedisConfig config = new RedisConfig("127.0.0.1", 6379, NO_AUTH);
        assertThrows(IllegalArgumentException.class, () -> new RedisCacheService(config, ""));
        assertThrows(NullPointerException.class, () -> new RedisCacheService(config, null));
    }

    @Test
    public void testUnreachableServerThrowsConnectionException() throws IOException {
        RedisCacheService service = new RedisCacheService(new RedisConfig("127.0.0.1", closedPort(), NO_AUTH));
        try {
            QueryKey key = new QueryKey.LatestActiveQuery(UUID.randomUUID(), "ban");
            // Failures must never look like misses
            assertThrows(ConnectionException.class, () -> service.getById(1));
            assertThrows(ConnectionException.class, () -> service.getByTypeAndRelId("ban", 1));
            assertThrows(ConnectionException.class, () -> service.getQueryResult(key));
            assertThrows(ConnectionException.class, () -> service.put(entry()));
            assertThrows(ConnectionException.class, () -> service.putQueryResult(key, Collections.emptyList()));
            assertThrows(ConnectionException.class, () -> service.invalidateById(1));
            assertThrows(ConnectionException.class, () -> service.invalidateByTypeAndRelId("ban", 1));
            assertThrows(ConnectionException.class, () -> service.invalidateQueryResult(key));
            assertThrows(ConnectionException.class, service::invalidateAll);
            assertThrows(ConnectionException.class, service::invalidateAllQueryResults);
        } finally {
            service.close();
        }
    }

    @Test
    public void testFailingCredentialProviderThrowsConnectionException() {
        CredentialProvider failing = consumer -> {
            throw new IllegalStateException("credential unavailable");
        };
        RedisCacheService service = new RedisCacheService(new RedisConfig("127.0.0.1", 1, failing));
        try {
            assertThrows(ConnectionException.class, () -> service.getById(1));
        } finally {
            service.close();
        }
    }

    @Test
    public void testCloseIsIdempotent() throws IOException {
        RedisCacheService service = new RedisCacheService(new RedisConfig("127.0.0.1", closedPort(), NO_AUTH));
        assertThrows(ConnectionException.class, () -> service.getById(1));
        service.close();
        service.close();
        // Still fails as a connection failure after close, not with an unrelated exception
        assertThrows(ConnectionException.class, () -> service.getById(1));
        service.close();
    }

    @Test
    public void testCanonicalFormDistinguishesQueries() {
        UUID target = UUID.randomUUID();
        QueryOptions options = QueryOptions.create();
        String targetForm = new QueryKey.TargetQuery(target, options).canonicalForm();
        assertEquals(targetForm, new QueryKey.TargetQuery(target, QueryOptions.create()).canonicalForm());
        assertNotEquals(targetForm, new QueryKey.TargetQuery(target, QueryOptions.create().includeWithdrawn(true))
                .canonicalForm());
        assertNotEquals(targetForm, new QueryKey.TargetQuery(UUID.randomUUID(), options).canonicalForm());
        assertEquals(new QueryKey.LatestActiveQuery(target, "a", "b").canonicalForm(),
                new QueryKey.LatestActiveQuery(target, "b", "a").canonicalForm());
        assertNotEquals(new QueryKey.LatestActiveQuery(target, "a").canonicalForm(),
                new QueryKey.LatestActiveQuery(target, "a", "b").canonicalForm());
        assertNotEquals(new QueryKey.AllQuery(options).canonicalForm(),
                new QueryKey.AllQuery(options, "a").canonicalForm());
    }
}
