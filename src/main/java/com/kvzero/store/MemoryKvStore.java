package com.kvzero.store;

import java.util.concurrent.ConcurrentHashMap;

/**
 * LEARNER CORE #1 — implement this class.
 *
 * <p>Suggested approach: {@code ConcurrentHashMap<String, KvEntry>}, lazy expire on {@link #get}.
 * Do not implement W-TinyLFU / timing-wheel / maxmemory in MVP.
 */
public final class MemoryKvStore implements KvStore {
  private final ConcurrentHashMap<String, KvEntry> map = new ConcurrentHashMap<>();

  @Override
  public void put(String key, byte[] value, long expireAtEpochMs) {
    put(key, value, expireAtEpochMs, null);
  }

  @Override
  public void put(String key, byte[] value, long expireAtEpochMs, Runnable enqueueAof) {
    map.compute(key, (k, existing) -> {
      KvEntry entry = new KvEntry(value, expireAtEpochMs);
      if (enqueueAof != null) {
        enqueueAof.run();
      }
      return entry;
    });
  }

  @Override
  public GetResult get(String key) {
    return get(key, null);
  }

  @Override
  public GetResult get(String key, Runnable enqueueAof) {
    GetResult[] out = new GetResult[1];
    map.compute(key, (k, existing) -> {
      if (existing == null) {
        out[0] = GetResult.miss();
        return null;
      }
      if (existing.isExpired(System.currentTimeMillis())) {
        out[0] = GetResult.expiredRemoved();
        if (enqueueAof != null) {
          enqueueAof.run();
        }
        return null;
      }
      out[0] = GetResult.hit(existing.value());
      return existing;
    });
    return out[0];
  }

  @Override
  public boolean delete(String key) {
    return delete(key, null);
  }

  @Override
  public boolean delete(String key, Runnable enqueueAof) {
    boolean[] removed = {false};
    map.compute(key, (k, existing) -> {
      if (existing == null) {
        return null;
      }
      removed[0] = true;
      if (enqueueAof != null) {
        enqueueAof.run();
      }
      return null;
    });
    return removed[0];
  }

  @Override
  public int size() {
    return map.size();
  }
}
