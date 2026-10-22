# SanctionManager

SanctionManager is a platform-independent Entry lifecycle and persistence library, with Minecraft adapters for Spigot, BungeeCord and Velocity.

## Modules

```text
platform adapters -> sanctionmanager-minecraft -> sanctionmanager-core -> sanctionmanager-api
```

- API: Entry, EntrySnapshot, EntryService, Identifier, TypeRegistry, configuration, public exceptions and events.
- Core: lifecycle, business rules, transactions, package-private EntryRepository and schema migration.
- Minecraft: platform type identifiers, commands, message formatting and player name storage.
- Platform adapters: platform events and player operations.

Core does not depend on Minecraft APIs or platform objects. EntryRepository is internal and cannot bypass the business service.

## Entry operations

```java
CoreConfig config = new CoreConfig("jdbc:h2:file:./data/sanctions", CredentialsProvider.anonymous());
try (SanctionManagerCore core = new SanctionManagerCore(config)) {
    Identifier type = new Identifier("app:suspension");
    core.getTypeRegistry().register(type);
    core.initialize();

    EntryService entries = core.getEntryService();
    Entry entry = entries.create(targetUuid, type, executorUuid, null, "reason", new JsonObject());
    Optional<Entry> active = entries.queryLatestActive(targetUuid, type);
    entry.withdraw(executorUuid);
}
```

Create returns a committed Entry. Core assigns executingTime; lifecycle state derives from withdrawal, expiryTime and overriddenBy. Entry snapshots remain readable after deletion or Core shutdown.

The database uses a global `sm_entries` mapping, `sm_types`, `sm_active`, schema/transaction metadata and per-type `sm_data_*` tables. The type suffix is a deterministic encoding of the complete namespaced Identifier.

Listeners use standaloneevent-common 1.6's native registry. Business events publish synchronously after commit. Independent custom event channels are available through EventChannels.create().

Minecraft commands and platform listeners use EntryService directly. Message formatting reads EntrySnapshot and uses the global long Entry ID through `%id%`.

## Validation and architecture reports

- [Entry cleanup report](ENTRY_CLEANUP_REPORT.md): removed architecture, current API/database structure and validation.
- [Core implementation report](CORE_IMPLEMENTATION_REPORT.md): lifecycle, credentials, migration and transaction details, followed by the cleanup revision.
- [Storage Backends](docs/STORAGE_BACKENDS.md): SQLite, MySQL, and other backend configuration and usage.

Build with a supported Gradle JVM and the project's Java toolchains:

```shell
./gradlew clean build
```

Older architecture documents are historical records, not current API contracts.
