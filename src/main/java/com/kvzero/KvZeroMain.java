package com.kvzero;

import com.kvzero.aof.FileAofJournal;
import com.kvzero.api.HttpKvServer;
import com.kvzero.config.KvConfig;
import com.kvzero.engine.KvEngine;
import com.kvzero.store.MemoryKvStore;
import java.nio.file.Files;

/** Boot: load config → open AOF → replay → bind HTTP. */
public final class KvZeroMain {
  public static void main(String[] args) throws Exception {
    KvConfig config = KvConfig.load();
    Files.createDirectories(config.dataDir());
    System.out.println("Starting kv-zero with " + config);

    MemoryKvStore store = new MemoryKvStore();
    FileAofJournal aof = new FileAofJournal(config.aofPath(), config.aofFsyncMode());
    KvEngine engine = new KvEngine(store, aof);
    engine.loadFromAof();

    HttpKvServer server = new HttpKvServer(config.httpPort(), engine);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        server.close();
        engine.close();
      } catch (Exception ignored) {
        // best-effort shutdown
      }
    }));
    server.start();
    System.out.println("kv-zero listening on :" + config.httpPort());
  }

  private KvZeroMain() {}
}
