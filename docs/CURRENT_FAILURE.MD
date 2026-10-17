# Current Failure: API/Core Rework Handoff

This document replaces the earlier audit. Baseline: `8869d37` and the
working tree inspected on October 17, 2026. Decision: **REJECT** for the
confirmed defects below, not for reserved implementations.

## A. Current Scope

`settings.gradle.kts` includes only `sanctionmanager-api` and
`sanctionmanager-core`; Core depends on API. The current operational path
under review is the Entry service using binary-file storage and, when
enabled, the in-memory cache. The public `Entry` and `EntryService`
contracts apply to that path. The README still describes older APIs and
excluded adapters, so it does not expand this deliverable.

Minecraft, Spigot, Bungee, and Velocity are commented out of Gradle
settings by `8869d37`. Their source directories remain on disk but are
not current projects; do not restore or build them. Redis caching and
database storage are explicitly unfinished in their class documentation
and are reserved for later work (section C). Their mere presence in
configuration does not make implementation mandatory now.

At audit start `git status --short --branch` showed only the existing
modification to this document (`M docs/CURRENT_FAILURE.MD`, Git's tracked
capitalization on this case-insensitive Windows filesystem). No
application-source changes are authorized by this audit.

## B. Confirmed Defects Requiring Fixes

The paths below are relative to the repository root. Each item concerns
the currently included binary/in-memory path. "Confirmed" means the
specified code path demonstrates the failure condition; section F
distinguishes this from a runtime regression test.

### 1. High: Binary Core initialization cannot use its own type manager

- **Problem:** Normal `initialize()` calls binary storage before
  constructing the type manager that storage requires.
- **Location:** `sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/SanctionManagerCore.java`,
  `initialize` (81-82), `getTypeManager` (129-133);
  `service/storage/BinaryFileStorageService.java`, `initialize` (49-56).
- **Evidence:** Binary initialization calls `core.getTypeManager()` at
  line 53 while the field is still null; the getter throws. Both
  `EntryServiceCoreTest` and `EntryServiceCoreCacheTest` assign the field
  reflectively before calling `initialize`, bypassing this path. The
  Core constructor is also private, and no public construction entry
  point was found in the included code.
- **Impact:** Ordinary binary Core setup cannot reach a usable Entry
  service without reflection.
- **Required fix:** Make type information available before initializing
  binary storage and provide a supported construction path consistent
  with the current Core API. Do not revive obsolete platform APIs.
- **Acceptance test:** Construct Core without reflection using its
  supported entry point, register a type, initialize binary storage,
  execute/query an Entry, then shut down. The test must not inject
  `typeManager` or replace storage reflectively merely to initialize.

### 2. High: Binary deletion and resized updates leave invalid indexes

- **Problem:** Delete removes only the primary index; an update whose
  encoded size changes relocates its record without updating indexes.
- **Location:** `sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/storage/engine/StorageEngine.java`,
  `deleteRecord` (220-232), `updateRecord` (203-217);
  `DataPageManager.java`, `updateRecord` (117-140).
- **Evidence:** `deleteRecord` explicitly leaves secondary-index removal
  as TODO. On a size change, `DataPageManager.updateRecord` frees the
  old slot and calls `insertIntoPage` but discards the new location;
  `StorageEngine.updateRecord` puts the old location back. The Entry
  service's type/target lookups use those indexes.
- **Impact:** Deleted data can be encountered through type/target
  queries, and updated records can disappear or resolve to a reused
  slot. These defects predate the cache commit but affect the active
  backend.
- **Required fix:** Update every affected live-record index on delete
  and relocation; keep index changes aligned with successful writes.
- **Acceptance test:** With cache disabled, insert neighboring
  records, delete one, change another's data to larger and smaller
  encoded sizes, and assert ID, type/relId, target, and all-record
  queries return exactly the surviving values. Close/reopen the file
  and repeat those assertions.

### 3. High: Failed update exposes data that storage did not commit

- **Problem:** Three service updates mutate their existing Entry before
  calling storage, without restoring it if storage fails.
- **Location:** `sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/service/EntryServiceCore.java`,
  `updateData` (408-424), `withdraw` (451-468), and
  `updateWithdrawReason` (494-510).
- **Evidence:** `current.setData`, `setWithdrawnBy`, and
  `setWithdrawalReason` precede `storage.updateRecord`. On IOException,
  the methods throw but do not undo these assignments. With in-memory
  caching, `MemoryCacheService.put` (150) retains the same mutable
  `EntryPersisted` reference.
- **Impact:** A failed write can change the caller's Entry or a cache
  hit while the persisted value remains old.
- **Required fix:** Prepare a separate candidate, commit it through
  storage, and only then publish the new state to wrappers and cache.
- **Acceptance test:** Inject IOException from `updateRecord` separately
  for each method with cache enabled and disabled. Assert an exception,
  unchanged prior Entry/cache state, unchanged storage, and a correct
  later successful update.

