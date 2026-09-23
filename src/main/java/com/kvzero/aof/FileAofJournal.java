package com.kvzero.aof;

import com.kvzero.config.AofFsyncMode;
import com.kvzero.store.KvStore;
import java.io.IOException;
import java.nio.file.Path;

/**
 * LEARNER CORE #2 — implement this class.
 *
 * <p>Constructor receives AOF path + {@link AofFsyncMode}. Start a daemon fsync ticker when mode is
 * {@link AofFsyncMode#EVERYSEC}.
 */
public final class FileAofJournal implements AofJournal {
  private final Path aofPath;
  private final AofFsyncMode mode;

  public FileAofJournal(Path aofPath, AofFsyncMode mode) {
    this.aofPath = aofPath;
    this.mode = mode;
    // TODO(learner): open channel / create parent dirs / start everysec ticker
  }

  @Override
  public void appendPut(String key, byte[] value, long expireAtEpochMs) throws IOException {
    throw new UnsupportedOperationException("LEARNER: implement FileAofJournal.appendPut (" + mode + ")");
  }

  @Override
  public void appendDel(String key) throws IOException {
    throw new UnsupportedOperationException("LEARNER: implement FileAofJournal.appendDel (" + mode + ")");
  }

  @Override
  public void replayInto(KvStore store) throws IOException {
    throw new UnsupportedOperationException("LEARNER: implement FileAofJournal.replayInto");
  }

  @Override
  public void close() throws IOException {
    // TODO(learner): stop ticker, final fsync, close channel
  }

  Path aofPath() {
    return aofPath;
  }

  AofFsyncMode mode() {
    return mode;
  }
}
