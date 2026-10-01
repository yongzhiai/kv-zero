package com.kvzero.store;

import java.util.concurrent.ConcurrentHashMap;

/**
 * LEARNER CORE #1 — implement this class.
 *
 * <p>Suggested approach: {@code ConcurrentHashMap<String, KvEntry>}, lazy expire on {@link #get}.
 * Do not implement W-TinyLFU / timing-wheel / maxmemory in MVP.
 */
public final class MemoryKvStore implements KvStore {
  // TODO(learner): ConcurrentHashMap<String, KvEntry> map = ...
  private  final ConcurrentHashMap<String,KvEntry> map =new ConcurrentHashMap<>();

  @Override
  public void put(String key, byte[] value, long expireAtEpochMs) {
    map.put(key,new KvEntry(value, expireAtEpochMs));
  }

  @Override
  public GetResult get(String key) {
    KvEntry kvEntry = map.get(key);
    if(kvEntry==null){
      return GetResult.miss();
    }
    if(kvEntry.isExpired(System.currentTimeMillis())){
      map.remove(key);
      return GetResult.expiredRemoved();
    }
    return GetResult.hit(kvEntry.value());
  }

  @Override
  public boolean delete(String key) {
    map.remove(key);
    return true;
  }

  @Override
  public int size() {
    return map.size();
  }
}
