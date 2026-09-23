# kv-zero

从0到1实现一个简单的kv存储 (Implementing a simple key-value store from scratch).

`kv-zero` is a small, concurrency-safe key-value store written in Go. It ships
with an in-memory store, optional JSON snapshot persistence, and a minimal HTTP
REST API.

## Requirements

- Go 1.22+

## Project layout

```
cmd/kvserver        HTTP server entrypoint
internal/store      concurrency-safe KV store with JSON persistence
internal/httpapi    REST handlers over the store
```

## Build, test, run

```bash
make build   # go build ./...
make test    # go test ./...
make vet     # go vet ./...
make run     # start the HTTP server on :8080
```

Or directly:

```bash
go run ./cmd/kvserver -addr :8080 -data data/kv.json
```

Configuration can also come from environment variables: `KV_ADDR` (listen
address) and `KV_DATA` (snapshot file path).

## HTTP API

| Method | Path        | Description              |
| ------ | ----------- | ------------------------ |
| GET    | `/health`   | Liveness + key count     |
| GET    | `/kv`       | List all keys            |
| GET    | `/kv/{key}` | Fetch a value            |
| PUT    | `/kv/{key}` | Set a value (raw body)   |
| DELETE | `/kv/{key}` | Delete a key             |

### Example

```bash
curl -X PUT  localhost:8080/kv/greeting -d 'hello'
curl         localhost:8080/kv/greeting
curl         localhost:8080/kv
curl -X DELETE localhost:8080/kv/greeting
```
