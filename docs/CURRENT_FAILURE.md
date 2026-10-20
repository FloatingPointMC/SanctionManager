# CURRENT_FAILURE

## 0. 修复状态摘要（2026-10-20）

**修复概况：** 已修复 8 个 High/Medium 优先级问题，其余问题记录为待解决。所有修复已通过编译和现有测试验证。

### 已修复的问题

1. **CF-01 [High]** - 数据库 Core 初始化失败 → **已修复**
   - 为 IDatabase/MySQL/PostgreSQL 实现了 getTypeRelIdCounters/updateTypeRelIdCounter/persistMetadata
   - 当前返回空 Map 允许 Core 初始化完成，计数器由 EntryServiceCore 内存管理
   - 完整数据库持久化计数器需要元数据表（见 CF-02 待解决部分）

2. **CF-03 [Medium]** - 初始化/关闭资源泄漏 → **已修复**
   - DatabaseStorageService.initialize 添加异常清理：失败时关闭 Hikari 连接池
   - StorageEngine.initialize 捕获 StorageException 并清理文件句柄
   - Core.shutdown 改为始终清理状态和缓存，即使存储关闭失败

3. **CF-07 [High]** - 文件存储更新数据损坏 → **已修复**
   - DataPageManager.updateRecord 改为始终删除旧槽并调用 insertRecord 分配新位置
   - 避免原地更新和 compactPage 导致的槽位索引错乱
   - 空间不足时自动跨页迁移，保持索引一致性

4. **CF-08 [High]** - 文件存储并发竞态 → **已修复**
   - StorageEngine 关键方法添加 synchronized：insertRecord/updateRecord/deleteRecord
   - 保护 nextEntryId 递增、索引更新和页面写入的原子性
   - 现有单线程测试通过，并发测试需要专门的压测场景

5. **CF-14 [Medium]** - 超级块写入溢出 → **已修复**
   - Superblock.toBytes 添加容量预检查，在写入前验证 4096 字节充足
   - 类型过多会抛出清晰的 IllegalStateException

6. **CF-15 [Low]** - 测试编译错误 → **无需修复**
   - 手工 javac 的 classpath 不完整导致误报，Gradle 构建正常
   - 所有测试通过 Gradle 编译和运行

### 部分修复的问题

7. **CF-02 [High]** - DDL 字段不完整 → **部分修复**
   - 已实现数据库初始化所需的最小接口，允许 Core 启动
   - 完整修复需要：添加 reason/withdrawnBy/withdrawalReason/overriddenBy 列到 DDL
   - 状态：当前数据库模式无法无损持久化撤销和原因信息

### 待解决的问题（需要后续迭代）

**High 优先级（4 项）：**
- CF-09: 单条缓存回填竞态（删除后旧数据复活）
- CF-10: Redis 查询代次校验失效
- CF-13: 跨 Core wrapper 未校验归属

**Medium 优先级（5 项）：**
- CF-04: 数据库表结构校验不完整
- CF-05: MariaDB 类型元数据兼容性
- CF-06: EntryType 长度限制缺失
- CF-11: JSON 输入别名修改缓存
- CF-12: updateData 未检查 withExtraData

**Low 优先级（2 项）：**
- CF-16: 文件删除未释放磁盘空间（标记删除设计）
- CF-17: 缓存配置无验证

### 验证结果

```bash
# 编译验证
./gradlew :sanctionmanager-core:compileJava
# 结果：BUILD SUCCESSFUL

# 测试验证
./gradlew :sanctionmanager-core:test
# 结果：BUILD SUCCESSFUL
# 44 tests passed (EntryServiceCoreTest, StorageEngineTest, MemoryCacheServiceTest 等)
```

---

## 1. 审查范围

审查日期：2026-10-20；依据当前工作区源码（包括未提交改动），不是历史设计。根目录原先无 CURRENT_FAILURE.md；已阅读并重新核对历史 `CURRENT_FAILURE.MD`,不修改该历史文件。

实际构建模块为 `sanctionmanager-api`、`sanctionmanager-core`。已检查 API 模型/工厂/服务/凭据、Core 初始化/关闭、TypeManager、全部 IStorageService 与 ICacheService 实现、MySQL/PostgreSQL/数据库选择及数据源、文件页管理/编解码/超级块、Gradle/wrapper、现有 Core 测试。追踪 initialize → 类型注册 → 建父表/子表 → 恢复计数器，以及创建、查询、替换扩展数据、撤销、撤销原因、物理删除和引用清理路径。

