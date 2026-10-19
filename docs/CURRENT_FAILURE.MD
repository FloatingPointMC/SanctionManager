# SanctionManager 当前故障审查与交接

基线：2026-10-19，HEAD `dfe68c0`。结论：**REJECT**，仅针对下列当前可证实的问题。本文件完整替代旧报告；路径相对仓库根目录，行号均重新核对。源码推导出的触发路径不等于已运行故障复现。

## A. 当前范围和执行验证

`settings.gradle.kts` 只包含 `sanctionmanager-api`、`sanctionmanager-core`；Core 依赖 API。Core 可配置二进制存储、内存缓存或已实现的 Redis 缓存，Redis 客户端为 Gradle 声明且代码实际使用的 Lettuce `7.9.0.RELEASE`。数据库持久化明确暂缓；Minecraft 和 Platform/Spigot/Bungee/Velocity 项目已移除，不属于验收范围。自动覆盖、乐观锁在现行 `EntryService` 文档中保留未来实现。

审查前 `git status --short`：只有两个未跟踪的用户文件 `RedisCacheServiceOfflineTest.java`、`RedisCacheServiceTest.java`；未触碰。检查了旧报告、Gradle 配置、近期 Git 记录、API/Core 源码及测试。实际运行：

`.\gradlew.bat :sanctionmanager-api:test :sanctionmanager-core:test :sanctionmanager-core:build --offline --no-daemon --console=plain --rerun-tasks`

结果 **BUILD SUCCESSFUL in 38s**，6 个 actionable tasks；API test `NO-SOURCE`。Core 测试 XML：83 个测试，37 skipped，46 执行，0 failures/errors。37 个真实 Redis 测试因 `127.0.0.1:6379` 不可达而跳过；7 个离线 Redis 测试通过。未执行真实 Redis、重启回归、故障注入、可控时间或 latch 并发测试；不可将这些路径标为运行验证通过。

## B. 确认缺陷及验收条件

**CF-01 High：二进制文件跨实例重开后记录丢失。** `core/storage/engine/RecordCodec.java` 的 `mapTypeToId` (285-295) 将类型映射仅存在实例内；`mapIdToTypeIdentifier` (298-303) 无法解码新实例读取的旧 type ID。`StorageEngine.java` 的 `openExistingFile/loadIndexesFromPage` (94-157) 在解码失败后记录警告并跳过记录，故已提交记录重启后从 ID、目标、类型索引消失，违反当前二进制持久化用途。**修复：** 持久化可恢复的类型标识/映射并正确重建索引；明确旧格式兼容策略。**验收测试：** 多类型写入、关闭、新实例重开，核对 ID、目标、类型/relId、全量查询及后续插入。现有 `StorageEngineTest` 每次使用新文件，不覆盖此情形。

**CF-02 High：跨服务实例 relId 重复。** `EntryServiceCore.java` 的 `typeRelIdCounters` (43) 与 `execute` (369-395) 每实例从零计数，不读取既有记录；`StorageEngine.java` 的 `indexSecondaryIndexes` (353-359) 对 `(type,relId)` 用 `put` 覆盖旧位置。重开并再创建同类型记录时两个记录获得同一 relId，违反 `Entry.getRelId` 同类型唯一合同，按关系 ID 查询/删除错位。**修复：** 由存储协调或恢复持久化的每类型唯一分配并防止并发重复。**验收测试：** CF-01 修复后跨 Core 实例重开创建和并发创建，核对唯一 relId、全部查找及删除。现有测试未覆盖跨实例计数。

**CF-03 High：已落盘执行因 Redis 失败而以待执行对象报错。** `EntryServiceCore.execute` (392-414) 在 `storage.insertRecord` 成功后才调用 `cache.put`、`invalidateAllQueryResults`，最后才转换 wrapper；`RedisCacheService.execute` (564-576) 可抛 `ConnectionException`，此处只捕获 `IOException`。例如 Redis 断线时存储已有新记录而 wrapper 仍是 `EntrySnapshot`；重试同一对象再次插入。违反 `EntryService.execute` 持久化后同一对象转为已存状态的语义，造成重复记录。**修复：** 明确提交后缓存失败语义，保持 wrapper、存储和缓存失效一致，避免重复插入；不能简单忽略失效错误。**验收测试：** 在 put 和失效两处分别注入 Redis 故障，核对异常/返回、wrapper、记录数、恢复后查询；离线 Redis 测试未覆盖此集成情形。

