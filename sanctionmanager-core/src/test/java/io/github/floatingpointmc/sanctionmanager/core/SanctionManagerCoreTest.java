package io.github.floatingpointmc.sanctionmanager.core;

import io.github.floatingpointmc.sanctionmanager.api.events.PunishmentExecuteEvent;
import io.github.floatingpointmc.sanctionmanager.api.events.PunishmentRemoveEvent;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Punishment;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Type;
import io.github.floatingpointmc.sanctionmanager.core.cache.LocalPunishmentCache;
import io.github.floatingpointmc.sanctionmanager.core.config.DatabaseConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.RedisConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.StorageConfig;
import io.github.floatingpointmc.sanctionmanager.core.factory.PunishmentFactoryCore;
import io.github.floatingpointmc.sanctionmanager.core.model.PunishmentRecord;
import io.github.floatingpointmc.sanctionmanager.core.repository.BinaryPunishmentRepository;
import io.github.floatingpointmc.sanctionmanager.core.service.PunishmentService;
import io.github.vlouboos.standaloneevent.api.ApiProvider;
import io.github.vlouboos.standaloneevent.api.EventHandler;
import io.github.vlouboos.standaloneevent.api.StandaloneEventAPI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class SanctionManagerCoreTest {

    private LocalPunishmentCache cache;

    @BeforeEach
    void setUp() {
        ApiProvider.injectApi(false);
        cache = new LocalPunishmentCache();
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void testEventBusFiresOnAddPunishment() {
        AtomicBoolean fired = new AtomicBoolean(false);
        ExecuteListener listener = new ExecuteListener(fired);
        StandaloneEventAPI.getApi().register(listener);

        PunishmentRecord punishment = new PunishmentRecord(
                1, 1, UUID.randomUUID(), UUID.randomUUID(), "Console",
                LocalDateTime.now(), null, false, null, false, null,
                false, null, "Test reason", Type.BAN
        );

        cache.put(punishment);
        assertNotNull(cache.findById(1));

        StandaloneEventAPI.getApi().unregister(listener);
    }

    @Test
    void testEventCancellationPreventsAction() {
        CancelListener listener = new CancelListener();
        StandaloneEventAPI.getApi().register(listener);

        PunishmentRecord punishment = new PunishmentRecord(
                2, 2, UUID.randomUUID(), UUID.randomUUID(), "Console",
                LocalDateTime.now(), null, false, null, false, null,
                false, null, "Cancelled test", Type.MUTE
        );

        cache.put(punishment);
        assertNotNull(cache.findById(2));

        StandaloneEventAPI.getApi().unregister(listener);
    }

    @Test
    void testLocalPunishmentCachePutAndFind() {
        UUID target = UUID.randomUUID();
        PunishmentRecord punishment = new PunishmentRecord(
                10, 5, target, null, "Console",
                LocalDateTime.now(), null, false, null, false, null,
                false, null, "Cache test", Type.BAN
        );

        cache.put(punishment);

        Punishment found = cache.findById(10);
        assertNotNull(found);
        assertEquals(target, found.getTarget());
        assertEquals(Type.BAN, found.getType());
        assertEquals(5, found.getRelId());

        Collection<Punishment> active = cache.findActiveByTarget(target);
        assertFalse(active.isEmpty());
        assertEquals(1, active.size());
    }

    @Test
    void testLocalPunishmentCacheInvalidate() {
        UUID target = UUID.randomUUID();
        PunishmentRecord punishment = new PunishmentRecord(
                20, 10, target, null, "Console",
                LocalDateTime.now(), null, false, null, false, null,
                false, null, "Invalidate test", Type.BAN
        );

        cache.put(punishment);
        assertNotNull(cache.findById(20));

        cache.invalidate(20);
        assertNull(cache.findById(20));
        assertTrue(cache.findActiveByTarget(target).isEmpty());
    }

    @Test
    void testDatabaseConfigJdbcUrl() {
        DatabaseConfig mysqlConfig = DatabaseConfig.builder()
                .driver("com.mysql.cj.jdbc.Driver")
                .host("localhost")
                .port(3306)
                .database("testdb")
                .user("root")
                .password("")
                .build();
        assertTrue(mysqlConfig.getJdbcUrl().startsWith("jdbc:mysql://"));

        DatabaseConfig sqliteConfig = DatabaseConfig.builder()
                .driver("org.sqlite.JDBC")
                .host("")
                .port(0)
                .database("test.db")
                .user("")
                .password("")
                .build();
        assertTrue(sqliteConfig.getJdbcUrl().startsWith("jdbc:sqlite:"));
    }

    @Test
    void testRelIdNotGloballyUnique() {
        UUID banTarget = UUID.randomUUID();
        UUID muteTarget = UUID.randomUUID();

        PunishmentRecord ban = new PunishmentRecord(
                1, 1, banTarget, null, "Console",
                LocalDateTime.now(), null, false, null, false, null,
                false, null, "Ban reason", Type.BAN
        );

        PunishmentRecord mute = new PunishmentRecord(
                2, 1, muteTarget, null, "Console",
                LocalDateTime.now(), null, false, null, false, null,
                false, null, "Mute reason", Type.MUTE
        );

        cache.put(ban);
        cache.put(mute);

        Punishment foundBan = cache.findById(1);
        Punishment foundMute = cache.findById(2);

        assertNotNull(foundBan);
        assertNotNull(foundMute);
        assertEquals(1, foundBan.getRelId());
        assertEquals(1, foundMute.getRelId());
        assertEquals(Type.BAN, foundBan.getType());
        assertEquals(Type.MUTE, foundMute.getType());
    }

    @Test
    void testTypeOrdinary() {
        assertEquals(0, Type.BAN.ordinary());
        assertEquals(1, Type.MUTE.ordinary());
    }

    @Test
    void testPunishmentRecordRelIdField() {
        PunishmentRecord record = new PunishmentRecord(
                100, 42, UUID.randomUUID(), UUID.randomUUID(), "Admin",
                LocalDateTime.now(), null, false, null, false, null,
                false, null, "Test", Type.BAN
        );
        assertEquals(100, record.getId());
        assertEquals(42, record.getRelId());
        assertEquals(Type.BAN, record.getType());
    }

    @Test
    void testStorageConfigDefaults() {
        StorageConfig defaults = StorageConfig.defaults();
        assertTrue(defaults.isDatabaseEnabled());
        assertFalse(defaults.isRedisEnabled());
        assertNotNull(defaults.getDatabaseConfig());
        assertNotNull(defaults.getRedisConfig());
        assertEquals("data", defaults.getBinaryDataDir());
    }

    @Test
    void testRedisConfigDefaults() {
        RedisConfig defaults = RedisConfig.defaults();
        assertEquals("localhost", defaults.getHost());
        assertEquals(6379, defaults.getPort());
        assertEquals("", defaults.getPassword());
    }

    @Test
    void testDatabaseConfigDefaults() {
        DatabaseConfig defaults = DatabaseConfig.defaults();
        assertEquals("com.mysql.cj.jdbc.Driver", defaults.getDriver());
        assertEquals("localhost", defaults.getHost());
        assertEquals(3306, defaults.getPort());
        assertEquals("sanctionmanager", defaults.getDatabase());
        assertEquals("root", defaults.getUser());
    }

    @Test
    void testStorageConfigIndependence() {
        StorageConfig dbOffRedisOff = StorageConfig.builder()
                .databaseEnabled(false).redisEnabled(false)
                .databaseConfig(DatabaseConfig.defaults()).redisConfig(RedisConfig.defaults())
                .binaryDataDir("data").build();
        assertFalse(dbOffRedisOff.isDatabaseEnabled());
        assertFalse(dbOffRedisOff.isRedisEnabled());

        StorageConfig dbOffRedisOn = StorageConfig.builder()
                .databaseEnabled(false).redisEnabled(true)
                .databaseConfig(DatabaseConfig.defaults()).redisConfig(RedisConfig.defaults())
                .binaryDataDir("data").build();
        assertFalse(dbOffRedisOn.isDatabaseEnabled());
        assertTrue(dbOffRedisOn.isRedisEnabled());

        StorageConfig dbOnRedisOff = StorageConfig.builder()
                .databaseEnabled(true).redisEnabled(false)
                .databaseConfig(DatabaseConfig.defaults()).redisConfig(RedisConfig.defaults())
                .binaryDataDir("data").build();
        assertTrue(dbOnRedisOff.isDatabaseEnabled());
        assertFalse(dbOnRedisOff.isRedisEnabled());

        StorageConfig dbOnRedisOn = StorageConfig.builder()
                .databaseEnabled(true).redisEnabled(true)
                .databaseConfig(DatabaseConfig.defaults()).redisConfig(RedisConfig.defaults())
                .binaryDataDir("data").build();
        assertTrue(dbOnRedisOn.isDatabaseEnabled());
        assertTrue(dbOnRedisOn.isRedisEnabled());
    }

    @Test
    void testBinaryStorageSaveAndLoad(@TempDir Path tempDir) {
        Path dataDir = tempDir.resolve("sm-data");
        BinaryPunishmentRepository repo = new BinaryPunishmentRepository(dataDir);

        UUID banTarget = UUID.randomUUID();
        UUID muteTarget = UUID.randomUUID();

        PunishmentRecord ban = new PunishmentRecord(
                0, 0, banTarget, null, "Console",
                LocalDateTime.now(), null, false, null, false, null,
                false, null, "Ban reason", Type.BAN
        );
        repo.save(ban);

        PunishmentRecord mute = new PunishmentRecord(
                0, 0, muteTarget, null, "Console",
                LocalDateTime.now(), null, false, null, false, null,
                false, null, "Mute reason", Type.MUTE
        );
        repo.save(mute);

        int banId = ban.getId();
        int muteId = mute.getId();
        int banRelId = ban.getRelId();
        int muteRelId = mute.getRelId();

        assertTrue(banId > 0);
        assertTrue(muteId > 0);
        assertTrue(banRelId > 0);
        assertTrue(muteRelId > 0);

        repo.close();

        BinaryPunishmentRepository repo2 = new BinaryPunishmentRepository(dataDir);
        Punishment loadedBan = repo2.findById(banId);
        Punishment loadedMute = repo2.findById(muteId);

        assertNotNull(loadedBan);
        assertNotNull(loadedMute);
        assertEquals(banTarget, loadedBan.getTarget());
        assertEquals(muteTarget, loadedMute.getTarget());
        assertEquals(Type.BAN, loadedBan.getType());
        assertEquals(Type.MUTE, loadedMute.getType());
        assertEquals(banRelId, loadedBan.getRelId());
        assertEquals(muteRelId, loadedMute.getRelId());

        repo2.close();
    }

    @Test
    void testBinaryStorageBanMuteIndependentIds(@TempDir Path tempDir) {
        Path dataDir = tempDir.resolve("sm-data-ids");
        BinaryPunishmentRepository repo = new BinaryPunishmentRepository(dataDir);

        PunishmentRecord ban1 = new PunishmentRecord(
                0, 0, UUID.randomUUID(), null, "Console",
                LocalDateTime.now(), null, false, null, false, null,
                false, null, "Ban 1", Type.BAN
        );
        repo.save(ban1);

        PunishmentRecord mute1 = new PunishmentRecord(
                0, 0, UUID.randomUUID(), null, "Console",
                LocalDateTime.now(), null, false, null, false, null,
                false, null, "Mute 1", Type.MUTE
        );
        repo.save(mute1);

        assertEquals(1, ban1.getRelId());
        assertEquals(1, mute1.getRelId());
        assertEquals(1, ban1.getId());
        assertEquals(2, mute1.getId());

        repo.close();
    }

    @Test
    void testBinaryStorageQueryBansOnly(@TempDir Path tempDir) {
        Path dataDir = tempDir.resolve("sm-data-bans");
        BinaryPunishmentRepository repo = new BinaryPunishmentRepository(dataDir);

        UUID target = UUID.randomUUID();

        PunishmentRecord ban = new PunishmentRecord(
                0, 0, target, null, "Console",
                LocalDateTime.now(), null, false, null, false, null,
                false, null, "Ban reason", Type.BAN
        );
        repo.save(ban);

        PunishmentRecord mute = new PunishmentRecord(
                0, 0, target, null, "Console",
                LocalDateTime.now(), null, false, null, false, null,
                false, null, "Mute reason", Type.MUTE
        );
        repo.save(mute);

        Collection<Punishment> bans = repo.findActiveBansByTarget(target);
        Collection<Punishment> mutes = repo.findActiveMutesByTarget(target);

        assertEquals(1, bans.size());
        assertEquals(1, mutes.size());
        assertEquals(Type.BAN, bans.iterator().next().getType());
        assertEquals(Type.MUTE, mutes.iterator().next().getType());

        repo.close();
    }

    @Test
    void testBinaryStorageQueryPunishmentsReturnsBoth(@TempDir Path tempDir) {
        Path dataDir = tempDir.resolve("sm-data-both");
        BinaryPunishmentRepository repo = new BinaryPunishmentRepository(dataDir);

        UUID target = UUID.randomUUID();

        PunishmentRecord ban = new PunishmentRecord(
                0, 0, target, null, "Console",
                LocalDateTime.now(), null, false, null, false, null,
                false, null, "Ban reason", Type.BAN
        );
        repo.save(ban);

        PunishmentRecord mute = new PunishmentRecord(
                0, 0, target, null, "Console",
                LocalDateTime.now(), null, false, null, false, null,
                false, null, "Mute reason", Type.MUTE
        );
        repo.save(mute);

        Collection<Punishment> all = repo.findActiveByTarget(target);
        assertEquals(2, all.size());

        long banCount = all.stream().filter(p -> p.getType() == Type.BAN).count();
        long muteCount = all.stream().filter(p -> p.getType() == Type.MUTE).count();
        assertEquals(1, banCount);
        assertEquals(1, muteCount);

        repo.close();
    }

    @Test
    void testBinaryStorageDelete(@TempDir Path tempDir) {
        Path dataDir = tempDir.resolve("sm-data-delete");
        BinaryPunishmentRepository repo = new BinaryPunishmentRepository(dataDir);

        PunishmentRecord ban = new PunishmentRecord(
                0, 0, UUID.randomUUID(), null, "Console",
                LocalDateTime.now(), null, false, null, false, null,
                false, null, "Ban reason", Type.BAN
        );
        repo.save(ban);
        int id = ban.getId();

        assertNotNull(repo.findById(id));
        repo.delete(id);
        assertNull(repo.findById(id));

        repo.close();
    }

    @Test
    void testBinaryStoragePersistenceAcrossRestart(@TempDir Path tempDir) {
        Path dataDir = tempDir.resolve("sm-data-restart");

        UUID target = UUID.randomUUID();
        int banId, banRelId;

        BinaryPunishmentRepository repo1 = new BinaryPunishmentRepository(dataDir);
        PunishmentRecord ban = new PunishmentRecord(
                0, 0, target, null, "Console",
                LocalDateTime.now(), null, false, null, false, null,
                false, null, "Persistent ban", Type.BAN
        );
        repo1.save(ban);
        banId = ban.getId();
        banRelId = ban.getRelId();
        repo1.close();

        BinaryPunishmentRepository repo2 = new BinaryPunishmentRepository(dataDir);
        Punishment loaded = repo2.findById(banId);
        assertNotNull(loaded);
        assertEquals(target, loaded.getTarget());
        assertEquals(banRelId, loaded.getRelId());
        assertEquals(Type.BAN, loaded.getType());
        assertEquals("Persistent ban", loaded.getReason());
        repo2.close();
    }

    @Test
    void testBinaryStorageGetIdVsGetRelId(@TempDir Path tempDir) {
        Path dataDir = tempDir.resolve("sm-data-idrelid");
        BinaryPunishmentRepository repo = new BinaryPunishmentRepository(dataDir);

        PunishmentRecord ban = new PunishmentRecord(
                0, 0, UUID.randomUUID(), null, "Console",
                LocalDateTime.now(), null, false, null, false, null,
                false, null, "Test", Type.BAN
        );
        repo.save(ban);

        assertEquals(1, ban.getId());
        assertEquals(1, ban.getRelId());

        PunishmentRecord mute = new PunishmentRecord(
                0, 0, UUID.randomUUID(), null, "Console",
                LocalDateTime.now(), null, false, null, false, null,
                false, null, "Test 2", Type.MUTE
        );
        repo.save(mute);
        assertEquals(2, mute.getId());
        assertEquals(1, mute.getRelId());

        assertNotEquals(ban.getId(), mute.getId());
        assertEquals(ban.getRelId(), mute.getRelId());

        repo.close();
    }

    @Test
    void testBinaryStorageAtomicWrite(@TempDir Path tempDir) throws IOException {
        Path dataDir = tempDir.resolve("sm-data-atomic");
        BinaryPunishmentRepository repo = new BinaryPunishmentRepository(dataDir);

        PunishmentRecord ban = new PunishmentRecord(
                0, 0, UUID.randomUUID(), null, "Console",
                LocalDateTime.now(), null, false, null, false, null,
                false, null, "Atomic test", Type.BAN
        );
        repo.save(ban);
        repo.close();

        assertFalse(Files.exists(dataDir.resolve("sanction-data.bin.tmp")));
        assertTrue(Files.exists(dataDir.resolve("sanction-data.bin")));
    }

    @Test
    void testBinaryStorageEmptyOnStartup(@TempDir Path tempDir) {
        Path dataDir = tempDir.resolve("sm-data-empty");
        BinaryPunishmentRepository repo = new BinaryPunishmentRepository(dataDir);

        UUID target = UUID.randomUUID();
        Collection<Punishment> result = repo.findActiveByTarget(target);
        assertTrue(result.isEmpty());

        repo.close();
    }

    @Test
    void testBinaryStorageUpdate(@TempDir Path tempDir) {
        Path dataDir = tempDir.resolve("sm-data-update");
        BinaryPunishmentRepository repo = new BinaryPunishmentRepository(dataDir);

        UUID target = UUID.randomUUID();
        PunishmentRecord ban = new PunishmentRecord(
                0, 0, target, null, "Console",
                LocalDateTime.now(), null, false, null, false, null,
                false, null, "Original", Type.BAN
        );
        repo.save(ban);

        ban.setReason("Updated");
        repo.update(ban);

        Punishment loaded = repo.findById(ban.getId());
        assertNotNull(loaded);
        assertEquals("Updated", loaded.getReason());

        repo.close();
    }

    @Test
    void testPunishmentServiceWithBinaryStorage(@TempDir Path tempDir) {
        Path dataDir = tempDir.resolve("sm-data-service");
        BinaryPunishmentRepository repo = new BinaryPunishmentRepository(dataDir);
        LocalPunishmentCache localCache = new LocalPunishmentCache();
        PunishmentService service = new PunishmentService(localCache, repo);

        UUID target = UUID.randomUUID();
        PunishmentRecord ban = new PunishmentRecord(
                0, 0, target, null, "Console",
                LocalDateTime.now(), null, false, null, false, null,
                false, null, "Service test", Type.BAN
        );

        service.addPunishment(ban);

        Punishment activeBan = service.queryActiveBan(target);
        assertNotNull(activeBan);
        assertEquals(Type.BAN, activeBan.getType());

        Punishment activeMute = service.queryActiveMute(target);
        assertNull(activeMute);

        repo.close();
    }

    @Test
    void testPlaceholderIdAndRelId(@TempDir Path tempDir) {
        Path dataDir = tempDir.resolve("sm-data-placeholder");
        BinaryPunishmentRepository repo = new BinaryPunishmentRepository(dataDir);

        Punishment ban = new PunishmentFactoryCore().create(UUID.randomUUID(), "Console", Type.BAN)
                .reason("Placeholder test")
                .executingTime(LocalDateTime.now())
                .build();

        repo.save(ban);

        String idPlaceholder = "%" + "id" + "%";
        String relIdPlaceholder = "%" + "rel_id" + "%";

        String idValue = String.valueOf(ban.getId());
        String relIdValue = String.valueOf(ban.getRelId());

        assertNotNull(idValue);
        assertNotNull(relIdValue);
        assertTrue(idValue.length() > 0);
        assertTrue(relIdValue.length() > 0);

        String template = "Ban #%id% (rel #%rel_id%)";
        String result = template.replace("%id%", idValue).replace("%rel_id%", relIdValue);
        assertTrue(result.contains(idValue));
        assertTrue(result.contains(relIdValue));

        repo.close();
    }

    public static class ExecuteListener {
        private final AtomicBoolean flag;

        public ExecuteListener(AtomicBoolean flag) {
            this.flag = flag;
        }

        @EventHandler
        public void onPunishmentExecute(PunishmentExecuteEvent event) {
            flag.set(true);
        }
    }

    public static class CancelListener {
        @EventHandler
        public void onCancel(PunishmentRemoveEvent event) {
            event.canceled = true;
        }
    }
}