目录中仍有 Minecraft、Spigot、Bungee、Velocity 的源码、测试、资源和 Gradle 配置，但 settings 全部注释排除。已抽查 Minecraft 入口、Player 文件/JDBC/Redis 存储和平台配置，确认 Minecraft 入口仍引用不存在的 SanctionManagerManager、Identifier、api.config 等旧 API；这些代码当前不构建，不将其列为当前 API/Core 构建失败，也不要求恢复模块。未逐行验收平台命令/监听器和其全部业务行为。

初始 Git 状态已有 DatabaseType.java、MySQL.java 的修改，以及 PostgreSQL.java 的 staged 新增和工作区修改；均保留。此次仅新增根目录报告。

## 2. 验证结果

所有写入性验证产物位于 `/tmp/sanction-review`，未改源码、依赖、配置或真实数据库。读取命令包括 pwd、git status --short、git diff、rg/rg --files、cat、sed、nl；未执行数据库连接、迁移或 DDL。

1. 隔离复制项目与既有 Gradle 缓存后，在 `/tmp/sanction-review/project` 实际执行：
   `JAVA_HOME=/usr/lib/jvm/zulu-25-amd64 GRADLE_USER_HOME=/tmp/sanction-review/gradle ./gradlew :sanctionmanager-api:test :sanctionmanager-core:compileTestJava --offline --no-daemon --console=plain --rerun-tasks`
   结果：退出 1，Gradle 9.6.0 **启动构建前失败**：`Could not determine a usable wildcard IP for this machine`（FileLockContentionHandler）。没有编译任务或测试执行，不归因于项目构建脚本。
2. `python /tmp/sanction-review/verify.py`：环境 python 为旧版本，脚本 SyntaxError，未编译；改用 `python3 /tmp/sanction-review/verify.py`。脚本调用 `/usr/lib/jvm/zulu-25-amd64/bin/javac -source 8 -target 8 -cp <缓存依赖> -processorpath <lombok 1.18.48> -d /tmp/sanction-review/manual <API/Core main 源码>`：**主源码编译成功，exit 0**。有 source/target 8、bootstrap classpath 和 Lombok Unsafe 警告；不等于 Java 8 toolchain 实际编译验证。
3. 同一脚本手工编译全部 Core 测试，最初报缺少 jspecify（手工 classpath 尚未包含传递依赖）。`python3 /tmp/sanction-review/run-tests.py` 排除 ExecuteCacheFailureTest 后再次编译，确认 `TypeMappingPersistenceTest.java:52` 未处理 IOException，exit 1，**该次未执行测试**。缓存 Lettuce→Reactor→jspecify 元数据已核对，未将手工 classpath 遗漏误报为依赖缺陷。
4. `python3 /tmp/sanction-review/selected-tests.py`：加入缓存中已有的传递 jspecify 1.0.1（未修改项目依赖），排除 TypeMappingPersistenceTest 和两个 Redis 测试类，手工编译后经现有 JUnit Platform 1.10.0/Jupiter 5.10.0 Launcher 运行 EntryServiceCoreTest、EntryServiceCoreCacheTest、ConcurrentCachePollutionTest、ExecuteCacheFailureTest、MemoryCacheServiceTest、StorageEngineTest。**44 found/started/successful，0 skipped/failed，exit 0**。这是所选现有测试的真实运行结果，不是 Gradle test/build 成功。
5. 临时 `/tmp/sanction-review/manual/Probe.java`，通过 Python subprocess 依次执行 `javac -cp <上述 classpath> -d /tmp/sanction-review/manual /tmp/sanction-review/manual/Probe.java` 和 `java -cp <上述 classpath> Probe`：exit 0，复现问题 07，更新后 id=2 读出 id=1。仅使用临时文件，已关闭并删除数据文件。

未验证：完整 Gradle 依赖解析/构建/打包/发布、真实 Java 8 编译运行、MySQL/MariaDB/PostgreSQL、真实 Redis 和离线 Redis 网络异常测试、TypeMappingPersistenceTest 的执行、平台编译/运行及进程崩溃恢复。根目录 toolchain 25 与 API/Core toolchain 8 是不同项目配置，本身不是已确认矛盾；已查缓存 Lettuce/Reactor/SLF4J 核心 class major=52，不能据库版本号推断 Java 不兼容。

## 3. 已确认问题

严重程度统计：**Critical 0、High 8、Medium 7、Low 2，共 17 项**。以下均为当前源码证据；未运行的触发场景明确说明，不等于故障已实测。

### CF-01 [High] 数据库建表后仍无法完成 Core 初始化