### 4. High: Existing query results are not independent snapshots

- **Problem:** Different returned wrappers can reference the same
  mutable cached record, so updating one changes an earlier result.
- **Location:** `EntryServiceCore.java`, `query` (66-74, 102-106),
  `queryAll` (137-145, 174-179), `queryById` (256-270),
  `updateData` (409), `withdraw` (452-453);
  `service/cache/MemoryCacheService.java`, `put` (143-153).
- **Evidence:** Query methods wrap `EntryPersisted` directly; the cache
  stores and returns the same object. Mutations alter that object in
  place. `Entry.java` and `EntryService.query/queryAll` promise
  independent, read-only query snapshots, not merely unmodifiable
  outer lists.
- **Impact:** A previously retained result changes after a later
  update/withdrawal through another wrapper.
- **Required fix:** Give query callers detached snapshots while
  preserving the pending Entry's documented transition after execute.
- **Acceptance test:** Obtain two results for one record, retain the
  first, update/withdraw through the second, and verify the first
  remains unchanged while a fresh lookup shows the committed change.
  Check defensive JSON and Date values.

### 5. High: Active cache entries remain valid after their expiry

- **Problem:** Cache hits return results that were active when cached
  without accounting for time passing.
- **Location:** `EntryServiceCore.java`, `query` (65-75 versus 85-100),
  `queryAll` (136-146 versus 156-172), `queryLatestActive`
  (212-220 versus 230-242).
- **Evidence:** Miss paths apply the expiry/state filter; hit paths
  return cached entries directly. `QueryKey` contains explicit query
  options but no validity deadline, and mutations are the only
  invalidation trigger. The current `EntryService` contract defines
  active as unexpired.
- **Impact:** An expired Entry can remain in default target/all
  results or be returned as latest active; if another active Entry
  exists, latest may fail to fall back to it.
- **Required fix:** Expire/revalidate time-sensitive cached results
  and recompute a latest-active answer when its winner expires.
- **Acceptance test:** With in-memory cache enabled, populate default
  query, queryAll, and latest-active results; advance beyond one
  Entry's expiry. Assert default results exclude it, latest falls
  back to a still-active Entry or becomes empty, and
  `includeExpired` results remain correct. Use controlled time if
  feasible. A successful cached empty result is not itself defective.

### 6. High: Concurrent cache hits mutate access-order maps under read locks

- **Problem:** `get` on the access-order LRU maps writes their linked
  ordering, yet multiple threads may call it under a shared read lock.
- **Location:** `sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/service/cache/MemoryCacheService.java`,
  constructor (65, 83), `getById` (92-99),
  `getByTypeAndRelId` (102-114), `getQueryResult` (117-140).
- **Evidence:** Both `LinkedHashMap`s use `accessOrder=true`; each hit
  changes internal ordering. `lock.readLock()` allows simultaneous
  hits to mutate the same map. This violates the cache's documented
  thread-safety and LRU behavior.
- **Impact:** Concurrent hits have undefined ordering and may damage
  map/index consistency.
- **Required fix:** Exclusively synchronize accesses that change LRU
  order, or replace the maps with a suitable concurrent design.
- **Acceptance test:** Assert entry/query eviction order after hits;
  run concurrent hit and insert/evict operations, assert capacity,
  secondary-index consistency, and absence of failures. The existing
  `assertTrue(true)` concurrency smoke test is insufficient.

### 7. High: An in-flight read can recache pre-mutation data

- **Problem:** Cache invalidation is not coordinated with publishing
  results from an earlier storage read.
- **Location:** `EntryServiceCore.java`, `query` (83-100),
  `queryAll` (154-172), `queryLatestActive` (228-242),
  `queryById` (265-270), `queryByTypeAndRelId` (302-307);
  `withdraw` (458-465), `execute` (361-375), `delete`
  (540-547, 577-587).
- **Evidence:** A read obtains an old record, mutation commits and
  invalidates, and the read can then execute `put`/`putQueryResult`.
  Neither the service nor cache checks a generation or holds a shared
  read-to-publication/mutation lock. This is a specific possible
  interleaving, not a claim that a race test already ran.
- **Impact:** A subsequent lookup can use stale entry or ID-list cache
  state after a completed withdrawal, deletion, or insertion.
- **Required fix:** Prevent publication of a pre-mutation read after
  that mutation's invalidation, for both point and list results.
- **Acceptance test:** Latch a storage read before cache publication;
  commit withdraw, delete, and insert in separate tests; resume the
  read and assert subsequent lookups match committed storage.

### 8. High, pre-existing: Documented data-update conflict is not detected

- **Problem:** Two independent snapshots can overwrite one another
  without an expected-version comparison.
