package com.kvzero.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kvzero.aof.FileAofJournal;
import com.kvzero.config.AofFsyncMode;
import com.kvzero.store.MemoryKvStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Engine wiring: expired GET must cause AOF DEL. Red until learner cores work. */
class KvEngineLazyExpireAofTest {
  @TempDir Path tmp;

  @Test
  void expiredGetAppendsDelToAof() throws Exception {
    Path aof = tmp.resolve("appendonly.aof");
    MemoryKvStore store = new MemoryKvStore();
    FileAofJournal journal = new FileAofJournal(aof, AofFsyncMode.ALWAYS);
    try (KvEngine engine = new KvEngine(store, journal)) {
      long expireAt = System.currentTimeMillis() + 30;
      engine.put("e", new byte[] {1}, expireAt);
      Thread.sleep(50);
      Optional<byte[]> v = engine.get("e");
      assertTrue(v.isEmpty());
    }
    String text = Files.readString(aof);
    assertTrue(text.contains("DEL e") || text.toUpperCase().contains("DEL"),
        "AOF should contain DEL after lazy-expire GET, got:\n" + text);
  }
}