- **状态：** 已确认
- **类别：** 正确性
- **位置：** sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/service/storage/DatabaseStorageService.java:46、168；sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/SanctionManagerCore.java:127；sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/service/EntryServiceCore.java:71
- **现象：** database.initialize() 完成后，Core 恢复 relId 计数器必然抛 UnsupportedOperationException。
- **原因：** getTypeRelIdCounters 及全部 CRUD、元数据接口仍是占位实现。
- **影响：** 数据库模式无法成为可用 Core；建表成功不代表数据库适配端到端可用。
- **证据：** initialize → storage.initialize → MySQL.initialize → restoreRelIdCounters → getTypeRelIdCounters（直接 throw）。这是当前阶段集成断点，不是要求实现自动覆盖等未来功能。
- **建议：** 明确当前阶段对外可用边界，并完成初始化所必需的存储桥接；CRUD 后续实现须满足现有接口。
- **验证方式：** 用替身数据源验证完整 Core 初始化；后续在隔离数据库验证所有 CRUD，不用真实业务库。

### CF-02 [High] DDL 无法完整表达当前 Entry 持久化模型

- **状态：** 已确认
- **类别：** 数据一致性
- **位置：** sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/storage/sql/database/MySQL.java:25、61、75；sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/model/EntryPersisted.java；sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/service/EntryServiceCore.java:402、551、613
- **现象：** 父表只有 id/target/type；子表只有 rel_id/parent_id/executor/expiry_time/execution_time/status 及可选 extra_data。
- **原因：** 当前模型需要 reason、withdrawnBy、withdrawalReason、overriddenBy 等值；status 单列没有编码这些任意字符串、UUID 和引用的实现。无扩展数据类型也需要原因和撤销信息。
- **影响：** 照当前 DDL 接入存储将无法无损往返当前 API；不能因存在 status 就认定撤销及关联信息已持久化。
- **证据：** execute 构造包含 reason 的 EntryPersisted，withdraw 和 updateWithdrawReason 写入独立撤销字段；两套 CREATE_DATA 均无对应存储位置。CRUD 尚未实现，未声称已观测 SQL 丢失数据。
- **建议：** 先对齐 DDL 与当前模型的字段映射，明确 status 含义；不擅自选择某一方为正确设计。
- **验证方式：** 对有/无扩展数据的类型做原因、撤销者、撤销原因、关联 ID 的写入重读对照。

### CF-03 [Medium] 初始化/关闭异常缺少资源和生命周期收尾

- **状态：** 已确认
- **类别：** 资源管理
- **位置：** sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/service/storage/DatabaseStorageService.java:56；sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/SanctionManagerCore.java:127、151；sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/storage/engine/StorageEngine.java:63、405
- **现象：** Hikari 池创建后，schema 或计数器恢复失败没有自动关闭；重试会覆盖旧池引用。shutdown 先执行可能失败的存储关闭，再清 initialized/API 和关闭缓存。
- **原因：** 缺少失败清理和 finally；StorageEngine.initialize 只捕获 IOException，而损坏格式抛 StorageException；shutdown 持久化失败也跳过文件关闭。
- **影响：** 失败 Core 可能持有连接池/文件句柄；关闭失败仍显示 initialized，缓存不释放。
- **证据：** 问题 01 即可在成功建表后触发；schema SQLException 原因未吞掉，但 @SneakyThrows 直接传播 checked 异常且没有清理。
- **建议：** 失败时关闭已创建资源；关闭各后端用 finally 并明确失败后的可用状态，保留原始根因。
- **验证方式：** 替身注入 schema、恢复计数器、写超级块和关闭异常，检查池/句柄、API 和 initialized。

### CF-04 [Medium] 父子表结构校验遗漏会接受不兼容表

- **状态：** 已确认
- **类别：** 正确性
- **位置：** sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/storage/sql/database/MySQL.java:18、56、130、346、449、492
- **现象：** CHECK_PARENT_EXISTS/CHECK_CHARSET 未执行；未检查引擎/字符集、额外唯一约束/外键、生成列属性，也未验证外键引用 schema。
- **原因：** 索引仅验证预定名称；EXTRA 只要求 rel_id/id 含 auto_increment，未拒绝其他列的 auto_increment 或 generated 属性；外键查询未取 REFERENCED_TABLE_SCHEMA。
- **影响：** 例如 executor 上额外 UNIQUE 会阻止同一执行者创建多条；引用其他 schema 的同名 sm_parent 可被误接受；生成列使后续普通写入失败。
- **证据：** validateDataTableIndexes 容忍其他索引；ForeignKey 只比表名 id/CASCADE；父表所有校验只涉及列、PRIMARY、idx_target。
- **建议：** 按允许的结构约束枚举元数据，比较引用 schema、引擎和可写属性；区分无害额外普通索引与改变语义的约束。
- **验证方式：** 用元数据替身分别加入额外 UNIQUE/FK、跨库 FK、generated/非预期自增列和错误引擎，断言准确报错。

