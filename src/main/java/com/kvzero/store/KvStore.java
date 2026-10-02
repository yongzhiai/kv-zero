package com.kvzero.store;

/**
 * LEARNER CORE #1 — in-memory engine (ConcurrentHashMap + lazy TTL).
 *
 * <p>Contract:
 * <ul>
 *   <li>{@link #put} / {@link #delete} update memory. The optional runnable is invoked inside the same
 *       per-key {@code compute}, after the new mapping is decided and before it is published.</li>
 *   <li>{@link #get}: if key missing → {@link GetResult#miss()}; if expired → remove it and return
 *       {@link GetResult#expiredRemoved()} (and run the optional runnable) so the engine can append
 *       AOF {@code DEL}; else hit.</li>
 *   <li>{@code expireAtEpochMs < 0} means no TTL.</li>
 * </ul>
 *
 * <p>Implement in {@link MemoryKvStore}. Keep get/put/delete off any heavy eviction maintenance.
 */
public interface KvStore {
  void put(String key, byte[] value, long expireAtEpochMs);

  /**
   * Same as {@link #put(String, byte[], long)}. {@code enqueueAof} runs inside this key's
   * {@code compute} when non-null.
   */
  void put(String key, byte[] value, long expireAtEpochMs, Runnable enqueueAof);

  GetResult get(String key);

  /**
   * Same as {@link #get(String)}. {@code enqueueAof} runs inside this key's {@code compute} only when
   * the entry is expired and removed.
   */
  GetResult get(String key, Runnable enqueueAof);

  /** @return true if a mapping was removed */
  boolean delete(String key);

  /**
   * Same as {@link #delete(String)}. {@code enqueueAof} runs inside this key's {@code compute} only
   * when a mapping is removed.
   */
  boolean delete(String key, Runnable enqueueAof);

  int size();
}
