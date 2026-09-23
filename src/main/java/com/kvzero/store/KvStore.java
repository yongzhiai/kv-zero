package com.kvzero.store;

/**
 * LEARNER CORE #1 — in-memory engine (ConcurrentHashMap + lazy TTL).
 *
 * <p>Contract:
 * <ul>
 *   <li>{@link #put} / {@link #delete} update memory only (AOF is owned by {@code KvEngine}).</li>
 *   <li>{@link #get}: if key missing → {@link GetResult#miss()}; if expired → remove it and return
 *       {@link GetResult#expiredRemoved()} so the engine can append AOF {@code DEL}; else hit.</li>
 *   <li>{@code expireAtEpochMs < 0} means no TTL.</li>
 * </ul>
 *
 * <p>Implement in {@link MemoryKvStore}. Keep get/put/delete off any heavy eviction maintenance.
 */
public interface KvStore {
  void put(String key, byte[] value, long expireAtEpochMs);

  GetResult get(String key);

  /** @return true if a mapping was removed */
  boolean delete(String key);

  int size();
}