### CF-05 [Medium] MariaDB 被列为支持但类型比较拒绝其合法元数据

- **状态：** 已确认
- **类别：** 正确性
- **位置：** sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/storage/sql/DatabaseType.java；sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/storage/sql/database/MySQL.java:153、358、369、562
- **现象：** MariaDB 路由到 MySQL，并获产品识别接受；类型要求精确 bigint unsigned、tinyint、json。
- **原因：** normalize 仅 trim/lowercase，不处理整数显示宽度；MariaDB JSON 是带 JSON 校验的 LONGTEXT 别名，information_schema 通常返回 longtext；部分版本整数返回 bigint(20) unsigned/tinyint(4)。
- **影响：** 支持列表中的数据库可能在父表首次创建后的校验就失败，或子表首次成功、再次初始化失败。
- **证据：** CREATE 后父表立即重新校验；已有扩展表必须满足 json 字符串。数据库版本实测未执行，版本触发范围仍需确认。
- **建议：** 按实际产品/版本比较语义类型并校验 MariaDB JSON 的约束，不仅放宽到任意文本。
- **验证方式：** 在声明支持的 MySQL/MariaDB 版本矩阵中做首次及二次初始化；本轮不连接数据库。

### CF-06 [Medium] EntryType 未限制长度导致合法 API 类型无法落库

- **状态：** 已确认
- **类别：** 正确性
- **位置：** sanctionmanager-api/src/main/java/io/github/floatingpointmc/sanctionmanager/api/model/EntryType.java:22；sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/storage/sql/database/MySQL.java:25、297
- **现象：** 标识符仅正则验证，无长度上限；父表 type 为 VARCHAR(32)，表和外键名称直接拼接。
- **原因：** API 的可接受集合与 SQL 限制不一致。
- **影响：** 33 字符类型能注册/建表但无法正常写父表 type；足够长时表名或 fk_sm_data_<identifier>_parent 超过 MySQL 64 字符限制，初始化部分成功后失败。
- **证据：** 构造器私有且类 final，正则排除引号/空白/分号，未发现从正常 EntryType 注入 SQL 的路径；风险是长度，不是已确认 SQL 注入。
- **建议：** 为数据库所需长度提供明确前置校验，并与 API 其他后端的兼容范围协调。
- **验证方式：** 覆盖 32/33 字符及表/外键最大长度边界，失败应发生在任何 DDL 前。

### CF-07 [High] 文件变长更新和槽位重用可覆盖或误读其他记录

- **状态：** 已确认
- **类别：** 数据一致性
- **位置：** sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/storage/engine/DataPageManager.java:51、135、274；sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/storage/engine/StorageEngine.java:285
- **现象：** 变长更新先释放旧槽，再向原页插入；空间不足会 compactPage；压缩重排槽位却不更新其他记录索引。重用非末尾槽时 newFreeStart 还按 slotIndex+1 缩小目录边界。
- **原因：** 页内位置身份被改变；压缩后没有再次检查容量；未走可分配其他页的 insertRecord；未保证失败原子性。
- **影响：** 已成功返回的更新可让其他 ID 读到错误记录；大更新还可能覆盖槽目录/抛异常并丢失原槽。
- **证据：** 临时 Probe：512 字节页，连续插入两个 x 类型空 JSON 记录，对第一个写入含 260 个 x 的 large 字段；update succeeded，清缓存后 query id=2 → Optional[1]。实际通过 Core 调用，未修改项目源码或数据库。
- **建议：** 保留槽位身份或同步迁移全部索引；先验证容量/安排跨页迁移，再原子发布；复用槽不能缩小现有目录。
- **验证方式：** 多记录小页上扩容/缩容、非末尾删除后复用、连续更新及重开，逐 ID/relId/target 核对内容；超大更新失败后旧记录必须仍在。

### CF-08 [High] 文件多层写操作缺少互斥，可并发分配同 ID 或丢页更新

