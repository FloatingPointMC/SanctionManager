# Binary File Storage Rules

## 1. Purpose and scope

This document defines the on-disk format and storage-engine rules for `BinaryFileStorageService`.

The storage engine is a platform-independent, file-backed persistence layer. It must store records and indexes on disk, use page-based random access, and avoid loading the complete dataset into memory for ordinary operations.

The implementation must follow this document. If existing public APIs impose additional constraints, preserve those APIs and implement the storage rules behind them. Do not change sanction business semantics to simplify storage.

`DatabaseStorageService` is outside the scope of this file format. Its storage operations must throw `java.lang.UnsupportedOperationException` until database support is implemented.

## 2. Design principles

1. **Disk is authoritative.** Persisted pages are the source of truth for records and indexes.
2. **Page-based I/O.** Access the file through pages, not by serializing the whole dataset into a single in-memory buffer.
3. **Persistent indexes.** Index nodes live on disk. In-memory structures may cache a bounded number of pages and a bounded amount of index metadata, but must not mirror all Entries.
4. **Stable addressing.** Every page has a stable page ID within a file generation. Page IDs are not raw byte offsets.
5. **Consistent updates.** Record and index changes must be recoverable as one logical operation using the write-ahead log.
6. **Explicit encoding.** All integer widths, byte order, field layouts, null markers, and length rules are defined below.
7. **Defensive validation.** Never trust lengths, page IDs, offsets, or counts read from disk.
8. **Business rules remain in the service layer.** The storage engine persists data and implements storage queries; it must not introduce Minecraft or platform dependencies.

## 3. File-level format

### 3.1 Files

The storage directory contains:

- `sanctions.dat` — primary data file containing the superblock, data pages, overflow pages, B+ tree pages, and allocation metadata.
- `sanctions.wal` — write-ahead log used for transaction recovery.

The implementation may use a configurable base filename, but the data file and its WAL must remain unambiguously associated. Do not add separate files for individual indexes unless the project explicitly requires that later.

The data file is created on first initialization. Existing files must be validated rather than silently reinitialized. An invalid or unsupported file must fail with a clear storage exception; it must never be treated as an empty database.

### 3.2 Byte order and primitive encodings

All multibyte fixed-width values use **big-endian** byte order.

| Type | Encoding | Size |
|---|---|---:|
| `byte` | two's-complement byte | 1 byte |
| `short` | signed big-endian integer | 2 bytes |
| `int` | signed big-endian integer | 4 bytes |
| `long` | signed big-endian integer | 8 bytes |
| UUID | most-significant 64 bits, then least-significant 64 bits | 16 bytes |
| byte sequence | exact bytes, length defined by surrounding field | variable |

Offsets and lengths must be validated before arithmetic or allocation. Use checked arithmetic when calculating `pageId * pageSize`, record sizes, or buffer capacities. Reject negative values where the format requires non-negative values.

### 3.3 Page size

The page size is selected when a new file is created and stored in the superblock. It is immutable for that file.

- The implementation must expose or internally centralize the configured default page size.
- The chosen page size must be a positive supported value and large enough for the superblock and page headers.
- On reopening, always use the page size stored in the file, not the current default configuration.
- A configuration mismatch must not silently reinterpret the file.
- Once a file exists, changing page size requires an explicit future migration tool; ordinary initialization must not rewrite the file.

All pages except the superblock are exactly `pageSize` bytes long.

### 3.4 Superblock

Page ID `0` is the superblock. It is reserved and is never allocated to a record or index.

The superblock uses the following fixed layout. Unused bytes are reserved and must be written as zero by the creator.

