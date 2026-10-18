package io.github.floatingpointmc.sanctionmanager.core.service.cache;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import io.github.floatingpointmc.sanctionmanager.api.exceptions.persistence.ConnectionException;
import io.github.floatingpointmc.sanctionmanager.api.exceptions.persistence.OperationException;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import io.github.floatingpointmc.sanctionmanager.core.config.cache.RedisConfig;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;
import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.protocol.ProtocolVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Redis-backed cache service implementation using the Lettuce client.
 * <p>
 * Implements the same observable contract as {@link MemoryCacheService}: entries are indexed by ID and by
 * (type, relId), query results store entry IDs, and a query result is only returned if every referenced entry
 * is still cached. A cached empty query result is returned as an empty list, which is distinct from a miss.
 * Redis failures are never reported as misses; see "Failures" below.
 * </p>
 *
 * <h2>Keys</h2>
 * <p>
 * Every key starts with the prefix {@value #DEFAULT_KEY_PREFIX} (written {@code P} below). The prefix is not
 * configurable because {@link RedisConfig} has no namespace setting.
 * </p>
 * <ul>
 *   <li>{@code P entry:id:<id>} - hash with field {@code d} (entry JSON) and field {@code r} (the full key of
 *       the entry's (type, relId) index).</li>
 *   <li>{@code P entry:rel:<typeId>:<relId>} - string holding the entry ID. The relId is the text after the
 *       last colon and never contains one, so the key is unambiguous for any type identifier.</li>
 *   <li>{@code P query:gen} - string holding the current random query generation token.</li>
 *   <li>{@code P query:r:<token>:<canonical key>} - JSON array of entry IDs, where the canonical key is
 *       {@link QueryKey#canonicalForm()}.</li>
 *   <li>{@code P query:m:<token>} - set of the query result keys written under {@code token}.</li>
 * </ul>
 *
 * <h2>Atomicity and consistency</h2>
 * <p>
 * Every operation that touches more than one key runs as a single Lua script, so the ID index, the
 * (type, relId) index and query results are updated and read atomically with respect to other Redis clients.
 * Replacing an entry removes its previous (type, relId) index if that index still points to it. Read scripts
 * verify that an index and the entry it references agree; a dangling index is reported as a miss.
 * {@link #invalidateAllQueryResults()} replaces the generation token, which makes every stored query result
 * unreachable in O(1) even if the tracking set has been evicted, and then deletes the tracked keys.
 * </p>
 * <p>
 * Atomicity is per operation only. The cache does not coordinate with storage: a query that reads storage
 * before a concurrent mutation and publishes its result after the mutation's invalidation can still store a
 * stale entry or query result, exactly as with {@link MemoryCacheService}. Preventing this requires
 * coordination in the caller (for example a version check), which the current {@code EntryServiceCore}
 * does not perform.
 * </p>
 * <p>
 * {@link #invalidateAll()} is not atomic: it first rotates the query generation and then deletes every key
 * under the prefix using incremental {@code SCAN}. Writes running concurrently with it may survive.
 * </p>
 * <p>
 * Lua scripts compute some key names at runtime, so this implementation supports standalone Redis servers
 * only, not Redis Cluster.
 * </p>
 *
 * <h2>Bounding</h2>
 * <p>
 * Keys are written without expiry and there is no capacity limit; memory bounding is left to the server's
 * {@code maxmemory} policy. Evicting any key never makes a read return wrong data, but under an
 * {@code allkeys-*} policy unreachable keys may remain until they are evicted themselves.
 * </p>
 *
 * <h2>Serialization</h2>
 * <p>
 * Entries are stored as JSON objects written with Gson, containing a format version and every field of
 * {@link EntryPersisted}. Decoding is strict: unknown format versions, missing fields, wrong JSON types,
 * non-integral numbers and non-canonical UUIDs are rejected. Decoded entries are new objects; the entry type is
 * reconstructed with {@link EntryType#of(String, boolean)}.
 * </p>
 *
 * <h2>Failures</h2>
 * <ul>
 *   <li>Connection failures (including credential provider failures and rejected authentication), commands
 *       issued while the connection is lost and command timeouts throw {@link ConnectionException}.</li>
 *   <li>Server error replies, invalid stored data, values that cannot be serialized and interruption of the
 *       calling thread throw {@link OperationException}.</li>
 * </ul>
 * <p>
 * Reads throw on invalid stored data instead of reporting a miss. Writes overwrite keys of the wrong Redis type.
 * There is no fallback to another cache.
 * </p>
 *
 * <h2>Lifecycle</h2>
 * <p>
 * This service owns one {@link RedisClient} and one shared, thread-safe connection. Both are created on first
 * use, not by the constructor, and a failed connection attempt is retried by the next operation. While the
 * connection is down, commands are rejected immediately and Lettuce reconnects in the background.
 * {@link #close()} releases the client; a later operation creates a new one. Command timeouts use the Lettuce
 * default because {@link RedisConfig} has no timeout setting. The RESP2 protocol is used so that servers older
 * than Redis 6 are supported.
 * </p>
 * <p>
 * Credentials are requested from the configured {@code CredentialProvider} on every connection handshake. An
 * empty username sends {@code AUTH <password>}; an empty password sends no {@code AUTH} at all.
 * </p>
 */
public class RedisCacheService implements ICacheService, AutoCloseable {
    /** The prefix of every key written by this service. */
    static final String DEFAULT_KEY_PREFIX = "sanctionmanager:cache:";

    private static final int FORMAT_VERSION = 1;
    private static final String FIELD_DATA = "d";
    private static final int SCAN_BATCH = 500;
    private static final TypeAdapter<JsonElement> JSON = new Gson().getAdapter(JsonElement.class);

    // Lua helpers that tolerate keys of the wrong type; used by write scripts so they can replace invalid data.
    private static final String LUA_HELPERS =
            "local function str(k) if redis.call('TYPE', k).ok == 'string' then return redis.call('GET', k) end return false end\n" +
            "local function field(k, f) if redis.call('TYPE', k).ok == 'hash' then return redis.call('HGET', k, f) end return false end\n";

    // KEYS: entry key, rel key. ARGV: id, json, rel key prefix.
    private static final Script PUT_ENTRY = new Script(LUA_HELPERS +
            "local old = field(KEYS[1], 'r')\n" +
            "if old and old ~= KEYS[2] and string.sub(old, 1, #ARGV[3]) == ARGV[3] and str(old) == ARGV[1] then\n" +
            "  redis.call('DEL', old)\n" +
            "end\n" +
            "redis.call('DEL', KEYS[1])\n" +
            "redis.call('HMSET', KEYS[1], 'r', KEYS[2], 'd', ARGV[2])\n" +
            "redis.call('SET', KEYS[2], ARGV[1])\n" +
            "return 1\n");

    // KEYS: entry key. ARGV: id, rel key prefix.
    private static final Script INVALIDATE_BY_ID = new Script(LUA_HELPERS +
            "local r = field(KEYS[1], 'r')\n" +
            "if r and string.sub(r, 1, #ARGV[2]) == ARGV[2] and str(r) == ARGV[1] then\n" +
            "  redis.call('DEL', r)\n" +
            "end\n" +
            "redis.call('DEL', KEYS[1])\n" +
            "return 1\n");

    // KEYS: rel key. ARGV: entry key prefix.
    private static final Script INVALIDATE_BY_REL = new Script(LUA_HELPERS +
            "local id = str(KEYS[1])\n" +
            "redis.call('DEL', KEYS[1])\n" +
            "if id and string.match(id, '^%-?%d+$') then\n" +
            "  local ek = ARGV[1] .. id\n" +
            "  if field(ek, 'r') == KEYS[1] then redis.call('DEL', ek) end\n" +
            "end\n" +
            "return 1\n");

    // KEYS: rel key. ARGV: entry key prefix. Returns the entry JSON or nil.
    private static final Script GET_BY_REL = new Script(
            "local id = redis.call('GET', KEYS[1])\n" +
            "if not id then return false end\n" +
            "if not string.match(id, '^%-?%d+$') then\n" +
            "  return redis.error_reply('invalid cached index value at ' .. KEYS[1])\n" +
            "end\n" +
            "local h = redis.call('HMGET', ARGV[1] .. id, 'r', 'd')\n" +
            "if h[1] ~= KEYS[1] then return false end\n" +
            "if not h[2] then return redis.error_reply('cached entry ' .. id .. ' has no data') end\n" +
            "return h[2]\n");

    // KEYS: generation key. ARGV: result key prefix, canonical query key, entry key prefix.
    // Returns {} on a miss, otherwise {ids JSON, entry JSON...}. A value not in the exact format written by
    // putQueryResult is returned without entries so that the caller rejects it instead of reporting a miss.
    private static final Script GET_QUERY = new Script(
            "local token = redis.call('GET', KEYS[1])\n" +
            "if not token then return {} end\n" +
            "local raw = redis.call('GET', ARGV[1] .. token .. ':' .. ARGV[2])\n" +
            "if not raw then return {} end\n" +
            "local body = string.match(raw, '^%[(.*)%]$')\n" +
            "if not body then return {raw} end\n" +
            "local ids = {}\n" +
            "if body ~= '' then\n" +
            "  for id in string.gmatch(body .. ',', '([^,]*),') do\n" +
            "    if not string.match(id, '^%-?%d+$') then return {raw} end\n" +
            "    ids[#ids + 1] = id\n" +
            "  end\n" +
            "end\n" +
            "local out = {raw}\n" +
            "for i = 1, #ids do\n" +
            "  local d = redis.call('HGET', ARGV[3] .. ids[i], 'd')\n" +
            "  if not d then return {} end\n" +
            "  out[#out + 1] = d\n" +
            "end\n" +
            "return out\n");

    // KEYS: generation key. ARGV: result key prefix, members key prefix, canonical query key, ids JSON, new token.
    private static final Script PUT_QUERY = new Script(LUA_HELPERS +
            "local token = str(KEYS[1])\n" +
            "if not token then\n" +
            "  token = ARGV[5]\n" +
            "  redis.call('SET', KEYS[1], token)\n" +
            "end\n" +
            "local qk = ARGV[1] .. token .. ':' .. ARGV[3]\n" +
            "redis.call('SET', qk, ARGV[4])\n" +
            "local mk = ARGV[2] .. token\n" +
            "local t = redis.call('TYPE', mk).ok\n" +
            "if t ~= 'set' and t ~= 'none' then redis.call('DEL', mk) end\n" +
            "redis.call('SADD', mk, qk)\n" +
            "return 1\n");

    // KEYS: generation key. ARGV: result key prefix, members key prefix, canonical query key.
    private static final Script INVALIDATE_QUERY = new Script(LUA_HELPERS +
            "local token = str(KEYS[1])\n" +
            "if token then\n" +
            "  local qk = ARGV[1] .. token .. ':' .. ARGV[3]\n" +
            "  redis.call('DEL', qk)\n" +
            "  local mk = ARGV[2] .. token\n" +
            "  if redis.call('TYPE', mk).ok == 'set' then redis.call('SREM', mk, qk) end\n" +
            "end\n" +
            "return 1\n");

    // KEYS: generation key. ARGV: result key prefix, members key prefix, new token.
    private static final Script INVALIDATE_ALL_QUERIES = new Script(LUA_HELPERS +
            "local old = str(KEYS[1])\n" +
            "redis.call('SET', KEYS[1], ARGV[3])\n" +
            "if old then\n" +
            "  local mk = ARGV[2] .. old\n" +
            "  if redis.call('TYPE', mk).ok == 'set' then\n" +
            "    local p = ARGV[1] .. old .. ':'\n" +
            "    local members = redis.call('SMEMBERS', mk)\n" +
            "    local batch = {}\n" +
            "    for i = 1, #members do\n" +
            "      if string.sub(members[i], 1, #p) == p then\n" +
            "        batch[#batch + 1] = members[i]\n" +
            "        if #batch == " + SCAN_BATCH + " then redis.call('DEL', unpack(batch)) batch = {} end\n" +
            "      end\n" +
            "    end\n" +
            "    if #batch > 0 then redis.call('DEL', unpack(batch)) end\n" +
            "  end\n" +
            "  redis.call('DEL', mk)\n" +
            "end\n" +
            "return 1\n");

    private final @NotNull RedisConfig config;
    private final @NotNull RedisURI redisUri;
    private final @NotNull String keyPrefix;
    private final @NotNull String entryIdPrefix;
    private final @NotNull String entryRelPrefix;
    private final @NotNull String queryResultPrefix;
    private final @NotNull String queryMembersPrefix;
    private final @NotNull String queryGenerationKey;

    private final Object lifecycleLock = new Object();
    private @Nullable RedisClient client;
    private volatile @Nullable StatefulRedisConnection<String, String> connection;

    /**
     * Creates a Redis cache service for the given configuration.
     * <p>
     * No connection is opened; the first cache operation connects.
     * </p>
     *
     * @param config the Redis configuration. Cannot be null.
     * @throws NullPointerException if {@code config} is null
     * @throws IllegalArgumentException if the host or port is invalid
     */
    public RedisCacheService(@NotNull RedisConfig config) {
        this(config, DEFAULT_KEY_PREFIX);
    }

    /**
     * Creates a Redis cache service that uses a custom key prefix. Intended for tests.
     *
     * @param config    the Redis configuration. Cannot be null.
     * @param keyPrefix the prefix of every key. Cannot be null or empty.
     * @throws NullPointerException if {@code config} or {@code keyPrefix} is null
     * @throws IllegalArgumentException if the prefix is empty, or the host or port is invalid
     */
    RedisCacheService(@NotNull RedisConfig config, @NotNull String keyPrefix) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        Objects.requireNonNull(keyPrefix, "keyPrefix cannot be null");
        if (keyPrefix.isEmpty()) {
            throw new IllegalArgumentException("keyPrefix cannot be empty");
        }
        this.keyPrefix = keyPrefix;
        this.entryIdPrefix = keyPrefix + "entry:id:";
        this.entryRelPrefix = keyPrefix + "entry:rel:";
        this.queryResultPrefix = keyPrefix + "query:r:";
        this.queryMembersPrefix = keyPrefix + "query:m:";
        this.queryGenerationKey = keyPrefix + "query:gen";
        this.redisUri = RedisURI.Builder.redis(config.getHost(), config.getPort())
                .withAuthentication(CredentialsProvider.from(this::resolveCredentials))
                .build();
    }

    /**
     * {@inheritDoc}
     *
     * @throws ConnectionException if Redis cannot be reached
     * @throws OperationException if the command fails or the stored entry is invalid
     */
    @Override
    public @NotNull Optional<EntryPersisted> getById(long id) {
        String json = execute(commands -> commands.hget(entryKey(id), FIELD_DATA));
        if (json == null) {
            return Optional.empty();
        }
        EntryPersisted entry = decodeEntry(json);
        if (entry.getId() != id) {
            throw invalidData("entry cached under id " + id + " has id " + entry.getId());
        }
        return Optional.of(entry);
    }

    /**
     * {@inheritDoc}
     *
     * @throws ConnectionException if Redis cannot be reached
     * @throws OperationException if the command fails or the stored index or entry is invalid
     */
    @Override
    public @NotNull Optional<EntryPersisted> getByTypeAndRelId(@NotNull String typeId, long relId) {
        String json = runScript(GET_BY_REL, ScriptOutputType.VALUE,
                new String[]{relKey(typeId, relId)}, entryIdPrefix);
        if (json == null) {
            return Optional.empty();
        }
        EntryPersisted entry = decodeEntry(json);
        if (!entry.getType().getIdentifier().equals(typeId) || entry.getRelId() != relId) {
            throw invalidData("entry cached under (" + typeId + ", " + relId + ") has ("
                    + entry.getType().getIdentifier() + ", " + entry.getRelId() + ")");
        }
        return Optional.of(entry);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The ID list and the referenced entries are read atomically.
     * </p>
     *
     * @throws ConnectionException if Redis cannot be reached
     * @throws OperationException if the command fails or the stored result or an entry is invalid
     */
    @Override
    public @NotNull Optional<List<EntryPersisted>> getQueryResult(@NotNull QueryKey key) {
        List<Object> reply = runScript(GET_QUERY, ScriptOutputType.MULTI,
                new String[]{queryGenerationKey}, queryResultPrefix, key.canonicalForm(), entryIdPrefix);
        if (reply.isEmpty()) {
            return Optional.empty();
        }
        List<Long> ids = decodeIds(replyString(reply.get(0)));
        if (reply.size() != ids.size() + 1) {
            throw invalidData("cached query result does not match its entry IDs");
        }
        List<EntryPersisted> result = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            String json = replyString(reply.get(i + 1));
            EntryPersisted entry = decodeEntry(json);
            if (entry.getId() != ids.get(i)) {
                throw invalidData("entry cached under id " + ids.get(i) + " has id " + entry.getId());
            }
            result.add(entry);
        }
        return Optional.of(result);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Atomically replaces any cached entry with the same ID and removes that entry's previous (type, relId)
     * index if it still points to this ID.
     * </p>
     *
     * @throws ConnectionException if Redis cannot be reached
     * @throws OperationException if the command fails or the entry cannot be serialized
     */
    @Override
    public void put(@NotNull EntryPersisted entry) {
        String json = encodeEntry(entry);
        runScript(PUT_ENTRY, ScriptOutputType.INTEGER,
                new String[]{entryKey(entry.getId()), relKey(entry.getType().getIdentifier(), entry.getRelId())},
                Long.toString(entry.getId()), json, entryRelPrefix);
    }

    /**
     * {@inheritDoc}
     *
     * @throws ConnectionException if Redis cannot be reached
     * @throws OperationException if the command fails
     */
    @Override
    public void putQueryResult(@NotNull QueryKey key, @NotNull List<EntryPersisted> entries) {
        JsonArray ids = new JsonArray();
        for (EntryPersisted entry : entries) {
            ids.add(entry.getId());
        }
        runScript(PUT_QUERY, ScriptOutputType.INTEGER, new String[]{queryGenerationKey},
                queryResultPrefix, queryMembersPrefix, key.canonicalForm(), ids.toString(), newToken());
    }

    /**
     * {@inheritDoc}
     * <p>
     * The (type, relId) index is removed only if it still points to this ID.
     * </p>
     *
     * @throws ConnectionException if Redis cannot be reached
     * @throws OperationException if the command fails
     */
    @Override
    public void invalidateById(long id) {
        runScript(INVALIDATE_BY_ID, ScriptOutputType.INTEGER, new String[]{entryKey(id)},
                Long.toString(id), entryRelPrefix);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The referenced entry is removed only if its own (type, relId) is the given one.
     * </p>
     *
     * @throws ConnectionException if Redis cannot be reached
     * @throws OperationException if the command fails
     */
    @Override
    public void invalidateByTypeAndRelId(@NotNull String typeId, long relId) {
        runScript(INVALIDATE_BY_REL, ScriptOutputType.INTEGER, new String[]{relKey(typeId, relId)}, entryIdPrefix);
    }

    /**
     * {@inheritDoc}
     *
     * @throws ConnectionException if Redis cannot be reached
     * @throws OperationException if the command fails
     */
    @Override
    public void invalidateQueryResult(@NotNull QueryKey key) {
        runScript(INVALIDATE_QUERY, ScriptOutputType.INTEGER, new String[]{queryGenerationKey},
                queryResultPrefix, queryMembersPrefix, key.canonicalForm());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Query results become unreachable atomically; the remaining keys under this service's prefix are then
     * deleted with incremental {@code SCAN}, which is not atomic. Keys outside the prefix are never touched.
     * </p>
     *
     * @throws ConnectionException if Redis cannot be reached
     * @throws OperationException if a command fails
     */
    @Override
    public void invalidateAll() {
        invalidateAllQueryResults();
        ScanArgs args = ScanArgs.Builder.matches(escapeGlob(keyPrefix) + "*").limit(SCAN_BATCH);
        ScanCursor cursor = ScanCursor.INITIAL;
        do {
            ScanCursor current = cursor;
            KeyScanCursor<String> page = execute(commands -> commands.scan(current, args));
            List<String> keys = page.getKeys();
            if (!keys.isEmpty()) {
                execute(commands -> commands.del(keys.toArray(new String[0])));
            }
            cursor = page;
        } while (!cursor.isFinished());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Atomically replaces the query generation, so no previously stored result can be read again, and deletes
     * the results tracked for the previous generation.
     * </p>
     *
     * @throws ConnectionException if Redis cannot be reached
     * @throws OperationException if the command fails
     */
    @Override
    public void invalidateAllQueryResults() {
        runScript(INVALIDATE_ALL_QUERIES, ScriptOutputType.INTEGER, new String[]{queryGenerationKey},
                queryResultPrefix, queryMembersPrefix, newToken());
    }

    /**
     * Closes the connection and shuts down the Redis client. Cached data is kept on the server.
     * <p>
     * This method is idempotent. A cache operation after closing opens a new client and connection.
     * Operations running concurrently with this method may fail with {@link ConnectionException}.
     * </p>
     */
    @Override
    public void close() {
        RedisClient closingClient;
        StatefulRedisConnection<String, String> closingConnection;
        synchronized (lifecycleLock) {
            closingClient = client;
            closingConnection = connection;
            client = null;
            connection = null;
        }
        try {
            if (closingConnection != null) {
                closingConnection.close();
            }
        } finally {
            if (closingClient != null) {
                closingClient.shutdown();
            }
        }
    }

    // Connection handling

    private @NotNull StatefulRedisConnection<String, String> connection() {
        StatefulRedisConnection<String, String> current = connection;
        if (current != null) {
            return current;
        }
        synchronized (lifecycleLock) {
            if (connection != null) {
                return connection;
            }
            if (client == null) {
                RedisClient created = RedisClient.create(redisUri);
                created.setOptions(ClientOptions.builder()
                        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                        .protocolVersion(ProtocolVersion.RESP2)
                        .build());
                client = created;
            }
            try {
                connection = client.connect(StringCodec.UTF8);
            } catch (RuntimeException e) {
                // Includes credential provider failures, which Lettuce wraps in CompletionException
                throw new ConnectionException("Unable to connect to Redis cache at "
                        + config.getHost() + ":" + config.getPort(), e);
            }
            return connection;
        }
    }

    private RedisCredentials resolveCredentials() {
        AtomicReference<RedisCredentials> result = new AtomicReference<>();
        config.getCredentialProvider().useCredential(credential -> credential.usePassword(password -> {
            String username = credential.getUsername();
            result.set(RedisCredentials.just(
                    username == null || username.isEmpty() ? null : username,
                    password.length == 0 ? null : password)); // Copies the password
        }));
        RedisCredentials credentials = result.get();
        if (credentials == null) {
            throw new ConnectionException("Redis credential provider did not supply a credential");
        }
        return credentials;
    }

    private <T> T execute(Function<RedisCommands<String, String>, T> operation) {
        StatefulRedisConnection<String, String> current = connection();
        try {
            return operation.apply(current.sync());
        } catch (RedisCommandExecutionException e) {
            // Error reply from the server
            throw new OperationException("Redis cache command failed: " + e.getMessage(), e);
        } catch (RedisCommandInterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OperationException("Interrupted while waiting for the Redis cache", e);
        } catch (RedisException e) {
            // Timeouts, connection loss and commands rejected while disconnected
            throw new ConnectionException("Redis cache is unreachable: " + e.getMessage(), e);
        }
    }

    private <T> T runScript(Script script, ScriptOutputType type, String[] keys, String... args) {
        return execute(commands -> {
            try {
                return commands.<T>evalsha(script.sha, type, keys, args);
            } catch (RedisNoScriptException e) {
                return commands.<T>eval(script.source, type, keys, args);
            }
        });
    }

    // Keys

    private String entryKey(long id) {
        return entryIdPrefix + id;
    }

    private String relKey(@NotNull String typeId, long relId) {
        return entryRelPrefix + typeId + ":" + relId;
    }

    private static String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String escapeGlob(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if (c == '*' || c == '?' || c == '[' || c == ']' || c == '\\') {
                builder.append('\\');
            }
            builder.append(c);
        }
        return builder.toString();
    }

    // Serialization

    private static String encodeEntry(EntryPersisted entry) {
        JsonObject json = new JsonObject();
        json.addProperty("v", FORMAT_VERSION);
        json.addProperty("id", entry.getId());
        json.addProperty("relId", entry.getRelId());
        json.addProperty("type", entry.getType().getIdentifier());
        json.addProperty("typeExtraData", entry.getType().withExtraData());
        json.addProperty("target", entry.getTarget().toString());
        json.addProperty("executor", entry.getExecutor().toString());
        Date expiryTime = entry.getExpiryTime();
        json.addProperty("expiryTime", expiryTime != null ? expiryTime.getTime() : null);
        json.addProperty("executingTime", entry.getExecutingTime().getTime());
        json.addProperty("reason", entry.getReason());
        JsonObject data = entry.getDataInternal();
        json.add("data", data != null ? data.deepCopy() : null);
        UUID withdrawnBy = entry.getWithdrawnBy();
        json.addProperty("withdrawnBy", withdrawnBy != null ? withdrawnBy.toString() : null);
        json.addProperty("withdrawalReason", entry.getWithdrawalReason());
        json.addProperty("overridden", entry.isOverridden());
        json.addProperty("overriddenBy", entry.getOverriddenBy());
        json.addProperty("version", entry.getVersion());
        try {
            StringWriter out = new StringWriter();
            JsonWriter writer = new JsonWriter(out);
            writer.setSerializeNulls(true);
            JSON.write(writer, json);
            writer.flush();
            return out.toString();
        } catch (IOException | IllegalArgumentException e) {
            throw new OperationException("Cannot serialize entry " + entry.getId() + " for the Redis cache", e);
        }
    }

    private static EntryPersisted decodeEntry(String value) {
        JsonElement element = parseJson(value);
        if (!element.isJsonObject()) {
            throw invalidData("cached entry is not a JSON object");
        }
        JsonObject json = element.getAsJsonObject();
        int version = (int) requireLong(json, "v");
        if (version != FORMAT_VERSION) {
            throw invalidData("unsupported cached entry format version " + version);
        }
        EntryType type;
        try {
            type = EntryType.of(requireString(json, "type"), requireBoolean(json, "typeExtraData"));
        } catch (IllegalArgumentException e) {
            throw new OperationException("Invalid data in Redis cache: invalid entry type", e);
        }
        Long expiryTime = optionalLong(json, "expiryTime");
        JsonElement data = requireField(json, "data");
        if (!data.isJsonNull() && !data.isJsonObject()) {
            throw invalidData("field 'data' is not a JSON object");
        }
        String withdrawnBy = optionalString(json, "withdrawnBy");
        long intVersion = requireLong(json, "version");
        if (intVersion < Integer.MIN_VALUE || intVersion > Integer.MAX_VALUE) {
            throw invalidData("field 'version' is out of range");
        }
        return new EntryPersisted(
                requireLong(json, "id"),
                requireLong(json, "relId"),
                type,
                parseUuid(requireString(json, "target"), "target"),
                parseUuid(requireString(json, "executor"), "executor"),
                expiryTime != null ? new Date(expiryTime) : null,
                new Date(requireLong(json, "executingTime")),
                optionalString(json, "reason"),
                data.isJsonNull() ? null : data.getAsJsonObject(),
                withdrawnBy != null ? parseUuid(withdrawnBy, "withdrawnBy") : null,
                optionalString(json, "withdrawalReason"),
                requireBoolean(json, "overridden"),
                optionalLong(json, "overriddenBy"),
                (int) intVersion
        );
    }

    private static List<Long> decodeIds(String value) {
        JsonElement element = parseJson(value);
        if (!element.isJsonArray()) {
            throw invalidData("cached query result is not a JSON array");
        }
        List<Long> ids = new ArrayList<>();
        for (JsonElement id : element.getAsJsonArray()) {
            ids.add(toLong(id, "query result ID"));
        }
        return ids;
    }

    private static JsonElement parseJson(String value) {
        try {
            JsonReader reader = new JsonReader(new StringReader(value));
            JsonElement element = JSON.read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw invalidData("trailing content after JSON value");
            }
            return element;
        } catch (IOException | RuntimeException e) {
            if (e instanceof OperationException) {
                throw (OperationException) e;
            }
            throw new OperationException("Invalid data in Redis cache: malformed JSON", e);
        }
    }

    private static String replyString(Object value) {
        if (!(value instanceof String)) {
            throw invalidData("unexpected script reply element " + value);
        }
        return (String) value;
    }

    private static JsonElement requireField(JsonObject json, String name) {
        JsonElement value = json.get(name);
        if (value == null) {
            throw invalidData("missing field '" + name + "'");
        }
        return value;
    }

    private static long requireLong(JsonObject json, String name) {
        return toLong(requireField(json, name), "field '" + name + "'");
    }

    private static @Nullable Long optionalLong(JsonObject json, String name) {
        JsonElement value = requireField(json, name);
        return value.isJsonNull() ? null : toLong(value, "field '" + name + "'");
    }

    private static long toLong(JsonElement value, String description) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw invalidData(description + " is not a number");
        }
        try {
            return Long.parseLong(value.getAsString());
        } catch (NumberFormatException e) {
            throw new OperationException("Invalid data in Redis cache: " + description + " is not an integer", e);
        }
    }

    private static String requireString(JsonObject json, String name) {
        String value = optionalString(json, name);
        if (value == null) {
            throw invalidData("field '" + name + "' is null");
        }
        return value;
    }

    private static @Nullable String optionalString(JsonObject json, String name) {
        JsonElement value = requireField(json, name);
        if (value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw invalidData("field '" + name + "' is not a string");
        }
        return value.getAsString();
    }

    private static boolean requireBoolean(JsonObject json, String name) {
        JsonElement value = requireField(json, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw invalidData("field '" + name + "' is not a boolean");
        }
        return value.getAsBoolean();
    }

    private static UUID parseUuid(String value, String name) {
        try {
            UUID uuid = UUID.fromString(value);
            if (uuid.toString().equals(value)) {
                return uuid;
            }
        } catch (IllegalArgumentException ignored) {
            // Reported below
        }
        throw invalidData("field '" + name + "' is not a UUID");
    }

    private static OperationException invalidData(String message) {
        return new OperationException("Invalid data in Redis cache: " + message);
    }

    /**
     * A Lua script with its precomputed SHA-1 digest, executed with {@code EVALSHA} and loaded on demand.
     */
    private static final class Script {
        private final String source;
        private final String sha;

        private Script(String source) {
            this.source = source;
            try {
                byte[] digest = MessageDigest.getInstance("SHA-1").digest(source.getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder(digest.length * 2);
                for (byte b : digest) {
                    hex.append(String.format("%02x", b & 0xff));
                }
                this.sha = hex.toString();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-1 is not available", e);
            }
        }
    }
}
