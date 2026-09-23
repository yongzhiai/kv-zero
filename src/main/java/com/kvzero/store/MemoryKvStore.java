package com.kvzero.store;

/**
 * LEARNER CORE #1 — implement this class.
 *
 * <p>Suggested approach: {@code ConcurrentHashMap<String, KvEntry>}, lazy expire on {@link #get}.
 * Do not implement W-TinyLFU / timing-wheel / maxmemory in MVP.
 */
public final class MemoryKvStore implements KvStore {
  // TODO(learner): ConcurrentHashMap<String, KvEntry> map = ...

  @Override
  public void put(String key, byte[] value, long expireAtEpochMs) {
    throw new UnsupportedOperationException("LEARNER: implement MemoryKvStore.put");
  }

  @Override
  public GetResult get(String key) {
    throw new UnsupportedOperationException("LEARNER: implement MemoryKvStore.get (lazy TTL)");
  }

  @Override
  public boolean delete(String key) {
    throw new UnsupportedOperationException("LEARNER: implement MemoryKvStore.delete");
  }

  @Override
  public int size() {
    throw new UnsupportedOperationException("LEARNER: implement MemoryKvStore.size");
  }
}