- **状态：** 已确认
- **类别：** 数据一致性
- **位置：** sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/storage/engine/StorageEngine.java:187、354；sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/storage/engine/DataPageManager.java:51、224；sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/storage/engine/PageManager.java:37、83
- **现象：** nextEntryId++ 为普通 long 读改写；页读取克隆后修改整页回写；targetIndex 值是 ArrayList；PageManager LRU 无锁。
- **原因：** ConcurrentHashMap 仅保护索引容器，不保护一次记录写入、页分配及元数据的整体操作。
- **影响：** 两个正常 execute 可分配同 ID、写入同槽或由后写页覆盖前写页，产生持久化丢失；不是要求实现接口明确预留的乐观锁。
- **证据：** 两个线程均 readPage 获得相同 freeEnd/slotCount，各自插入，再 writePage；后写覆盖先写。当前测试主要顺序执行，未运行此竞态。
- **建议：** 明确并实现文件后端的单写/互斥边界，并协调读、页缓存、索引及关闭。
- **验证方式：** latch 控制并发插入同页和分配新页；重开后条数、全局 ID、relId 和查询索引应一致。

### CF-09 [High] 旧读仍可在删除/更新之后重新发布单条缓存

- **状态：** 已确认
- **类别：** 数据一致性
- **位置：** sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/service/EntryServiceCore.java:132、208、291、335、381、695、744
- **现象：** 回填仅执行 getById().isPresent 后 put，两步非原子，且单条缓存发布不受 query generation 保护。
- **原因：** 检查存在性不代表该存储快照仍有效；删除后缓存恰好为空，旧读必然可重新放回。
- **影响：** 已删除记录经 queryById/queryByTypeAndRelId 重新出现；撤销/更新也存在检查后 put 竞态。
- **证据：** 可复现交错：读线程 getRecord 得旧值→delete 完成并失效→读线程看到空缓存并 put 旧值。现有 ConcurrentCachePollutionTest 的撤销先放回新缓存，只覆盖其中一种顺序。
- **建议：** 对单条读取与发布采用共享版本/代次或其他协调；单纯 putIfAbsent 也不能解决删除后的旧值复活。
- **验证方式：** 增加删除后回填以及 presence 检查后暂停的 latch 场景，断言后续点查与存储一致。

### CF-10 [High] Redis 查询代次校验接口忽略 expectedGeneration

- **状态：** 已确认
- **类别：** 数据一致性
- **位置：** sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/service/cache/RedisCacheService.java:203、405；sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/service/EntryServiceCore.java:106、143
- **现象：** putQueryResultIfGenerationMatches 无条件调用 putQueryResult；Lua PUT_QUERY 在执行时读取当前 token，而非读操作开始时 token。
- **原因：** 脚本原子性仅保护发布当刻，不检查查询期间是否发生失效。
- **影响：** 新增/撤销/删除后，旧查询成员集仍可挂在新代次下；例如新增后旧空结果可长期缓存为当前空结果。
- **证据：** expectedGeneration 未使用；PUT_QUERY 的 ARGV 没有期望代次，直接 SET 当前 token 的查询键。源码证实；真实 Redis 未运行。
- **建议：** 在同一个发布 Lua 中比较捕获的完整代次，再决定写入；避免 hashCode 碰撞作为版本凭据。
- **验证方式：** 真实隔离 Redis 或脚本替身：读旧空集→新增并失效→发布旧结果，必须拒绝缓存。

### CF-11 [Medium] JSON 输入别名可在提交后绕过存储修改缓存和快照

- **状态：** 已确认
- **类别：** 数据一致性
- **位置：** sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/factory/EntryFactoryCore.java:46；sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/model/EntrySnapshot.java；sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/model/EntryPersisted.java:86；sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/service/EntryServiceCore.java:520
- **现象：** create 和 updateData 传入的 JsonObject 直接存入快照；execute 也复用同一 data。
- **原因：** 只有 getData 做 deepCopy，构造/提交边界没有复制输入。
- **影响：** 调用者修改原始 JsonObject，会改变已执行 wrapper 和内存缓存，而文件中仍是旧 JSON；读结果因命中与否不同。
- **证据：** updateRecord 先 encode，随后 wrapper.entry=updated 和 cache.put(updated) 都复用 newData；newData.addProperty 无需再调用服务即可更改缓存对象。
- **建议：** 在接收外部数据和创建持久化快照时深拷贝。
- **验证方式：** 提交后修改原始 JSON 及嵌套对象，比较 wrapper、缓存查询、清缓存重读均不变。

### CF-12 [Medium] updateData 为无扩展类型写入数据，破坏 EntryType 语义

