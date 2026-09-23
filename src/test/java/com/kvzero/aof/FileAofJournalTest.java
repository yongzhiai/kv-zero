package com.kvzero.aof;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kvzero.config.AofFsyncMode;
import com.kvzero.store.GetResult;
import com.kvzero.store.MemoryKvStore;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Behavior specs for LEARNER {@link FileAofJournal}. Red until implemented. */
class FileAofJournalTest {
  @TempDir Path tmp;

  @Test
  void alwaysAppendPutSurvivesReplay() throws Exception {
    Path aof = tmp.resolve("appendonly.aof");
    MemoryKvStore store = new MemoryKvStore();
    try (FileAofJournal journal = new FileAofJournal(aof, AofFsyncMode.ALWAYS)) {
      journal.appendPut("k1", new byte[] {7, 8}, -1);
    }
    assertTrue(Files.size(aof) > 0);
    MemoryKvStore loaded = new MemoryKvStore();
    try (FileAofJournal journal = new FileAofJournal(aof, AofFsyncMode.ALWAYS)) {
      journal.replayInto(loaded);
    }
    GetResult r = loaded.get("k1");
    assertTrue(r.value().isPresent());
    assertArrayEquals(new byte[] {7, 8}, r.value().orElseThrow());
  }

  @Test
  void delRemovesOnReplay() throws Exception {
    Path aof = tmp.resolve("appendonly.aof");
    try (FileAofJournal journal = new FileAofJournal(aof, AofFsyncMode.ALWAYS)) {
      journal.appendPut("k", new byte[] {1}, -1);
      journal.appendDel("k");
    }
    MemoryKvStore loaded = new MemoryKvStore();
    try (FileAofJournal journal = new FileAofJournal(aof, AofFsyncMode.ALWAYS)) {
      journal.replayInto(loaded);
    }
    assertTrue(loaded.get("k").value().isEmpty());
  }

  @Test
  void everysecAppendDoesNotThrow() throws Exception {
    Path aof = tmp.resolve("appendonly.aof");
    try (FileAofJournal journal = new FileAofJournal(aof, AofFsyncMode.EVERYSEC)) {
      journal.appendPut("fast", new byte[] {1}, -1);
      journal.appendDel("fast");
    }
  }
}