**CF-04 High：并发旧读可在修改失效后发布旧缓存。** `EntryServiceCore.java` 的 `query` (88-107)、`queryAll` (160-180)、`queryLatestActive` (240-256)、`queryById` (282-290)、`queryByTypeAndRelId` (325-333) 在存储读取后才 put；`withdraw` (527-538)、`delete` (635-643) 提交后失效，两步之间无共享锁或版本校验。读线程先取旧记录，修改线程提交并失效，读线程随后 put，之后查询命中旧数据。`RedisCacheService` 类文档 (65-69) 也明确单条 Lua 原子性不解决此交错。**修复：** 对读、发布、修改失效施加跨操作协调，覆盖点查及结果列表。**验收测试：** latch 控制读取后/发布前，分别撤销、删除、新增，完成后后续查找必须与存储相符；现有测试未覆盖该竞态。

**CF-05 Medium：内存 LRU 并发命中未互斥。** `MemoryCacheService.java` 构造器 (69-89) 使用 `accessOrder=true` 的 `LinkedHashMap`；`getById/getByTypeAndRelId/getQueryResult` (99-147) 在共享 `readLock` 下调用会修改链表的 `get`。两个查找线程能同时写链表，LRU 顺序与索引一致性没有保证；这是已证实的错误同步方式，非声称已观测到损坏。**修复：** 为顺序修改提供互斥或使用正确的并发结构。**验收测试：** 命中后淘汰顺序、并发命中/插入后的容量、内容和二级索引断言；现有 `testConcurrentAccess` 的 `assertTrue(true)` 不证明一致性。

**CF-06 Medium：公共 Entry 泄漏可变执行时间。** `EntryPersisted.java` 的 `executingTime` Lombok getter (37-38) 返回内部 `Date`，`EntryWrapper.getExecutingTime` (61-64) 原样转发。调用者执行 `entry.getExecutingTime().setTime(...)` 会修改只读快照，并可能修改内存缓存共享记录的排序依据，违反 `Entry`“返回可变对象均防御复制”的合同。`query/queryAll` 已复制 Entry 对象，但仍不能阻止此 getter。**修复：** 构造与返回时防御复制时间值，隔离缓存共享对象。**验收测试：** 修改 pending、持久化、列表和点查所得日期，断言原对象、后续查询及排序不变；当前测试未覆盖。

**CF-07 Medium：内存缓存同 ID 替换留下旧关系索引。** `MemoryCacheService.put` (153-164) 覆盖 ID 时只添加新 `(type,relId)` 映射，不删除原映射；`getByTypeAndRelId` (110-120) 因而让旧关系错误命中新条目，违反 `ICacheService.put` 的双索引合同。直接对缓存 put 同 ID 不同关系可触发，不依赖数据库。**修复：** 替换时有条件清旧映射，保持淘汰/删除一致。**验收测试：** 替换前后旧关系 miss、新关系 hit，淘汰和按 ID 失效后均 miss；现有测试只覆盖插入及淘汰。

**CF-08 Medium：Core 正常关闭不释放文件存储。** `SanctionManagerCore.shutdown` (140-152) 注释了 `storageService.shutdown()`，只关闭缓存；`BinaryFileStorageService.shutdown` (178-181) 和 `StorageEngine.shutdown` (395-398) 存在却未从该路径调用。正常关闭留下 PageManager 文件通道，跳过关闭时同步与资源释放。**修复：** 经既有存储抽象执行关闭，并明确失败/重复关闭语义。**验收测试：** Core 写入、shutdown、核对后端关闭/句柄释放及新实例重新打开读取；目前 Core 服务测试不覆盖资源关闭。

**CF-09 Low：精确到期时 active 谓词分歧。** `EntryServiceCore.matchesStateFilter` (721-739) 用 `expiryTime.before(now)` 判过期；`queryLatestActive` (244-249) 要求 `expiryTime.after(now)`。当同一 `now == expiryTime` 时默认查询认为 active，latest 认为 inactive，两种查询合同不一致。方法内部各自调用 `new Date()`，尚无可控时间运行复现；源码谓词分歧可证。**修复：** 统一边界定义及缓存路径。**验收测试：** 控制同一 now，在到期前、相等、到期后断言两个查询一致。

汇总：**9 个确认缺陷：Critical 0、High 4、Medium 4、Low 1。**

## C. 旧报告八项逐项复核

