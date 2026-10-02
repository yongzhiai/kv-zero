package com.kvzero.aof;

import java.io.IOException;

/**
 * LEARNER CORE #2 — AOF journal (append + replay + fsync policy).
 *
 * <p>Suggested line format (UTF-8, one command per line):
 * <pre>
 * PUT &lt;key&gt; &lt;expireAtEpochMs&gt; &lt;base64(value)&gt;
 * DEL &lt;key&gt;
 * </pre>
 * Keys must not contain whitespace. Use Base64 for values.
 *
 * <p>Semantics (locked with learner):
 * <ul>
 *   <li>{@code ALWAYS}: {@link #appendPut}/{@link #appendDel} must durable-fsync before return.</li>
 *   <li>{@code EVERYSEC}: append to file/buffer and return; background ~1s fsync.</li>
 * </ul>
 */
public interface AofJournal extends AutoCloseable {
  /**
   * Prepare a PUT record. Offer it inside the key's {@code compute}, then {@link AofOp#finish()}.
   */
  AofOp startPut(String key, byte[] value, long expireAtEpochMs);

  /** Prepare a DEL record. Offer it only when this call actually removes the key. */
  AofOp startDel(String key);

  void appendPut(String key, byte[] value, long expireAtEpochMs) throws IOException;

  void appendDel(String key) throws IOException;

  /** Load existing AOF into the store (startup). Missing file = no-op. */
  void replayInto(com.kvzero.store.KvStore store) throws IOException;

  @Override
  void close() throws IOException;
}
