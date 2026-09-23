#!/usr/bin/env bash
# Placeholder load test — fill in after MemoryKvStore + FileAofJournal are green.
# Target (week 2): ~100k QPS, P99 <= 10ms on loopback.
set -euo pipefail
HOST="${HOST:-127.0.0.1}"
PORT="${PORT:-8080}"
echo "TODO: drive GET/PUT against http://${HOST}:${PORT}/kv/... (wrk / vegeta / custom)"
echo "Baselines to compare later: empty Netty handler vs full stack vs pure CHM JMH"