- **Location:** `EntryServiceCore.java`, `updateData` (384-425);
  `storage/engine/StorageEngine.java`, `updateRecord` (203-217).
- **Evidence:** `EntryService.updateData` explicitly promises
  optimistic-lock conflict behavior and an unchanged original Entry.
  `EntryPersisted` carries a `version`, but the service passes no
  expected version and storage overwrites without checking one.
- **Impact:** A stale `updateData` can silently replace a newer
  committed update on the active binary backend.
- **Required fix:** Atomically compare/update the stored version for
  data changes and only publish on success. Do not generalize this
  into unspecified cross-backend features.
- **Acceptance test:** Load independent snapshots, commit an update
  from the first, then attempt a conflicting update from the second.
  Assert the documented conflict, unchanged second snapshot, and
  persistence of the first value after cache invalidation/reopen.
  Exercise concurrent updates as well.

## C. Intentionally Deferred Features

- **INTENTIONALLY DEFERRED - NOT A CURRENT DEFECT:** Database storage.
  `DatabaseStorageService` documents that it is not yet implemented and
  deliberately throws `UnsupportedOperationException`; no current
  approved requirement demands JDBC support. Do not implement or
  require database tests in this rework.
- **INTENTIONALLY DEFERRED - NOT A CURRENT DEFECT:** Redis caching.
  `RedisCacheService` documents that it is not yet implemented and
  deliberately throws `UnsupportedOperationException`; the owner has
  clarified that reserved operations need not be implemented now. Do
  not implement or require Redis integration tests.
- Excluded Minecraft and platform adapters are not current features,
  defects, or acceptance targets.

## D. Unresolved Scope Questions

- **SCOPE UNCONFIRMED:** `EntryService.execute` says a new entry *may*
  override compatible active entries according to application-specific
  rules. No compatible-type rules are defined in the included code.
  Do not demand an override implementation or claim that one is
  correct until the owner supplies the rule.
- **SCOPE UNCONFIRMED:** Process-crash atomicity and recovery
  guarantees for the binary format were not established. Do not turn
  write-order observations into an unbounded transaction redesign.
- **SCOPE UNCONFIRMED:** The supported external Core construction API
  and shutdown/reopen ownership are not specified beyond current
  Javadocs. Defect 1 establishes broken initialization order on the
  active path; confirm the intended public construction shape before
  changing signatures or adding broad lifecycle APIs.

## E. Acceptance Checklist

1. The included binary Core initializes without reflective injection
   and supports a real execute/query path (defect 1).
2. Delete and both larger/smaller updates preserve all storage
   lookups before and after reopen with cache disabled (defect 2).
3. Failed data, withdrawal, and withdrawal-reason writes leave
   caller/cache/storage unchanged; successful writes publish after
   commit (defect 3).
4. Previously returned query snapshots remain fixed after another
   wrapper updates the record (defect 4).
5. Cached active results reflect expiry and latest-active fallback;
   explicitly included expired results remain correct (defect 5).
6. LRU hits/evictions are synchronized and tested with meaningful
   capacity/index assertions under contention (defect 6).
7. Controlled read/mutation interleavings cannot republish stale
   point or query results after commit (defect 7).
8. Stale data updates report the documented conflict without losing
   the winner's persisted value (defect 8).
9. Preserve existing cache-first routing, full query keys, sorted
   results, bounded capacities, valid secondary indexes, and
   successful empty-result caching. Run included-module tests and
   build without `clean`; provide exact command outcomes. Do not
   implement section C or invent section D requirements.

## F. Verification

- Inspected current Git status, `settings.gradle.kts`, recent Git
  history (`8869d37`, `e6dec3d`), README, existing handoff document,
  included API/Core sources and relevant tests. Git status before
  editing: `main...origin/main`, only `M docs/CURRENT_FAILURE.MD`.
- This audit rechecked the current source paths cited above. In the
  preceding audit, the command
  `.\gradlew.bat :sanctionmanager-api:test :sanctionmanager-core:test :sanctionmanager-core:build --offline --no-daemon --console=plain --rerun-tasks`
  succeeded in 13 seconds: API test `NO-SOURCE`, Core tests executed,
  Core build successful (six actionable tasks). **That command has not
  been re-run during this documentation-only revision.**
- No runtime failure-injection, clock-controlled expiry,
  latch-controlled race, database, Redis, or process-crash test has
  been executed in this revision. The defect conditions above are
  established by source-level paths; acceptance requires the tests
  listed per defect.
- Reconciliation: earlier Minecraft/platform build and listener
  findings are withdrawn; absence of Redis/database implementation
  is deferred, not a defect; bounded capacity, key parameterization,
  cache-first routing, sorting, and successful empty-result caching
  are not listed as failures. The earlier claim that an empty cached
  result becomes active solely as time passes is withdrawn. Override
  rules and crash guarantees remain unconfirmed, not rework tickets.