| Offset | Size | Field | Meaning |
|---:|---:|---|---|
| 0 | 8 | `magic` | ASCII bytes `SMBFILE1` |
| 8 | 4 | `formatVersion` | Current format version: `1` |
| 12 | 4 | `pageSize` | Page size in bytes |
| 16 | 8 | `fileGeneration` | Non-negative generation identifier |
| 24 | 8 | `filePageCount` | Number of allocated addressable pages, including page 0 |
| 32 | 8 | `nextEntryId` | Next global Entry ID to allocate |
| 40 | 8 | `recordRootPageId` | Root page ID of the global-ID B+ tree |
| 48 | 8 | `typeRelRootPageId` | Root page ID of the `(type, relId)` B+ tree |
| 56 | 8 | `targetRootPageId` | Root page ID of the target-UUID B+ tree |
| 64 | 8 | `freePageHead` | Head of free-page list, or `-1` |
| 72 | 8 | `walCheckpointLsn` | Last WAL LSN included in a completed checkpoint |
| 80 | 8 | `committedLsn` | Latest committed transaction LSN reflected by the superblock |
| 88 | 4 | `flags` | Feature flags; unknown mandatory flags cause open failure |
| 92 | 4 | `headerCrc32c` | CRC32C over bytes `[0, 92)` |
| 96 | remaining | reserved | Zero-filled for version 1 |

The initial empty file has `filePageCount = 1`, `nextEntryId = 1`, all root page IDs `-1`, and `freePageHead = -1`.

A root page ID is `-1` when its index is empty. A valid non-negative page ID must be less than `filePageCount`.

The implementation must validate the magic, supported version, page size, counts, root IDs, and CRC before using the file. The implementation must not trust `filePageCount` enough to read beyond the physical file length.

The superblock is updated only under the WAL protocol. Do not rely on an in-place superblock write alone for crash consistency.

### 3.5 Page addressing

Page IDs are zero-based. For every page ID `p > 0`:

`fileOffset(p) = p * pageSize`

Page ID `0` is the superblock at offset zero. The physical file length must be a multiple of `pageSize` after a completed operation. During recovery, an incomplete tail may exist and must be handled according to WAL recovery rules.

A page ID is valid only when `0 < pageId < filePageCount`. Validate multiplication overflow before calculating the byte offset. Use positional file I/O where available so independent page reads do not depend on a shared mutable file cursor.

## 4. Common page layout

Every non-superblock page begins with a 32-byte common header.

| Offset | Size | Field | Meaning |
|---:|---:|---|---|
| 0 | 1 | `pageType` | Page type code |
| 1 | 1 | `pageFlags` | Page flags; unknown mandatory flags are rejected |
| 2 | 2 | `headerLength` | `32` for version 1 |
| 4 | 8 | `pageId` | Must match the requested page ID |
| 12 | 8 | `pageLsn` | WAL LSN of the latest change to this page |
| 20 | 4 | `pageCrc32c` | CRC32C of the complete page with this field zeroed |
| 24 | 4 | `generation` | Page generation, incremented when a freed page is reused |
| 28 | 4 | `reserved` | Zero in version 1 |

Page type codes:

| Code | Type |
|---:|---|
| 1 | Data page |
| 2 | Overflow page |
| 3 | B+ tree leaf page |
| 4 | B+ tree internal page |
| 5 | Free page |
| 6 | Allocation/metadata page, only if required by the implementation |

A page's CRC must be verified before its contents are used. A CRC failure must be reported as corruption unless recovery can restore the page from a valid WAL record. A page must never be accepted merely because its page ID appears plausible.

All page modifications must update `pageLsn` and `pageCrc32c` as part of the logged page image or logged page changes.

## 5. Data pages and records

### 5.1 Data-page layout

A data page uses a slotted-page layout so variable-length records can be located and compacted without storing a fixed-size slot for every possible byte.

After the 32-byte common header, the data-page header is:

| Page offset | Size | Field |
|---:|---:|---|
| 32 | 2 | `slotCount` |
| 34 | 2 | `freeStart` — first byte after the slot directory |
| 36 | 2 | `freeEnd` — first free byte from the end of the page |
| 38 | 2 | `fragmentedBytes` |
| 40 | 4 | reserved, zero |

The slot directory begins at offset `44`. Each slot entry is 8 bytes:

| Slot field offset | Size | Field |
|---:|---:|---|
| +0 | 2 | `recordOffset` |
| +2 | 2 | `recordLength` |
| +4 | 2 | `slotGeneration` |
| +6 | 1 | `slotFlags` |
| +7 | 1 | reserved |

Slot flags include `FREE = 0` and `USED = 1`. Other values are invalid in format version 1.

Record bytes are stored from the end of the page toward the beginning. The slot directory grows forward from offset 44. `freeStart` must equal `44 + slotCount * 8`; `freeEnd` must not be less than `freeStart`. A used slot must point wholly within the record area and must not overlap another used slot's bytes.

