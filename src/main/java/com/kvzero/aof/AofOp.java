package com.kvzero.aof;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * One AOF record. {@link #offer()} only enqueues it; the journal thread performs the file write.
 * Call {@link #offer()} inside the same {@code compute} that mutates the key, then {@link #finish()}
 * after that {@code compute} returns.
 */
public final class AofOp {
  final boolean put;
  final String key;
  final byte[] value;
  final long expireAtEpochMs;
  private final FileAofJournal journal;
  private final boolean waitDurable;
  private final CompletableFuture<Void> done = new CompletableFuture<>();
  private boolean queued;

  AofOp(FileAofJournal journal, boolean put, String key, byte[] value, long expireAtEpochMs, boolean waitDurable) {
    this.journal = journal;
    this.put = put;
    this.key = key;
    this.value = value == null ? null : Arrays.copyOf(value, value.length);
    this.expireAtEpochMs = expireAtEpochMs;
    this.waitDurable = waitDurable;
  }

  /** Enqueue this record. Must run on the thread that holds the key's {@code compute}. */
  public void offer() {
    journal.enqueue(this);
    queued = true;
  }

  /** {@code ALWAYS} waits until this record is fsynced. {@code EVERYSEC} returns immediately. */
  public void finish() throws IOException {
    if (!queued || !waitDurable) {
      return;
    }
    try {
      done.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted waiting for AOF fsync", e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IOException io) {
        throw io;
      }
      throw new IOException("AOF write failed", cause);
    }
  }

  void complete() {
    done.complete(null);
  }

  void fail(Throwable cause) {
    done.completeExceptionally(cause);
  }
}
