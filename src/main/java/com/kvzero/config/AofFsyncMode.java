package com.kvzero.config;

/**
 * AOF fsync policy (Redis-style naming).
 *
 * <ul>
 *   <li>{@link #ALWAYS} — after memory apply, wait for AOF append + fsync before ACK.</li>
 *   <li>{@link #EVERYSEC} — after memory apply, append to AOF buffer and ACK without waiting for fsync
 *       (background fsync ~1s).</li>
 * </ul>
 */
public enum AofFsyncMode {
  ALWAYS,
  EVERYSEC;

  public static AofFsyncMode fromString(String raw) {
    if (raw == null || raw.isBlank()) {
      return EVERYSEC;
    }
    return switch (raw.trim().toLowerCase()) {
      case "always" -> ALWAYS;
      case "everysec", "every_sec", "every-second", "every_second" -> EVERYSEC;
      default -> throw new IllegalArgumentException("Unknown aof.fsync mode: " + raw);
    };
  }
}
