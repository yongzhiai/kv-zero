# kv-zero

从 0 到 1 实现一个高性能进程内 K/V（教学动手仓）。

## 本周 MVP 范围

| 包含 | 不含（后置） |
|---|---|
| Java 21 + Maven | W-TinyLFU / 时间轮主动过期 |
| Netty HTTP（IO 线程 + 业务线程池） | HTTP/2 必选 |
| `ConcurrentHashMap` + **惰性 TTL** | 堆外 |
| **AOF**（`always` / `everysec`） | RDB / 混合持久化 |
| 值 = opaque bytes | maxmemory 硬顶 / 淘汰（下一阶段） |

## 分工

- **骨架（已提供）**：工程、配置、Netty HTTP、`KvEngine` 接线、行为规格测试、压测脚本占位。
- **你写核心**：
  1. [`MemoryKvStore`](src/main/java/com/kvzero/store/MemoryKvStore.java)
  2. [`FileAofJournal`](src/main/java/com/kvzero/aof/FileAofJournal.java)

详见 [`LEARNER.md`](LEARNER.md)。

## 快速开始

```bash
# 实现核心后：
mvn test
mvn -q exec:java -Dkv.http.port=8080 -Dkv.aof.fsync=everysec -Dkv.data.dir=./data

curl -X PUT "http://127.0.0.1:8080/kv/foo?ttlMs=5000" --data-binary "bar"
curl -i "http://127.0.0.1:8080/kv/foo"
curl -X DELETE "http://127.0.0.1:8080/kv/foo"
```

配置（系统属性 / 环境变量）：

- `kv.http.port` / `KV_HTTP_PORT`（默认 8080）
- `kv.data.dir` / `KV_DATA_DIR`（默认 `./data`）
- `kv.aof.fsync` / `KV_AOF_FSYNC`：`always` | `everysec`
- `kv.aof.file` / `KV_AOF_FILE`（默认 `appendonly.aof`）

## 性能门禁（第二周再打满）

- 目标：~10 万 QPS，P99 ≤ 10ms（本机 loopback）
- 占位脚本：`scripts/loadtest.sh`