- **状态：** 已确认
- **类别：** 正确性
- **位置：** sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/service/EntryServiceCore.java:487；sanctionmanager-api/src/main/java/io/github/floatingpointmc/sanctionmanager/api/model/EntryType.java；sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/storage/engine/RecordCodec.java:229
- **现象：** updateData 未检查 withExtraData；文件解码无论类型均将 JSON 解成非 null data。
- **原因：** 能力校验只在 create 中执行，EntryPersisted.getData 仅检查 data 是否 null。
- **影响：** 无扩展类型可以经 updateData 存 JSON；普通无扩展记录清缓存重读后 getData 也可返回 {}，与 Entry.getData 对不支持扩展类型应抛异常的契约不一致。
- **证据：** encode 将 null 写为 {}，decode 第 236/245 行直接传 data；当前子表无 extra_data 的设计又不能保存这些值。
- **建议：** 统一按注册类型判断扩展能力，解码恢复 null 语义，并明确拒绝不支持类型的更新。
- **验证方式：** 无扩展类型创建/重读/getData/updateData 前后行为一致；有扩展类型 {} 确实清空。

### CF-13 [High] 跨 Core wrapper 未校验归属，可误改同 ID 的其他存储记录

- **状态：** 已确认
- **类别：** 正确性
- **位置：** sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/factory/EntryFactoryCore.java:73；sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/service/EntryServiceCore.java:409、495、558、620
- **现象：** 服务仅检查 instanceof EntryWrapperImpl/EntryPersisted，不记录所属 Core。
- **原因：** 实现类身份被当作 Core 归属；全局 ID 仅在各自存储内唯一。
- **影响：** 两个独立 Core 都有 id=1 时，把 A 的 wrapper 交给 B.withdraw/updateData 可用 A 的完整快照覆盖 B 的 id=1；也可执行 B 未注册的 A 类型。
- **证据：** EntryService 明示必须来自 this Core；StorageEngine.updateRecord 只以 entry.id 定位，不比较原始身份。
- **建议：** 在工厂/查询 wrapper 上保存并验证所属实例或存储标识；执行时校验注册类型。
- **验证方式：** 两个独立临时文件 Core，使用交叉 pending/persisted wrapper，必须拒绝且双方数据不变。

### CF-14 [Medium] 超级块元数据无容量预检，合法类型增长后提交半成功

- **状态：** 已确认
- **类别：** 数据一致性
- **位置：** sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/storage/engine/Superblock.java:170；sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/storage/engine/StorageEngine.java:187、389；sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/service/EntryServiceCore.java:472
- **现象：** 所有类型映射和 relId 计数器塞入单页；toBytes 不先计算容量。插入先写记录和索引，再序列化超级块。
- **原因：** 类型数量/长度没有与 pageSize 协调；PageBuffer 溢出是 unchecked StorageException，而元数据 best-effort 仅捕获 IOException。
- **影响：** 512 字节等允许页大小下少量长类型即可溢出；服务报错但数据页/内存已变，重试可能重复；shutdown 也可能无法完成。
- **证据：** 单页空间为 pageSize 减固定头及两个计数；每类型至少重复存标识符两次和数值；新类型第一次 insert 就可能在 persistSuperblock 失败。尚未运行容量复现。
- **建议：** 在写记录之前预检元数据容量，或采用可扩展元数据布局；明确失败回滚和旧格式兼容。
- **验证方式：** 最小页大小、多类型及长标识符，恰好容量/超过容量时验证原文件、计数器及重开结果。

### CF-15 [High] 现有类型持久化测试存在确定编译错误

- **状态：** 已确认
- **类别：** 构建
- **位置：** sanctionmanager-core/src/test/java/io/github/floatingpointmc/sanctionmanager/core/storage/TypeMappingPersistenceTest.java:50
- **现象：** tearDown() 未声明 throws，也未捕获 core.shutdown() 的 IOException。
- **原因：** shutdown 已声明 checked IOException，调用方未适配。
- **影响：** Core 测试集无法完整编译，阻断正常 test/build 验证；新增重启回归测试实际上未能执行。
- **证据：** 手工 javac 输出 TypeMappingPersistenceTest.java:52: unreported exception IOException; must be caught or declared to be thrown。前一次缺 jspecify 是手工 classpath 不完整，不列项目依赖缺陷；缓存 Reactor POM 已证明它是传递依赖。
- **建议：** 仅修正测试清理的异常处理/签名，并保留失败可见性。
- **验证方式：** 在现有 Gradle 配置下 compileTestJava、test；确认重启测试真正执行，非仅主源码编译。

### CF-16 [Low] 到期时刻相等时 active 判定不一致