Slot indices are local to a page and are not global record identifiers. Any external pointer to a record must include both page ID and slot index, plus generation information where required to detect stale references.

### 5.2 Record encoding

Each Entry is encoded as a versioned, length-prefixed record. All fields use big-endian encoding.

The record header is:

| Record offset | Size | Field |
|---:|---:|---|
| 0 | 4 | `recordLength`, including the complete record header and body |
| 4 | 2 | `recordVersion`, currently `1` |
| 6 | 2 | `recordFlags`, zero for version 1 |
| 8 | 8 | `id` |
| 16 | 8 | `relId` |
| 24 | 4 | `typeId` |
| 28 | 16 | target UUID |
| 44 | 16 | executor UUID |
| 60 | 8 | `executingTimeMillis` |
| 68 | 1 | `reasonPresent` (`0` or `1`) |
| 69 | variable | UTF-8 reason bytes if present |
| variable | 1 | `expiryPresent` (`0` or `1`) |
| variable | 8 if present | `expiryTimeMillis` |
| variable | 1 | `withdrawnByPresent` (`0` or `1`) |
| variable | 16 if present | withdrawn-by UUID |
| variable | 1 | `withdrawReasonPresent` (`0` or `1`) |
| variable | variable | UTF-8 withdrawal reason if present |
| variable | 1 | `overriddenByPresent` (`0` or `1`) |
| variable | 8 if present | overridden-by Entry ID |
| variable | 4 | `dataLength` |
| variable | `dataLength` | encoded JSON data bytes |
| final | 4 | CRC32C of all preceding bytes in this record |

**Variable-field encoding rule:** every present UTF-8 string is encoded as a 4-byte signed length followed by exactly that many UTF-8 bytes. The presence marker is separate from the length. A null string has presence `0` and no length; an empty string has presence `1`, length `0`. Lengths must be non-negative and must fit entirely within `recordLength`.

The table above is a logical field order. For implementation, place each string's 4-byte length immediately after its presence marker, before its bytes. The decoder must reject invalid presence markers, unsupported record versions, impossible lengths, truncated fields, and trailing or missing bytes.

`typeId` must be mapped by a stable storage-level type registry, not by Java enum ordinal. Assignments must remain stable for the lifetime of a format version. Unknown type IDs must be reported as unsupported/corrupt data rather than mapped to an arbitrary type.

The target UUID and executor UUID are mandatory. `id` and `relId` use signed 64-bit storage but must be validated against the API's permitted range. The storage engine must not infer an executor from a missing value.

The JSON data field stores the existing structured Entry data representation. Use a deterministic UTF-8 JSON encoding where practical. Do not silently discard unknown JSON fields during a read/write round trip.

### 5.3 Records that do not fit

If an encoded record does not fit in one data page, use overflow pages rather than truncating it. The data-page slot stores a small record descriptor identifying the overflow chain; distinguish this representation with a defined slot flag and document the descriptor layout in the implementation.

An overflow page has this body layout immediately after the 32-byte common header:

| Page offset | Size | Field |
|---:|---:|---|
| 32 | 8 | `nextPageId` (`-1` means end of chain) |
| 40 | 4 | `payloadLength` |
| 44 | remaining | payload bytes; unused tail bytes are zero |

The maximum payload length is `pageSize - 44`. The slot stores a 16-byte overflow descriptor in place of the record bytes:

| Descriptor offset | Size | Field |
|---:|---:|---|
| 0 | 8 | first overflow page ID |
| 8 | 4 | total encoded record length |
| 12 | 4 | record CRC32C copied from the assembled record trailer |

The slot flag for this representation is `OVERFLOW = 2`; `USED = 1` identifies an inline record. The slot's `recordLength` is 16 for an overflow descriptor. The record CRC is checked after reassembly. Validate the total assembled record length against the descriptor, every page ID, every payload length, chain termination, and cycles. No page may appear twice in the same chain.

### 5.4 Record relocation and slot reuse

Updating a variable-length record may relocate it to another slot or page. The storage engine must update the primary index and every index that stores a record locator in the same logical transaction. Reusing a slot increments `slotGeneration`. Reusing a page increments its page generation. Stale references must be detected rather than silently pointing to a different Entry.

