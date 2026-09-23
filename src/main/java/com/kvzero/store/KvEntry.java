package com.kvzero.store;

import java.util.Arrays;
import java.util.Objects;

/**
 * In-memory value envelope.
 *
 * @param value raw bytes (opaque; clients may use protobuf)
 * @param expireAtEpochMs absolute expiry instant; {@code -1} means no TTL
 */
public record KvEntry(byte[] value, long expireAtEpochMs) {
  public KvEntry {
    Objects.requireNonNull(value, "value");
    value = Arrays.copyOf(value, value.length);
  }

  public boolean isEternal() {
    return expireAtEpochMs < 0;
  }

  public boolean isExpired(long nowEpochMs) {
    return !isEternal() && nowEpochMs >= expireAtEpochMs;
  }

  @Override
  public byte[] value() {
    return Arrays.copyOf(value, value.length);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof KvEntry that)) {
      return false;
    }
    return expireAtEpochMs == that.expireAtEpochMs && Arrays.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return 31 * Arrays.hashCode(value) + Long.hashCode(expireAtEpochMs);
  }
}
