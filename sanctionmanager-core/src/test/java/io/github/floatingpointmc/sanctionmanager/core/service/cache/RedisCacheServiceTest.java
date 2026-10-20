package io.github.floatingpointmc.sanctionmanager.core.service.cache;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.floatingpointmc.sanctionmanager.api.credential.Credential;
import io.github.floatingpointmc.sanctionmanager.api.credential.CredentialProvider;
import io.github.floatingpointmc.sanctionmanager.api.exceptions.PersistenceException;
import io.github.floatingpointmc.sanctionmanager.api.exceptions.persistence.ConnectionException;
import io.github.floatingpointmc.sanctionmanager.api.exceptions.persistence.OperationException;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryWrapper;
import io.github.floatingpointmc.sanctionmanager.api.model.QueryOptions;
import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.config.CoreConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.cache.CacheConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.cache.RedisConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.BinaryFileConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.StorageConfig;
import io.github.floatingpointmc.sanctionmanager.core.management.TypeManager;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;
import io.github.floatingpointmc.sanctionmanager.core.service.CacheService;
import io.github.floatingpointmc.sanctionmanager.core.service.EntryServiceCore;
import io.lettuce.core.KillArgs;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of {@link RedisCacheService} against a real Redis server.
 * <p>
 * The server is read from the environment variable {@code SANCTIONMANAGER_TEST_REDIS} ({@code host:port}) and
 * defaults to {@code 127.0.0.1:6379}. If it is not reachable, every test is skipped. Each test uses a random key
 * prefix and deletes only keys under it. Authentication tests additionally require
 * {@code SANCTIONMANAGER_TEST_REDIS_AUTH} ({@code host:port:password}) pointing to a server with
 * {@code requirepass} set.
 * </p>
 */
public class RedisCacheServiceTest {
    private static final CredentialProvider NO_AUTH = RedisCacheServiceOfflineTest.NO_AUTH;
    private static final EntryType BAN = EntryType.of("ban");
    private static final EntryType NOTE = EntryType.of("note", true);

    private static String host;
    private static int port;
    private static boolean available;
    private static RedisClient rawClient;
    private static StatefulRedisConnection<String, String> rawConnection;

    private String prefix;
    private RedisCacheService cache;
    private RedisCommands<String, String> raw;

    @BeforeAll
    public static void connectRaw() {
        String address = System.getenv("SANCTIONMANAGER_TEST_REDIS");
        if (address == null || address.isEmpty()) {
            address = "127.0.0.1:6379";
        }
        int colon = address.lastIndexOf(':');
        host = address.substring(0, colon);
        port = Integer.parseInt(address.substring(colon + 1));
        rawClient = RedisClient.create(RedisURI.Builder.redis(host, port).withTimeout(Duration.ofSeconds(5)).build());
        try {
            rawConnection = rawClient.connect();
            available = "PONG".equals(rawConnection.sync().ping());
        } catch (RuntimeException e) {
            available = false;
        }
    }

    @AfterAll
    public static void closeRaw() {
        if (rawConnection != null) {
            rawConnection.close();
        }
        rawClient.shutdown();
    }

    @BeforeEach
    public void setUp() {
        Assumptions.assumeTrue(available, "Redis server not reachable at " + host + ":" + port);
        prefix = "sanctionmanager-test:" + UUID.randomUUID() + ":";
        cache = new RedisCacheService(new RedisConfig(host, port, NO_AUTH), prefix);
        raw = rawConnection.sync();
    }

    @AfterEach
    public void tearDown() {
        if (cache != null) {
            cache.close();
        }
        if (available) {
            List<String> keys = raw.keys(prefix.replace("*", "\\*") + "*");
            if (!keys.isEmpty()) {
                raw.del(keys.toArray(new String[0]));
            }
        }
    }

    private static EntryPersisted entry(long id, long relId, EntryType type) {
        return entry(id, relId, type, UUID.randomUUID());
    }

    private static EntryPersisted entry(long id, long relId, EntryType type, UUID target) {
        return new EntryPersisted(id, relId, type, target, UUID.randomUUID(), null, new Date(1700000000000L),
                "reason " + id, type.withExtraData() ? new JsonObject() : null, null, null, false, null, 0);
    }