Deleting an Entry must mark its slot reusable and release overflow pages when no longer referenced. Compaction may move record bytes within a page, but must preserve slot correctness and be logged.

## 6. Persistent B+ tree indexes

All indexes are persistent and stored as B+ tree pages in `sanctions.dat`. Nodes must be read from disk through the page manager; the implementation must not build a permanent in-memory copy of every tree node.

### 6.1 Required indexes

1. **Primary index:** `id -> record locator`. Keys are signed 64-bit Entry IDs, ordered numerically.
2. **Type/related-ID index:** `(typeId, relId, id) -> record locator`. Composite keys are ordered lexicographically by numeric `typeId`, then numeric `relId`, then numeric `id`. Including `id` permits deterministic ordering and duplicate related IDs.
3. **Target index:** `(targetUuid, executingTimeMillis, id) -> record locator`. UUID ordering is unsigned lexicographic ordering of its 16 encoded bytes; timestamp and ID use numeric ordering.

If the actual API requires an additional index to satisfy a required query efficiently, document and implement it. Do not add an index that is not needed by the API or query contract.

### 6.2 B+ tree invariants

- All keys and child page references are persisted.
- Internal nodes contain separator keys and child page IDs.
- Leaf nodes contain keys and values/record locators and are linked in key order.
- All leaves in one tree are at the same depth.
- Node splits, separator updates, root changes, merges, and page reclamation are logged.
- Search and range scans validate node type, key ordering, child IDs, and page checksums.
- A range scan follows leaf links only after validating the links and must terminate safely on corruption.
- Root page IDs are stored in the superblock and updated under WAL protection.
- Duplicate keys must follow the exact composite-key definition above; a key that is specified as unique must not be duplicated.

Define node capacity from the page size and the encoded key/value widths; do not hard-code a capacity that only works for one page size. Splits must preserve minimum occupancy according to the chosen B+ tree algorithm. Deletion must correctly rebalance or merge nodes and update leaf links.

### 6.3 B+ tree page layouts

The common 32-byte page header is followed by a 16-byte B+ tree header:

| Page offset | Size | Field |
|---:|---:|---|
| 32 | 1 | `treeId` (`1` primary, `2` type/related, `3` target) |
| 33 | 1 | `level` (`0` for leaf) |
| 34 | 2 | `keyCount` |
| 36 | 2 | `freeStart` |
| 38 | 2 | `freeEnd` |
| 40 | 8 | `parentPageId` (`-1` for root) |

The body starts at offset 48. For version 1, each index uses fixed-width keys and values, so node capacity can be calculated from `pageSize`; no variable-length key compression is used.

**Leaf entries** are stored in key order as fixed-width key bytes followed by a 16-byte record locator. The locator layout is `dataPageId: long`, `slotIndex: unsigned short`, `slotGeneration: unsigned short`, `pageGeneration: int`. Leaf pages have an additional 8-byte `nextLeafPageId` immediately after the B+ tree header; `-1` means no next leaf. The first entry therefore begins at offset 56. Unused bytes are zero. `freeStart` must equal `56 + keyCount * (keyWidth + 16)` for leaf pages.

**Internal pages** store `keyCount + 1` child page IDs and `keyCount` separator keys. The body is: first child page ID (`long`), then repeated separator key bytes and right-child page ID (`long`). The first key begins at offset 56, after the first child ID. `freeStart` must equal `56 + keyCount * (keyWidth + 8)`. Internal pages have no leaf link. `freeEnd` equals `pageSize`; reserved body bytes are zero.

Key widths are:
- Primary tree: 8 bytes (`id`).
- Type/related tree: 20 bytes (`typeId` as 4 bytes, `relId` as 8 bytes, `id` as 8 bytes).
- Target tree: 32 bytes (16-byte UUID, `executingTimeMillis` as 8 bytes, `id` as 8 bytes).

The tree ID and page type must agree. All keys in a node must be strictly ordered. Every internal node's child count must equal `keyCount + 1`. Child pages must have the same tree ID and a level exactly one less than their parent. The root has no parent; parent references must be updated during splits, merges, and root changes.

### 6.4 Record locators

