# LEARNER — 你要写的核心

合作模式对齐 `im-zero` / `distributed-id`：骨架已就绪，测试先红；你把下面两处实现到绿。

## 1. `MemoryKvStore`

文件：`src/main/java/com/kvzero/store/MemoryKvStore.java`

- 用 `ConcurrentHashMap<String, KvEntry>` 存数据。
- `put(key, value, expireAtEpochMs)`：`expireAtEpochMs < 0` 表示永不过期。
- `get(key)`：
  - 不存在 → `GetResult.miss()`
  - 已过期 → **删除**该 key，返回 `GetResult.expiredRemoved()`（引擎会因此写 AOF `DEL`）
  - 否则 → `GetResult.hit(bytes)`
- `delete` / `size` 按字面实现。
- **不要**在本类做 W-TinyLFU、时间轮、maxmemory。

验收：`MemoryKvStoreTest` 全绿。

## 2. `FileAofJournal`

文件：`src/main/java/com/kvzero/aof/FileAofJournal.java`

建议行协议（UTF-8，一行一条）：

```
PUT <key> <expireAtEpochMs> <base64(value)>
DEL <key>
```

- `ALWAYS`：每次 `append*` 写入后 `force(true)`（或等价 fsync）再返回。
- `EVERYSEC`：`append*` 只保证写到文件/页缓存即可返回；后台约 1s 一次 fsync。
- `replayInto(store)`：按序重放；缺文件则 no-op。
- `close`：停 ticker、最后一次 fsync、关文件。

写路径顺序（已由 `KvEngine` 固定，与你的锁定一致）：

1. 先改内存
2. 再 append AOF
3. `ALWAYS` 在 journal 内刷盘成功后，HTTP 才返回成功；`EVERYSEC` 不在热路径等刷盘

验收：`FileAofJournalTest` + `KvEngineLazyExpireAofTest` 全绿。

## 本周明确不做

- maxmemory 硬拒绝 / 淘汰 PK（你已确认要做成正经淘汰，放到下一阶段）
- 时间轮主动过期、W-TinyLFU、HTTP/2、堆外