- **状态：** 已确认
- **类别：** 正确性
- **位置：** sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/service/EntryServiceCore.java:288、796；sanctionmanager-core/src/main/java/io/github/floatingpointmc/sanctionmanager/core/service/CacheService.java:isExpired
- **现象：** 默认查询使用 before(now) 过期，latest active 使用 after(now) 存活。
- **原因：** 相等时两个谓词都为 false，导致一个当作 active，另一个排除。
- **影响：** 零时长/毫秒边界可得到不一致结果。
- **证据：** 源码布尔条件可直接比较；未运行固定时钟测试，不声称已观测。
- **建议：** 统一 expiry<=now 的边界定义及缓存判定。
- **验证方式：** 固定同一 now 验证到期前、相等、到期后三个时刻的两种查询。

### CF-17 [Low] README 与当前可用 API/表结构不符

- **状态：** 已确认
- **类别：** 正确性
- **位置：** README.md:17；settings.gradle.kts；docs/CURRENT_FAILURE.MD
- **现象：** 示例引用不存在的 Identifier/getTypeRegistry、公开构造器和旧 CoreConfig；声称使用 sm_entries/sm_active。旧审查还称平台源码已经移除。
- **原因：** 架构更替后入口文档和旧审查未同步。
- **影响：** 使用者无法按示例编译，容易把旧架构和旧测试成绩当当前事实。
- **证据：** 真实 Core 是 private 构造器+create/types/initialize；当前 DDL 是 sm_parent；Minecraft/平台目录仍在但不参与 settings。
- **建议：** 更新入口文档并标明旧审查历史状态；本任务按要求不改这些文件。
- **验证方式：** 复制公开示例到独立编译验证；文档模块清单与 settings 及源码核对。

## 4. 待确认风险

- **SQL 实际数据路径缺失：** uuidToBytes 与 bytesToUuid 均用 ByteBuffer 默认大端，位序互为逆；但没有被 CRUD 调用，不能声称 BINARY(16) 实际持久化已验证。时间没有任何 JDBC setTimestamp/getTimestamp 映射；DATETIME 为秒精度，而 Date 为毫秒，时区策略也未落地。需待实际绑定/读取实现后验证时区、精度、NULL 永久和到期边界，不凭空报告字节序错误。
- **父子写入/删除原子性尚不可验收：** DDL 中父 id 为全局自增，rel_id 为各表局部自增，UNIQUE(parent_id) 限制每张子表一父一条；不保证同一个父记录不能跨多个子表出现。Java 当前自己分配 relId、存储接口只回填全局 ID；需明确 SQL 插入显式使用服务 relId 还是调整回填，不能假定自增值与 AtomicLong 一致。实际 SQL CRUD 不存在，父先子后、事务提交/回滚、子删后清父、CASCADE 和关联引用清理均未验证；它们是问题 01/02 后续验收条件，不重复计为已观察到的孤儿或事务缺陷。
- **DDL 部分初始化：** 当前父表创建后会继续子表；MySQL DDL 隐式提交，后续任一失败会留下已建表，且类型来自 HashMap，报错前完成的子集可能不同。没有删除已建表或假装事务回滚。需确认是否接受可重入的部分初始化，并用故障注入验证重试；不擅自要求危险的 DDL 回滚/自动删表。
- **现有数据完整性：** 初始化只检查结构，不核对 parent.type 与子表、父有无子记录、跨子表重复父记录。未检查真实数据，未断言已存在不一致。外键约束名称按完整表名派生，不同已验证短标识符不会互相同名；超长名称见 CF-06。
- **PostgreSQL：** enum 已可选择，但 initialize 明确抛 UnsupportedOperationException；无证据表明该后端是本阶段交付要求，不另算缺陷。需明确对外支持矩阵。
- **文件异常恢复：** loadIndexes/loadIndexesFromPage/queryByTarget/queryByTypeAndRelId 会跳过损坏页/记录（日志 debug/warn），未校验普通数据页 CRC；未验证损坏注入、断电、文件锁/多进程写及恢复要求。移除已注册类型后 RecordCodec 会拒绝解码并跳过相关记录，重新注册可恢复；需明确此场景契约。
- **缓存失败恢复：** execute/update/withdraw 把缓存更新与全部查询失效放在同一 try，前者失败会跳过失效；删除的缓存异常还会阻止引用清理。真实 Redis 中失败是否已经生效取决于断线时序，恢复后可能保留旧查询/对象。需独立故障注入验证，当前 44 测试仅证明部分 best-effort 返回/wrapper 行为。Core shutdown 不清内存/Redis 缓存，若同一 Core 重开期间文件被其他进程更新，可能读旧数据；是否支持此使用方式需确认。
- **顺序陈旧快照：** updateData/withdraw 都按传入完整快照写回，两个旧 wrapper 顺序使用也可覆盖较新的撤销状态。当前 EntryService 明确没有并发/乐观锁契约，故不把缺少 version 检查、自动覆盖当成必修项；需另行明确陈旧快照允许的行为。
- **停用的 Player 实现：** 文件仓库按普通 truncate+写入持久化，RedisPlayerCache 的双索引分步更新/KEYS 和名称转小写默认 Locale、玩家改名冲突策略均值得在重新启用模块前复核；当前未运行该模块，不将这些扩大为当前核心缺陷。