A locator must identify the record's storage location without relying on a Java object address. Its 16-byte encoding is:

| Offset | Size | Field |
|---:|---:|---|
| 0 | 8 | `dataPageId` |
| 8 | 2 | unsigned `slotIndex` |
| 10 | 2 | unsigned `slotGeneration` |
| 12 | 4 | `pageGeneration` |

If an overflow descriptor is used, the locator points to the descriptor's slot; the descriptor points to the overflow chain. Validate page and slot generations before dereferencing a locator.

## 7. Page allocation and free pages

Page IDs are allocated monotonically by extending `filePageCount`, or reused from the free-page list. Page `0` is never free.

A free page has the common header with type `FREE` and a defined next-free-page ID in its body. `freePageHead` in the superblock points to the first free page or `-1`.

When reusing a page:
1. Remove it from the free list transactionally.
2. Increment its generation.
3. Initialize its new page type and contents.
4. Log the change before writing the page.
5. Update all owning references atomically through the transaction protocol.

Do not reuse a page while any live index, record, or overflow chain still references it. Detect invalid free-list pointers and cycles.

## 8. Write-ahead log and recovery

### 8.1 WAL file and record layout

The WAL begins with a 32-byte file header:

| Offset | Size | Field |
|---:|---:|---|
| 0 | 8 | ASCII magic `SMBWAL01` |
| 8 | 4 | WAL format version (`1`) |
| 12 | 4 | reserved, zero |
| 16 | 8 | file generation; must match the data-file superblock |
| 24 | 4 | CRC32C over bytes `[0, 24)` |
| 28 | 4 | reserved, zero |

Each WAL record has a 32-byte header followed by payload and a 4-byte trailer CRC32C:

| Record offset | Size | Field |
|---:|---:|---|
| 0 | 4 | `recordLength`, including header and trailer |
| 4 | 2 | `recordType` |
| 6 | 2 | flags, zero for version 1 |
| 8 | 8 | monotonically increasing `lsn` |
| 16 | 8 | `transactionId` |
| 24 | 4 | `payloadLength` |
| 28 | 4 | reserved, zero |
| 32 | variable | payload |
| `recordLength - 4` | 4 | CRC32C over all preceding record bytes |

`recordLength` must equal `32 + payloadLength + 4`. Records must be appended sequentially. LSNs must increase strictly. The CRC must be checked before interpreting payloads.

Version 1 record types are: `1 BEGIN`, `2 PAGE_IMAGE`, `3 COMMIT`, `4 ABORT`, `5 CHECKPOINT`. A `PAGE_IMAGE` payload consists of `pageId: long`, `imageKind: byte` (`0` before-image, `1` after-image), three reserved zero bytes, and exactly `pageSize` image bytes. The page image must have a valid page ID, page header, and checksum once applied. A `CHECKPOINT` payload contains `checkpointLsn: long` and `committedLsn: long`.

Use a consistent undo/redo protocol: log before-images and after-images for modified pages; force the WAL before writing corresponding data pages. On recovery, redo after-images belonging to committed transactions and undo before-images for transactions without a valid commit record, in an order that restores a consistent state. A commit record must be forced to stable storage before reporting success. Do not acknowledge a transaction until its commit record is durable.

### 8.2 Commit and recovery

- Append WAL records before writing affected data pages.
- Force the WAL to stable storage before acknowledging commit.
- Persist dirty pages only after their WAL records are durable.
- On startup, validate WAL records and recover committed transactions according to the chosen redo/undo strategy.
- Ignore only an incomplete trailing WAL record that can be unambiguously identified as a torn tail; corruption in the middle of the log must fail safely.
- Checkpoint only after the data pages and superblock state represented by the checkpoint are durable.
- Recovery must be idempotent: running it again must not corrupt the file.

If full crash-safe transaction support cannot be completed in the current task, do not claim it is implemented. Keep the format and implementation internally consistent and report the missing guarantee explicitly.

## 9. Page cache and memory limits

A page cache is permitted, but it must have a fixed configurable maximum size or another explicit bounded eviction policy.

