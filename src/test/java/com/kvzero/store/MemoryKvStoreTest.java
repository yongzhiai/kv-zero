package com.kvzero.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Behavior specs for LEARNER {@link MemoryKvStore}. Red until implemented. */
class MemoryKvStoreTest {
  @Test
  void putGetRoundTrip() {
    MemoryKvStore store = new MemoryKvStore();
    store.put("a", new byte[] {1, 2, 3}, -1);
    GetResult r = store.get("a");
    assertFalse(r.expiredAndRemoved());
    assertTrue(r.value().isPresent());
    assertArrayEquals(new byte[] {1, 2, 3}, r.value().orElseThrow());
  }

  @Test
  void missingKeyIsMiss() {
    MemoryKvStore store = new MemoryKvStore();
    GetResult r = store.get("missing");
    assertFalse(r.expiredAndRemoved());
    assertTrue(r.value().isEmpty());
  }

  @Test
  void lazyExpireRemovesAndSignals() throws InterruptedException {
    MemoryKvStore store = new MemoryKvStore();
    long expireAt = System.currentTimeMillis() + 30;
    store.put("temp", new byte[] {9}, expireAt);
    Thread.sleep(50);
    GetResult r = store.get("temp");
    assertTrue(r.expiredAndRemoved());
    assertTrue(r.value().isEmpty());
    // second get is a plain miss
    GetResult r2 = store.get("temp");
    assertFalse(r2.expiredAndRemoved());
    assertTrue(r2.value().isEmpty());
  }

  @Test
  void deleteRemoves() {
    MemoryKvStore store = new MemoryKvStore();
    store.put("x", new byte[] {1}, -1);
    assertTrue(store.delete("x"));
    assertFalse(store.delete("x"));
    assertEquals(0, store.size());
  }
}
