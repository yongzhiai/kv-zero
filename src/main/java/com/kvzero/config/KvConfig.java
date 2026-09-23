package com.kvzero.config;

import java.nio.file.Path;
import java.util.Locale;

/** Process configuration. Overridable via system properties / env (see {@link #load()}). */
public record KvConfig(
    int httpPort,
    Path dataDir,
    AofFsyncMode aofFsyncMode,
    String aofFileName
) {
  public Path aofPath() {
    return dataDir.resolve(aofFileName);
  }

  public static KvConfig load() {
    int port = intProp("kv.http.port", "KV_HTTP_PORT", 8080);
    Path dataDir = Path.of(strProp("kv.data.dir", "KV_DATA_DIR", "./data"));
    AofFsyncMode mode = AofFsyncMode.fromString(strProp("kv.aof.fsync", "KV_AOF_FSYNC", "everysec"));
    String aofName = strProp("kv.aof.file", "KV_AOF_FILE", "appendonly.aof");
    return new KvConfig(port, dataDir, mode, aofName);
  }

  private static String strProp(String sys, String env, String def) {
    String v = System.getProperty(sys);
    if (v != null && !v.isBlank()) {
      return v;
    }
    v = System.getenv(env);
    if (v != null && !v.isBlank()) {
      return v;
    }
    return def;
  }

  private static int intProp(String sys, String env, int def) {
    String v = strProp(sys, env, null);
    if (v == null) {
      return def;
    }
    return Integer.parseInt(v.trim());
  }

  @Override
  public String toString() {
    return "KvConfig{port=%d, dataDir=%s, aofFsync=%s, aofFile=%s}"
        .formatted(httpPort, dataDir.toAbsolutePath(), aofFsyncMode.name().toLowerCase(Locale.ROOT), aofFileName);
  }
}