- Cache pages by page ID and generation.
- Dirty pages must not be evicted before their WAL dependencies are durable.
- Writes invalidate or update cached pages consistently.
- Closing the storage service flushes required state and releases resources.
- Queries must not depend on a full in-memory collection of Entry objects.
- Do not eagerly load all data pages or all index nodes at startup.
- Metadata such as root page IDs, the page count, and a bounded page cache may be held in memory.

## 10. Query semantics and service boundary

`BinaryFileStorageService` must obey `IStorageService` and the business semantics enforced by `EntryServiceCore`.

- Use persistent indexes to locate candidate records.
- Apply all required filters and ordering exactly.
- Use half-open time ranges `[start, end)` where required by the API.
- Do not silently change null handling, withdrawal semantics, expiration semantics, or override behavior.
- Keep Minecraft, Bukkit, Paper, Spigot, BungeeCord, Velocity, Cloud, Player, CommandSender, and Adventure types out of the core storage format and core module.
- The storage engine persists data; it must not send messages or perform Minecraft actions.

The query result may be materialized in memory as the method's return value. The prohibition is against retaining a complete dataset as an internal mirror for future queries.

## 11. Validation and corruption handling

Validate all data read from disk, including:
- file magic, version, page size, and superblock checksum;
- physical file length and page count;
- page IDs, page types, header lengths, and page checksums;
- slot counts, slot boundaries, record offsets, and record lengths;
- UTF-8 field lengths and presence markers;
- record checksum and record version;
- B+ tree key order, child pointers, leaf links, and root references;
- free-list pointers and overflow chains;
- WAL record lengths, LSN order, checksums, and transaction state.

Never trust a length before checking it against both the enclosing record/page boundary and safe allocation limits. Avoid unbounded allocations derived directly from disk data.

If corruption cannot be repaired using valid WAL data, fail with a clear exception and preserve the original files for diagnosis. Do not silently initialize a new empty store over a corrupt existing store.

## 12. Compatibility and evolution

`formatVersion` identifies the on-disk format. Any incompatible layout change requires a version increment and an explicit migration strategy. Version 1 readers must reject unknown mandatory feature flags and unsupported record versions.

Reserved fields must be written as zero. Do not assign new meanings to reserved fields without a versioning decision.

## 13. Required invariants

The implementation and tests must enforce all of the following:

1. Page 0 is always the superblock and is never allocated.
2. Every valid page ID maps to exactly one page-sized region.
3. Every page's stored ID matches its physical page ID.
4. Page and record checksums are validated before data is trusted.
5. Every live Entry has exactly one primary-index entry.
6. Every secondary-index entry points to the corresponding live Entry record.
7. No live index or record points to a free page.
8. A page or slot generation changes when its storage location is reused.
9. Insert, update, withdrawal, and delete operations keep records and indexes consistent.
10. WAL durability precedes data-page persistence and transaction acknowledgement.
11. Recovery is idempotent under the documented recovery algorithm.
12. Normal queries read persisted records through page/index access, not through a full in-memory dataset mirror.
13. Closing and reopening the file preserves all committed records and index state.
14. Invalid or corrupt data fails explicitly rather than returning fabricated records.

## 14. Implementation and testing requirements

Implement this specification in `BinaryFileStorageService` and its storage helpers. Keep the code aligned with `IStorageService` and `EntryServiceCore`; do not alter business rules to make storage easier.

Tests must cover:
- creating a new file and reopening an existing file;
- persistence of records and indexes across restart;
- primary and secondary index lookup and range scans;
- insertion, variable-length updates, relocation, deletion, and page reuse;
- page and record boundary conditions;
- overflow chains for oversized records;
- checksum failures, truncation, invalid pointers, and malformed lengths;
- WAL recovery and repeated recovery if WAL support is implemented;
- bounded caching and the absence of a full-dataset in-memory mirror;
- `DatabaseStorageService` operations throwing `UnsupportedOperationException`.

Use temporary files and directories in tests. Never overwrite user data.

## 15. Workspace safety

Preserve all existing tracked, untracked, and uncommitted work. Never run `git reset --hard`, `git clean`, or other destructive cleanup commands. Do not run `gradle clean`. Do not delete tests, weaken assertions, or revert the entire workspace. Make only task-related changes.

When implementing, update this document if a necessary design detail changes. The implementation and specification must agree; do not leave conflicting field layouts or multiple competing encodings.