| 旧编号/主张 | 最终状态 | 当前证据 |
|---|---|---|
| 1 初始化顺序/入口 | **已经修复** | `SanctionManagerCore.create` (66-69) 为公共入口，`initialize` (129-134) 先建 TypeManager。 |
| 2 删除/变长更新索引 | **已经修复** | `StorageEngine.deleteRecord` (321-330) 清索引，`updateRecord` (289-300) 使用新位置。跨重开故障 CF-01 是独立问题。 |
| 3 写失败污染原对象 | **已经修复** | `EntryServiceCore.updateData/withdraw/updateWithdrawReason` 创建候选并在 `storage.updateRecord` 成功后发布 (449-479、509-539、568-598)；CF-03 是新的提交后缓存错误。 |
| 4 查询快照共享 | **仍然存在（范围缩小）** | `query/queryAll` 已 `wrapWithDefensiveCopy` (717-719)，原先跨 wrapper 共享论据撤回；可变 Date 泄漏仍违反快照合同 (CF-06)。 |
| 5 过期缓存错误命中 | **已经修复** | `CacheService.getQueryResult` (115-135) 对含过期项回源，point 查询 (78-109) 同样回源；固定 TTL 不必需。到期边界另见 CF-09。 |
| 6 LRU 读锁 | **仍然存在** | access-order map 的 `get` 在共享锁下执行 (CF-05)；容量有界，不误报为无界。 |
| 7 并发失效后旧读回填 | **仍然存在** | 存储读取与缓存发布/失效没有共同版本检查 (CF-04)。 |
| 8 乐观锁 | **原判断不成立** | 现行 `EntryService` 文档明确保留乐观锁供未来使用，不可仅凭 version 字段要求实现。 |

八项统计：仍存在 3、已修复 4、不再适用 0、原判断不成立 1、无法确认 0。旧报告之外的 Minecraft/platform 编译及监听器主张**不再适用**（模块主动移除）；“Redis 尚未实现”**不再适用**（当前已实现）；“必须实现数据库”**原判断不成立**（所有者明确暂缓）。不将这些历史补充主张混入八项计数。

## D. Redis 审查、限制和暂缓项

`RedisCacheService` 当前使用 Lettuce，JSON 编解码记录 ID、类型/关系、UUID、时间、扩展数据、撤销/覆盖、version (617-692)；Lua 操作处理 ID/关系索引和查询列表 (135-248、309-405)，空结果与 miss 区分，查询失效使用 generation 轮换 (229-248、485-489)。`invalidateAll` 用 `SCAN`，不是 `KEYS` (460-473)；`CacheService.shutdown` (224-233) 调用 Redis `close` (499-516)；命令错误不伪装为 miss (564-576)。**这是源码结论，不是 Redis 服务器实测**。已确认的服务层提交后失败和并发回填分别见 CF-03、CF-04。

默认 Redis key 前缀固定 (RedisCacheService 123、273-299)，多个独立部署共用 Redis DB 时可能碰撞；**范围无法确认**：未找到必须支持该部署方式的批准合同，不列必修缺陷。Redis 本地无 TTL/容量依服务端策略，`ICacheService` 明示实现依赖服务器淘汰；Cluster 动态键限制已有文档 (76-77)，不擅自扩展范围。真实 Redis 的 Lua、认证、异常传播、连接重建、并发测试全部因服务不可用而**未独立验证**。二进制进程崩溃原子性要求也未确立，不列缺陷。`QueryKey` 包含查询显式过滤参数；`QueryOptions` 的 `[start,end)`、排序、成功空结果缓存存在可核代码；不因缺少固定 TTL 推断错误结果。

数据库持久化 **INTENTIONALLY DEFERRED — NOT A CURRENT DEFECT**；自动覆盖、乐观锁也明确保留未来工作。不得实施这些预留功能，不恢复已删除模块。

## E. 交接验收

Claude Code 须先通读本文，按 CF-01 至 CF-09 的触发条件分别编写能证明修复的测试；先解决二进制重开和关系 ID 唯一性，再解决提交/失效顺序与缓存隔离。真实 Redis 可用时补跑服务器集成与故障路径；未能运行则如实说明，不以离线测试代替。保持现有模块和公共边界，不实施暂缓功能或无关重构。运行 API/Core 测试与构建（不得 `clean`），逐项提供命令、跳过数、结果及工作区差异；没有证据不得宣称修复完成，修复后须重新独立验收。