    private static void assertEntryEquals(EntryPersisted expected, EntryPersisted actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getRelId(), actual.getRelId());
        assertEquals(expected.getType(), actual.getType());
        assertEquals(expected.getType().withExtraData(), actual.getType().withExtraData());
        assertEquals(expected.getTarget(), actual.getTarget());
        assertEquals(expected.getExecutor(), actual.getExecutor());
        assertEquals(expected.getExpiryTime(), actual.getExpiryTime());
        assertEquals(expected.getExecutingTime(), actual.getExecutingTime());
        assertEquals(expected.getReason(), actual.getReason());
        assertEquals(expected.getDataInternal(), actual.getDataInternal());
        assertEquals(expected.getWithdrawnBy(), actual.getWithdrawnBy());
        assertEquals(expected.getWithdrawalReason(), actual.getWithdrawalReason());
        assertEquals(expected.isOverridden(), actual.isOverridden());
        assertEquals(expected.getOverriddenBy(), actual.getOverriddenBy());
        assertEquals(expected.getVersion(), actual.getVersion());
    }

    private static List<Long> ids(List<EntryPersisted> entries) {
        List<Long> ids = new ArrayList<>();
        for (EntryPersisted entry : entries) {
            ids.add(entry.getId());
        }
        return ids;
    }

    private String queryResultKey() {
        List<String> keys = raw.keys(prefix + "query:r:*");
        assertEquals(1, keys.size());
        return keys.get(0);
    }

    // Lookups, round trips and misses

    @Test
    public void testMisses() {
        assertFalse(cache.getById(1).isPresent());
        assertFalse(cache.getByTypeAndRelId("ban", 1).isPresent());
        assertFalse(cache.getQueryResult(new QueryKey.LatestActiveQuery(UUID.randomUUID(), "ban")).isPresent());
    }

    @Test
    public void testPutAndGetById() {
        EntryPersisted entry = entry(1, 10, BAN);
        cache.put(entry);
        Optional<EntryPersisted> cached = cache.getById(1);
        assertTrue(cached.isPresent());
        assertEntryEquals(entry, cached.get());
        assertNotSame(entry, cached.get());
    }

    @Test
    public void testPutAndGetByTypeAndRelId() {
        EntryPersisted entry = entry(1, 10, BAN);
        cache.put(entry);
        Optional<EntryPersisted> cached = cache.getByTypeAndRelId("ban", 10);
        assertTrue(cached.isPresent());
        assertEntryEquals(entry, cached.get());
        assertFalse(cache.getByTypeAndRelId("ban", 11).isPresent());
        assertFalse(cache.getByTypeAndRelId("note", 10).isPresent());
    }

    @Test
    public void testSameRelIdDifferentTypes() {
        cache.put(entry(1, 5, BAN));
        cache.put(entry(2, 5, NOTE));
        assertEquals(1, cache.getByTypeAndRelId("ban", 5).get().getId());
        assertEquals(2, cache.getByTypeAndRelId("note", 5).get().getId());
        cache.invalidateByTypeAndRelId("ban", 5);
        assertFalse(cache.getById(1).isPresent());
        assertTrue(cache.getById(2).isPresent());
    }

    @Test
    public void testNegativeAndExtremeIds() {
        EntryPersisted entry = entry(Long.MIN_VALUE, Long.MAX_VALUE, BAN);
        cache.put(entry);
        assertEntryEquals(entry, cache.getById(Long.MIN_VALUE).get());
        assertEntryEquals(entry, cache.getByTypeAndRelId("ban", Long.MAX_VALUE).get());
        QueryKey key = new QueryKey.LatestActiveQuery(UUID.randomUUID(), "ban");
        cache.putQueryResult(key, Collections.singletonList(entry));
        assertEquals(Collections.singletonList(Long.MIN_VALUE), ids(cache.getQueryResult(key).get()));
    }

    // Serialization

    @Test
    public void testSerializationOfAllFields() {
        JsonObject data = new JsonObject();
        data.addProperty("text", "Ünïcødé \"quoted\" \\ \n line ☃");
        data.addProperty("number", 12.5);
        data.addProperty("big", Long.MAX_VALUE);
        data.add("nothing", null);
        JsonArray array = new JsonArray();
        array.add(1);
        array.add("two");
        array.add(new JsonObject());
        data.add("array", array);
        JsonObject nested = new JsonObject();
        nested.addProperty("flag", true);
        data.add("nested", nested);
        EntryPersisted full = new EntryPersisted(42, 7, NOTE, UUID.randomUUID(), UUID.randomUUID(),
                new Date(1800000000123L), new Date(1700000000456L), "reason ✓", data, UUID.randomUUID(),
                "withdrawn", true, 99L, 3);
        cache.put(full);
        EntryPersisted cached = cache.getById(42).get();
        assertEntryEquals(full, cached);
        assertTrue(cached.getType().withExtraData());
        assertNotSame(data, cached.getDataInternal());
    }

    @Test
    public void testSerializationOfNullableFields() {
        EntryPersisted minimal = new EntryPersisted(1, 1, BAN, UUID.randomUUID(), UUID.randomUUID(), null,
                new Date(0), null, null, null, null, false, null, 0);
        cache.put(minimal);
        EntryPersisted cached = cache.getById(1).get();
        assertEntryEquals(minimal, cached);
        assertNull(cached.getExpiryTime());
        assertNull(cached.getReason());
        assertNull(cached.getDataInternal());
        assertNull(cached.getWithdrawnBy());
        assertNull(cached.getOverriddenBy());
    }

    @Test
    public void testDataWithNullValuedMembersIsPreserved() {
        JsonObject data = new JsonObject();
        data.add("explicitNull", null);
        EntryPersisted entry = new EntryPersisted(1, 1, NOTE, UUID.randomUUID(), UUID.randomUUID(), null,
                new Date(0), null, data, null, null, false, null, 0);
        cache.put(entry);
        assertTrue(cache.getById(1).get().getDataInternal().has("explicitNull"));
    }

    // Query results

    @Test
    public void testQueryResultRoundTripPreservesOrder() {
        EntryPersisted a = entry(3, 1, BAN);
        EntryPersisted b = entry(1, 2, BAN);
        EntryPersisted c = entry(2, 3, BAN);
        cache.put(a);
        cache.put(b);
        cache.put(c);
        QueryKey key = new QueryKey.TargetQuery(UUID.randomUUID(), QueryOptions.create());
        cache.putQueryResult(key, Arrays.asList(a, b, c));
        List<EntryPersisted> result = cache.getQueryResult(key).get();
        assertEquals(Arrays.asList(3L, 1L, 2L), ids(result));
        assertEntryEquals(b, result.get(1));
    }

    @Test
    public void testEmptyQueryResultIsDistinctFromMiss() {
        QueryKey key = new QueryKey.AllQuery(QueryOptions.create(), "ban");
        assertFalse(cache.getQueryResult(key).isPresent());
        cache.putQueryResult(key, Collections.emptyList());
        Optional<List<EntryPersisted>> result = cache.getQueryResult(key);
        assertTrue(result.isPresent());
        assertTrue(result.get().isEmpty());
    }

    @Test
    public void testQueryResultWithEvictedEntryIsMiss() {
        EntryPersisted a = entry(1, 1, BAN);
        EntryPersisted b = entry(2, 2, BAN);
        cache.put(a);
        cache.put(b);
        QueryKey key = new QueryKey.LatestActiveQuery(UUID.randomUUID(), "ban");
        cache.putQueryResult(key, Arrays.asList(a, b));
        cache.invalidateById(2);
        assertFalse(cache.getQueryResult(key).isPresent());
        cache.put(b);
        assertTrue(cache.getQueryResult(key).isPresent());
    }

    @Test
    public void testQueryResultReflectsReplacedEntry() {
        EntryPersisted entry = entry(1, 1, BAN);
        cache.put(entry);
        QueryKey key = new QueryKey.LatestActiveQuery(UUID.randomUUID(), "ban");
        cache.putQueryResult(key, Collections.singletonList(entry));
        entry.setWithdrawnBy(UUID.randomUUID());
        cache.put(entry);
        assertEquals(entry.getWithdrawnBy(), cache.getQueryResult(key).get().get(0).getWithdrawnBy());
    }

    @Test
    public void testDistinctQueryKeysDoNotCollide() {
        UUID target = UUID.randomUUID();
        EntryPersisted a = entry(1, 1, BAN, target);
        EntryPersisted b = entry(2, 2, BAN, target);
        cache.put(a);
        cache.put(b);
        QueryKey active = new QueryKey.TargetQuery(target, QueryOptions.create());
        QueryKey withdrawn = new QueryKey.TargetQuery(target, QueryOptions.create().includeWithdrawn(true));
        QueryKey latest = new QueryKey.LatestActiveQuery(target);
        QueryKey all = new QueryKey.AllQuery(QueryOptions.create());
        cache.putQueryResult(active, Collections.singletonList(a));
        cache.putQueryResult(withdrawn, Arrays.asList(a, b));
        cache.putQueryResult(latest, Collections.emptyList());
        assertEquals(Collections.singletonList(1L), ids(cache.getQueryResult(active).get()));
        assertEquals(Arrays.asList(1L, 2L), ids(cache.getQueryResult(withdrawn).get()));
        assertTrue(cache.getQueryResult(latest).get().isEmpty());
        assertFalse(cache.getQueryResult(all).isPresent());
        // An equal key built separately hits the same result
        assertTrue(cache.getQueryResult(new QueryKey.TargetQuery(target, QueryOptions.create())).isPresent());
    }

    @Test
    public void testPutQueryResultOverwrites() {
        EntryPersisted a = entry(1, 1, BAN);
        cache.put(a);
        QueryKey key = new QueryKey.LatestActiveQuery(UUID.randomUUID(), "ban");
        cache.putQueryResult(key, Collections.singletonList(a));
        cache.putQueryResult(key, Collections.emptyList());
        assertTrue(cache.getQueryResult(key).get().isEmpty());
    }

    // Invalidation

    @Test
    public void testInvalidateByIdRemovesBothIndexes() {
        cache.put(entry(1, 10, BAN));
        cache.invalidateById(1);
        assertFalse(cache.getById(1).isPresent());
        assertFalse(cache.getByTypeAndRelId("ban", 10).isPresent());
        assertEquals(0, raw.exists(prefix + "entry:rel:ban:10"));
        cache.invalidateById(1); // No-op
    }

    @Test
    public void testInvalidateByTypeAndRelIdRemovesBothIndexes() {
        cache.put(entry(1, 10, BAN));
        cache.invalidateByTypeAndRelId("ban", 10);
        assertFalse(cache.getByTypeAndRelId("ban", 10).isPresent());
        assertFalse(cache.getById(1).isPresent());
        cache.invalidateByTypeAndRelId("ban", 10); // No-op
    }

    @Test
    public void testInvalidateQueryResult() {
        cache.put(entry(1, 1, BAN));
        QueryKey key1 = new QueryKey.LatestActiveQuery(UUID.randomUUID(), "ban");
        QueryKey key2 = new QueryKey.LatestActiveQuery(UUID.randomUUID(), "ban");
        cache.putQueryResult(key1, Collections.emptyList());
        cache.putQueryResult(key2, Collections.emptyList());
        cache.invalidateQueryResult(key1);
        assertFalse(cache.getQueryResult(key1).isPresent());
        assertTrue(cache.getQueryResult(key2).isPresent());
        assertTrue(cache.getById(1).isPresent());
    }

    @Test
    public void testInvalidateAllQueryResultsKeepsEntries() {
        EntryPersisted entry = entry(1, 1, BAN);
        cache.put(entry);
        QueryKey key1 = new QueryKey.LatestActiveQuery(UUID.randomUUID(), "ban");
        QueryKey key2 = new QueryKey.AllQuery(QueryOptions.create());
        cache.putQueryResult(key1, Collections.singletonList(entry));
        cache.putQueryResult(key2, Collections.emptyList());
        cache.invalidateAllQueryResults();
        assertFalse(cache.getQueryResult(key1).isPresent());
        assertFalse(cache.getQueryResult(key2).isPresent());
        assertTrue(cache.getById(1).isPresent());
        assertTrue(cache.getByTypeAndRelId("ban", 1).isPresent());
        // Old result keys are deleted, not just unreachable
        assertTrue(raw.keys(prefix + "query:r:*").isEmpty());
        // New results can be cached again
        cache.putQueryResult(key1, Collections.singletonList(entry));
        assertTrue(cache.getQueryResult(key1).isPresent());
    }

    @Test
    public void testInvalidateAll() {
        EntryPersisted entry = entry(1, 1, BAN);
        cache.put(entry);
        cache.put(entry(2, 2, NOTE));
        QueryKey key = new QueryKey.LatestActiveQuery(UUID.randomUUID(), "ban");
        cache.putQueryResult(key, Collections.singletonList(entry));
        cache.invalidateAll();
        assertFalse(cache.getById(1).isPresent());
        assertFalse(cache.getById(2).isPresent());
        assertFalse(cache.getByTypeAndRelId("ban", 1).isPresent());
        assertFalse(cache.getQueryResult(key).isPresent());
        assertTrue(raw.keys(prefix + "*").isEmpty());
        cache.put(entry);
        assertTrue(cache.getById(1).isPresent());
    }

    @Test
    public void testInvalidateAllWithManyKeys() {
        for (int i = 0; i < 1200; i++) {
            cache.put(entry(i, i, BAN));
        }
        cache.invalidateAll();
        assertTrue(raw.keys(prefix + "*").isEmpty());
    }

    // Namespace isolation

    @Test
    public void testNamespaceIsolation() {
        String otherPrefix = prefix + "other:";
        String globPrefix = prefix + "g*[?]:";
        String foreignKey = prefix.substring(0, prefix.length() - 1) + "-foreign";
        raw.set(foreignKey, "keep");
        RedisCacheService other = new RedisCacheService(new RedisConfig(host, port, NO_AUTH), otherPrefix);
        RedisCacheService glob = new RedisCacheService(new RedisConfig(host, port, NO_AUTH), globPrefix);
        try {
            EntryPersisted mine = entry(1, 1, BAN);
            EntryPersisted theirs = entry(1, 1, BAN);
            QueryKey key = new QueryKey.AllQuery(QueryOptions.create());
            cache.put(mine);
            other.put(theirs);
            glob.put(theirs);
            other.putQueryResult(key, Collections.singletonList(theirs));
            assertEquals(mine.getTarget(), cache.getById(1).get().getTarget());
            assertEquals(theirs.getTarget(), other.getById(1).get().getTarget());
            assertFalse(cache.getQueryResult(key).isPresent());

            // A prefix containing glob characters must not match other namespaces
            glob.invalidateAll();
            assertTrue(cache.getById(1).isPresent());
            assertTrue(other.getById(1).isPresent());

            other.invalidateAll();
            assertTrue(cache.getById(1).isPresent());
            cache.invalidateAllQueryResults();
            cache.invalidateAll();
            assertEquals("keep", raw.get(foreignKey));
        } finally {
            other.close();
            glob.close();
            raw.del(foreignKey);
        }
    }

    // Replacement and stale indexes

    @Test
    public void testReplacementRemovesStaleRelIndex() {
        cache.put(entry(1, 10, BAN));
        cache.put(entry(1, 20, NOTE));
        assertFalse(cache.getByTypeAndRelId("ban", 10).isPresent());
        assertEquals(0, raw.exists(prefix + "entry:rel:ban:10"));
        assertEquals(1, cache.getByTypeAndRelId("note", 20).get().getId());
        assertEquals("note", cache.getById(1).get().getType().getIdentifier());
    }

    @Test
    public void testReplacementKeepsRelIndexClaimedByAnotherEntry() {
        cache.put(entry(1, 10, BAN));
        cache.put(entry(2, 10, BAN)); // Entry 2 claims (ban, 10)
        cache.put(entry(1, 11, BAN)); // Replacing entry 1 must not remove entry 2's index
        assertEquals(2, cache.getByTypeAndRelId("ban", 10).get().getId());
        assertEquals(1, cache.getByTypeAndRelId("ban", 11).get().getId());
        // Invalidating entry 1 must not remove entry 2's index either
        cache.invalidateById(1);
        assertEquals(2, cache.getByTypeAndRelId("ban", 10).get().getId());
    }

    @Test
    public void testDanglingRelIndexIsMiss() {
        cache.put(entry(1, 10, BAN));
        cache.put(entry(2, 10, BAN)); // (ban, 10) now points to 2
        cache.put(entry(2, 11, BAN)); // Entry 2 moves away and removes its index
        assertFalse(cache.getByTypeAndRelId("ban", 10).isPresent());
        // Entry 1 is still cached by ID
        assertTrue(cache.getById(1).isPresent());
        // Invalidating (ban, 10) must not remove entry 1, whose index was overwritten
        cache.invalidateByTypeAndRelId("ban", 10);
        assertTrue(cache.getById(1).isPresent());
    }

    // Failures and malformed data

    @Test
    public void testMalformedEntryJsonThrows() {
        raw.hset(prefix + "entry:id:1", "d", "{not json");
        assertThrows(OperationException.class, () -> cache.getById(1));
        raw.hset(prefix + "entry:id:1", "d", "[]");
        assertThrows(OperationException.class, () -> cache.getById(1));
        raw.hset(prefix + "entry:id:1", "d", "{} trailing");
        assertThrows(OperationException.class, () -> cache.getById(1));
    }

    @Test
    public void testEntrySchemaViolationsThrow() {
        cache.put(entry(1, 1, BAN));
        String valid = raw.hget(prefix + "entry:id:1", "d");
        String[][] corruptions = {
                {"\"v\":1", "\"v\":2"},
                {"\"v\":1,", ""},
                {"\"id\":1", "\"id\":\"1\""},
                {"\"id\":1", "\"id\":1.5"},
                {"\"id\":1", "\"id\":2"},
                {"\"type\":\"ban\"", "\"type\":\"Bad Type\""},
                {"\"type\":\"ban\"", "\"type\":null"},
                {"\"overridden\":false", "\"overridden\":0"},
                {"\"version\":0", "\"version\":4294967296"},
                {"\"data\":null", "\"data\":[]"},
                {"\"expiryTime\":null", "\"expiryTime\":\"soon\""},
        };
        for (String[] corruption : corruptions) {
            assertTrue(valid.contains(corruption[0]), corruption[0]);
            raw.hset(prefix + "entry:id:1", "d", valid.replace(corruption[0], corruption[1]));
            assertThrows(OperationException.class, () -> cache.getById(1), corruption[1]);
        }
        raw.hset(prefix + "entry:id:1", "d", valid);
        String uuid = cache.getById(1).get().getTarget().toString();
        for (String invalidUuid : new String[]{"x", uuid.toUpperCase(Locale.ROOT), uuid.replace("-", "")}) {
            raw.hset(prefix + "entry:id:1", "d", valid.replace(uuid, invalidUuid));
            assertThrows(OperationException.class, () -> cache.getById(1), invalidUuid);
        }
    }

    @Test
    public void testWrongRedisTypeThrowsAndIsOverwrittenByPut() {
        raw.set(prefix + "entry:id:1", "plain string");
        assertThrows(OperationException.class, () -> cache.getById(1));
        EntryPersisted entry = entry(1, 1, BAN);
        cache.put(entry);
        assertEntryEquals(entry, cache.getById(1).get());
    }

    @Test
    public void testMalformedRelIndexThrows() {
        raw.set(prefix + "entry:rel:ban:1", "not-a-number");
        assertThrows(OperationException.class, () -> cache.getByTypeAndRelId("ban", 1));
        raw.del(prefix + "entry:rel:ban:1");
        raw.sadd(prefix + "entry:rel:ban:1", "1");
        assertThrows(OperationException.class, () -> cache.getByTypeAndRelId("ban", 1));
        // Invalidation removes the invalid index
        cache.invalidateByTypeAndRelId("ban", 1);
        assertFalse(cache.getByTypeAndRelId("ban", 1).isPresent());
    }

    @Test
    public void testMalformedQueryResultThrows() {
        EntryPersisted entry = entry(1, 1, BAN);
        cache.put(entry);
        QueryKey key = new QueryKey.LatestActiveQuery(UUID.randomUUID(), "ban");
        cache.putQueryResult(key, Collections.singletonList(entry));
        String resultKey = queryResultKey();
        for (String value : new String[]{"[1", "{\"a\":1}", "[1.5]", "[\"1\"]", "[1] x"}) {
            raw.set(resultKey, value);
            assertThrows(OperationException.class, () -> cache.getQueryResult(key), value);
        }
        raw.del(resultKey);
        raw.sadd(resultKey, "x");
        assertThrows(OperationException.class, () -> cache.getQueryResult(key));
    }

    @Test
    public void testMalformedEntryInQueryResultThrows() {
        EntryPersisted entry = entry(1, 1, BAN);
        cache.put(entry);
        QueryKey key = new QueryKey.LatestActiveQuery(UUID.randomUUID(), "ban");
        cache.putQueryResult(key, Collections.singletonList(entry));
        raw.hset(prefix + "entry:id:1", "d", "garbage");
        assertThrows(OperationException.class, () -> cache.getQueryResult(key));
    }

    @Test
    public void testWritesRecoverFromWrongTypesInQueryKeys() {
        raw.sadd(prefix + "query:gen", "x");
        QueryKey key = new QueryKey.LatestActiveQuery(UUID.randomUUID(), "ban");
        assertThrows(OperationException.class, () -> cache.getQueryResult(key));
        cache.invalidateAllQueryResults();
        cache.putQueryResult(key, Collections.emptyList());
        assertTrue(cache.getQueryResult(key).isPresent());
    }

    /**
     * Kills every normal client connection on the server except the raw test connection.
     */
    @Test
    public void testConnectionLossIsReportedAndRecovered() throws InterruptedException {
        cache.put(entry(1, 1, BAN));
        raw.clientKill(KillArgs.Builder.typeNormal().skipme(true));
        long deadline = System.currentTimeMillis() + 10000;
        boolean recovered = false;
        while (!recovered && System.currentTimeMillis() < deadline) {
            try {
                // Either the entry is returned or the failure is reported; never a silent miss
                assertTrue(cache.getById(1).isPresent());
                recovered = true;
            } catch (ConnectionException e) {
                Thread.sleep(100);
            }
        }
        assertTrue(recovered, "cache did not reconnect");
    }

    // Concurrency

    @Test
    public void testConcurrentReplacementKeepsIndexesConsistent() throws Exception {
        int threads = 8;
        int iterations = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int thread = t;
                futures.add(executor.submit(() -> {
                    for (int i = 0; i < iterations; i++) {
                        cache.put(entry(1, (thread + i) % 4, BAN));
                        cache.getByTypeAndRelId("ban", i % 4).ifPresent(e -> assertEquals(1, e.getId()));
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        long relId = cache.getById(1).get().getRelId();
        for (int r = 0; r < 4; r++) {
            assertEquals(r == relId, cache.getByTypeAndRelId("ban", r).isPresent(), "relId " + r);
        }
        assertEquals(1, raw.keys(prefix + "entry:rel:*").size());
    }

    @Test
    public void testConcurrentQueryInvalidationNeverReturnsInvalidatedResult() throws Exception {
        EntryPersisted entry = entry(1, 1, BAN);
        cache.put(entry);
        QueryKey key = new QueryKey.LatestActiveQuery(UUID.randomUUID(), "ban");
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 2; t++) {
                futures.add(executor.submit(() -> {
                    for (int i = 0; i < 200; i++) {
                        cache.putQueryResult(key, Collections.singletonList(entry));
                        cache.getQueryResult(key).ifPresent(r -> assertEquals(1, r.size()));
                    }
                    return null;
                }));
                futures.add(executor.submit(() -> {
                    for (int i = 0; i < 200; i++) {
                        cache.invalidateAllQueryResults();
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        cache.invalidateAllQueryResults();
        assertFalse(cache.getQueryResult(key).isPresent());
        // Every result key written for a rotated generation was deleted
        assertTrue(raw.keys(prefix + "query:r:*").isEmpty());
    }

    // Lifecycle

    @Test
    public void testCloseAndReuse() {
        cache.put(entry(1, 1, BAN));
        cache.close();
        cache.close();
        // Data survives the client; a new connection is opened on demand
        assertTrue(cache.getById(1).isPresent());
    }

    @Test
    public void testAuthentication() {
        String auth = System.getenv("SANCTIONMANAGER_TEST_REDIS_AUTH");
        Assumptions.assumeTrue(auth != null && !auth.isEmpty(), "SANCTIONMANAGER_TEST_REDIS_AUTH not set");
        String[] parts = auth.split(":", 3);
        String authHost = parts[0];
        int authPort = Integer.parseInt(parts[1]);
        char[] password = parts[2].toCharArray();
        CredentialProvider valid = consumer -> {
            try (Credential credential = new Credential("", password)) {
                consumer.accept(credential);
            }
        };
        CredentialProvider wrong = consumer -> {
            try (Credential credential = new Credential("", "wrong".toCharArray())) {
                consumer.accept(credential);
            }
        };
        RedisCacheService authed = new RedisCacheService(new RedisConfig(authHost, authPort, valid), prefix);
        RedisCacheService denied = new RedisCacheService(new RedisConfig(authHost, authPort, wrong), prefix);
        RedisCacheService anonymous = new RedisCacheService(new RedisConfig(authHost, authPort, NO_AUTH), prefix);
        try {
            authed.put(entry(1, 1, BAN));
            assertTrue(authed.getById(1).isPresent());
            assertThrows(ConnectionException.class, () -> denied.getById(1));
            // Rejected either during the handshake or by the command, depending on the server version
            assertThrows(PersistenceException.class, () -> anonymous.getById(1));
            authed.invalidateAll();
        } finally {
            authed.close();
            denied.close();
            anonymous.close();
        }
    }

    // Integration with EntryServiceCore

    @Test
    public void testEntryServiceCoreUsesRedisCache() throws Exception {
        Path dir = Files.createTempDirectory("redis-cache-test-");
        SanctionManagerCore core = null;
        try {
            List<EntryType> types = Collections.singletonList(BAN);
            CoreConfig config = new CoreConfig(
                    new StorageConfig(false, new BinaryFileConfig(dir.resolve("test.dat").toString(), 4096), null),
                    CacheConfig.redis(new RedisConfig(host, port, NO_AUTH)));
            core = createCore(config, types);
            // Use the test namespace instead of the default prefix
            CacheService cacheService = core.getCacheService();
            Field backend = CacheService.class.getDeclaredField("cacheService");
            backend.setAccessible(true);
            ((RedisCacheService) backend.get(cacheService)).close();
            backend.set(cacheService, cache);

            EntryServiceCore service = (EntryServiceCore) core.getEntryService();
            UUID target = UUID.randomUUID();
            EntryWrapper entry = (EntryWrapper) core.getEntryFactory().create("ban", target, UUID.randomUUID(), "Test", null);
            service.execute(entry);
            long id = entry.getId();
            assertTrue(cache.getById(id).isPresent());
            assertTrue(cache.getByTypeAndRelId("ban", entry.getRelId()).isPresent());

            QueryOptions options = QueryOptions.create().includeWithdrawn(true);
            QueryKey key = new QueryKey.TargetQuery(target, options);
            assertEquals(1, service.query(target, options).size());
            assertEquals(Collections.singletonList(id), ids(cache.getQueryResult(key).get()));
            UUID otherTarget = UUID.randomUUID();
            assertTrue(service.query(otherTarget, options).isEmpty());
            Optional<List<EntryPersisted>> emptyResult =
                    cache.getQueryResult(new QueryKey.TargetQuery(otherTarget, options));
            assertTrue(emptyResult.isPresent() && emptyResult.get().isEmpty(), "empty results are cached");

            UUID withdrawnBy = UUID.randomUUID();
            service.withdraw(entry, withdrawnBy, "Withdrawn");
            assertFalse(cache.getQueryResult(key).isPresent(), "mutation must invalidate query results");
            assertEquals(withdrawnBy, cache.getById(id).get().getWithdrawnBy());
            assertEquals(withdrawnBy, service.query(target, options).get(0).getWithdrawnBy());

            // delete(long) rejects withdrawn entries; delete by (type, relId) instead
            service.delete("ban", entry.getRelId());
            assertFalse(cache.getById(id).isPresent());
            assertFalse(cache.getByTypeAndRelId("ban", entry.getRelId()).isPresent());
            assertFalse(cache.getQueryResult(key).isPresent());
            assertTrue(service.query(target, options).isEmpty());

            core.shutdown();
            core = null;
            // Shutdown closed the backend; the cached data remains on the server
            assertTrue(cache.getQueryResult(key).isPresent());
        } finally {
            if (core != null) {
                core.shutdown();
            }
            Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    private static SanctionManagerCore createCore(CoreConfig config, List<EntryType> types) throws Exception {
        Constructor<SanctionManagerCore> constructor = SanctionManagerCore.class.getDeclaredConstructor(CoreConfig.class);
        constructor.setAccessible(true);
        SanctionManagerCore core = constructor.newInstance(config);
        core.types(types.toArray(new EntryType[0]));
        Field typeManagerField = SanctionManagerCore.class.getDeclaredField("typeManager");
        typeManagerField.setAccessible(true);
        typeManagerField.set(core, new TypeManager(types));
        core.initialize();
        return core;
    }
}
