package com.kvzero.store;

import java.util.Optional;

/**
 * Result of {@link KvStore#get(String)}.
 *
 * @param value present when key exists and is not expired
 * @param expiredAndRemoved true when the key existed but was past TTL and the store deleted it
 *     (caller must append {@code DEL} to AOF)
 */
public record GetResult(Optional<byte[]> value, boolean expiredAndRemoved) {
  public static GetResult miss() {
    return new GetResult(Optional.empty(), false);
  }

  public static GetResult hit(byte[] bytes) {
    return new GetResult(Optional.of(bytes), false);
  }

  public static GetResult expiredRemoved() {
    return new GetResult(Optional.empty(), true);
  }
}
