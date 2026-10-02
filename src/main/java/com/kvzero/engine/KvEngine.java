package com.kvzero.engine;

import com.kvzero.aof.AofJournal;
import com.kvzero.store.GetResult;
import com.kvzero.store.KvStore;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Skeleton wiring: memory-first, then AOF.
 *
 * <ul>
 *   <li>PUT/DELETE: apply memory → append AOF (ALWAYS blocks on fsync inside journal).</li>
 *   <li>GET: if lazy-expire removed the key → append AOF DEL, return empty.</li>
 * </ul>
 */
public final class KvEngine implements AutoCloseable {
  private final KvStore store;
  private final AofJournal aof;

  public KvEngine(KvStore store, AofJournal aof) {
    this.store = Objects.requireNonNull(store);
    this.aof = Objects.requireNonNull(aof);
  }

  public void put(String key, byte[] value, long expireAtEpochMs) throws IOException {
    requireKey(key);
    Objects.requireNonNull(value, "value");
    var op = aof.startPut(key, value, expireAtEpochMs);
    store.put(key, value, expireAtEpochMs, op::offer);
    op.finish();
  }

  public Optional<byte[]> get(String key) throws IOException {
    requireKey(key);
    var op = aof.startDel(key);
    GetResult result = store.get(key, op::offer);
    op.finish();
    if (result.expiredAndRemoved()) {
      return Optional.empty();
    }
    return result.value();
  }

  public boolean delete(String key) throws IOException {
    requireKey(key);
    var op = aof.startDel(key);
    boolean removed = store.delete(key, op::offer);
    op.finish();
    return removed;
  }

  public int size() {
    return store.size();
  }

  public void loadFromAof() throws IOException {
    aof.replayInto(store);
  }

  @Override
  public void close() throws IOException {
    aof.close();
  }

  private static void requireKey(String key) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("key must be non-blank");
    }
    if (key.chars().anyMatch(Character::isWhitespace)) {
      throw new IllegalArgumentException("key must not contain whitespace (AOF line format)");
    }
  }
}