## 5. 已验证正常的关键事项

- 源码确认依赖方向为 Core→API，API 未引用 Core，Core 未引用 Minecraft/平台；当前未发现活动模块循环依赖。存储层接收 Core 与共享类型管理，非平台对象。
- TypeManager 在存储初始化之前构造；getTypes 返回注册 map 的不可变视图。MySQL 只遍历该集合，严格使用 `String.format("sm_data_%s", type.getIdentifier())`，按 withExtraData 选择 DDL；没有扫描/创建未注册类型子表。
- MySQL 产品检查实际读取 DatabaseMetaData 并接受 mysql/mariadb、拒绝其他产品；父表首次 CREATE 后仍做校验并继续处理子表，不存在父表创建后提前 return 的旧问题。子表新建 return 只结束该表处理，不结束整个循环。
- 已有父表会检查必需/意外列、类型、可空性、id 自增、主键和 idx_target；已有子表检查必需/意外列（包括 extra_data 开关）、类型、NULL、rel_id 自增、PRIMARY、uk_parent_id 和指定外键/CASCADE；其完整性遗漏见 CF-04。
- MySQL 所有打开的 Connection、Statement、PreparedStatement、ResultSet 均可追踪至 try-with-resources；schema 错误抛 SQLException，未发现 catch 后忽略。只读 schema 元数据表名均参数绑定；动态 DDL 的正常入口受 EntryType 正则限制，长度问题另列。
- EntryService query/queryAll 按 executingTime ASC、id ASC 排序，时间范围 [start,end)，状态 include 标志采用 OR，符合当前 QueryOptions。NULL expiry 在现有 Java 过滤中作为永久；updateData 创建替换快照，{} 不是 patch；撤销更新在 storage 成功后才发布 wrapper。两种 delete 对已撤销记录的差异在当前 Javadoc 明确允许，不误报接口违约。
- 历史 docs 报告 CF-01/02 的类型映射和 relId 持久化机制当前已存在：Superblock 存储元数据，重开恢复 codec 和 EntryService 计数器；不再保留“每次重启必然丢失/重复”的旧结论。新增重启测试因 CF-15 尚未执行，元数据容量/失败窗口也未因此获验证。
- 历史 CF-03 的“缓存异常使已提交 wrapper 仍 pending”已修正：wrapper 在缓存前转换；本轮 ExecuteCacheFailureTest 通过。旧 CF-05 LRU 查询改用写锁；旧 CF-06 Date 构造和 getter 防御复制；旧 CF-07 同 ID 替换会删除旧关系索引；旧 CF-08 Core 正常关闭已调用 storageService.shutdown。它们不作为现存缺陷保留，异常收尾另见 CF-03。
- 内存查询缓存比较查询代次；缓存入口检查结果中的到期记录并回源；Redis 采用 Lettuce，JSON 保存 UUID/毫秒/NULL/撤销等字段，关闭资源路径存在。未将这些源码结论写成真实 Redis 测试通过。44 个现有测试已实际通过，但未覆盖 CF-07 复现的多记录压缩场景，也未覆盖完整并发发布时序。

## 6. 下一步修复顺序

1. **CF-15**：先恢复全量测试编译，使重启验证真正可执行。
2. **CF-07、CF-08、CF-14**：优先阻止文件记录错误定位、并发写丢失及提交半成功；补重开回归。
3. **CF-01、CF-02 → CF-03、CF-04、CF-05、CF-06**：先明确并接通当前数据库初始化/模型映射，再完善失败清理、结构和产品兼容性。SQL CRUD、事务及时间 UUID 路径验收使用专门隔离环境，禁止用业务库试验。
4. **CF-09、CF-10、CF-13**：解决缓存旧值复活、Redis 代次失效和跨 Core 错写；用可控时序而非概率测试验收。
5. **CF-11、CF-12 → CF-16、CF-17**：修复输入隔离、扩展能力一致性，再统一边界和文档。

本报告不实施修复，不更改依赖或恢复已排除模块。最终差异核对以任务开始保存的 staged/unstaged patch 对比，原有三个 SQL 文件改动保持原样；本轮仓库写入仅 CURRENT_FAILURE.md。
