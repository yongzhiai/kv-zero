package com.kvzero.aof;

import com.kvzero.config.AofFsyncMode;
import com.kvzero.store.KvStore;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.jctools.queues.MpscUnboundedArrayQueue;

/**
 * LEARNER CORE #2 — implement this class.
 *
 * <p>Constructor receives AOF path + {@link AofFsyncMode}. Start a daemon fsync ticker when mode is
 * {@link AofFsyncMode#EVERYSEC}.
 */
public final class FileAofJournal implements AofJournal {
  private static final int QUEUE_CHUNK = 1024;
  /** High bit marks the journal closed. Low bits count producers inside {@link #enqueue}. */
  private static final int CLOSED = 1 << 30;

  private final Path aofPath;
  private final AofFsyncMode mode;
  private final ScheduledExecutorService scheduledExecutorService =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "kv-aof-fsync");
        thread.setDaemon(true);
        return thread;
      });
  private final FileChannel fileChannel;
  private final MpscUnboundedArrayQueue<AofOp> queue = new MpscUnboundedArrayQueue<>(QUEUE_CHUNK);
  private final AtomicInteger gate = new AtomicInteger();
  private final Object channelLock = new Object();
  private final Thread consumer;

  public FileAofJournal(Path aofPath, AofFsyncMode mode) {
    this.aofPath = aofPath;
    this.mode = mode;
    try {
      this.fileChannel = FileChannel.open(aofPath, StandardOpenOption.CREATE, StandardOpenOption.READ,
          StandardOpenOption.WRITE);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    if (mode == AofFsyncMode.EVERYSEC) {
      scheduledExecutorService.scheduleAtFixedRate(() -> {
        try {
          synchronized (channelLock) {
            if ((gate.get() & CLOSED) == 0) {
              fileChannel.force(true);
            }
          }
        } catch (IOException e) {
          System.err.println("Error fsyncing AOF file: " + e.getMessage());
        }
      }, 1, 1, TimeUnit.SECONDS);
    }
    consumer = Thread.ofPlatform().name("kv-aof").daemon(true).unstarted(this::consume);
    consumer.start();
  }

  @Override
  public AofOp startPut(String key, byte[] value, long expireAtEpochMs) {
    return new AofOp(this, true, key, value, expireAtEpochMs, mode == AofFsyncMode.ALWAYS);
  }

  @Override
  public AofOp startDel(String key) {
    return new AofOp(this, false, key, null, 0L, mode == AofFsyncMode.ALWAYS);
  }

  void enqueue(AofOp op) {
    int state;
    do {
      state = gate.get();
      if ((state & CLOSED) != 0) {
        throw new IllegalStateException("AOF journal is closed");
      }
    } while (!gate.compareAndSet(state, state + 1));
    try {
      queue.offer(op);
    } finally {
      gate.decrementAndGet();
    }
    LockSupport.unpark(consumer);
  }

  @Override
  public void appendPut(String key, byte[] value, long expireAtEpochMs) throws IOException {
    AofOp op = startPut(key, value, expireAtEpochMs);
    op.offer();
    op.finish();
  }

  @Override
  public void appendDel(String key) throws IOException {
    AofOp op = startDel(key);
    op.offer();
    op.finish();
  }

  private void consume() {
    while (true) {
      AofOp op = queue.poll();
      if (op == null && producersQuiesced()) {
        op = queue.poll();
        if (op == null) {
          return;
        }
      }
      if (op == null) {
        LockSupport.park();
        continue;
      }
      try {
        writeOp(op);
        op.complete();
      } catch (IOException e) {
        op.fail(e);
      }
    }
  }

  private boolean producersQuiesced() {
    int state = gate.get();
    return (state & CLOSED) != 0 && (state & ~CLOSED) == 0;
  }

  private void writeOp(AofOp op) throws IOException {
    synchronized (channelLock) {
      fileChannel.position(fileChannel.size());
      if (op.put) {
        writePut(op.key, op.value, op.expireAtEpochMs);
      } else {
        writeDel(op.key);
      }
      if (mode == AofFsyncMode.ALWAYS) {
        fileChannel.force(true);
      }
    }
  }

  private void writePut(String key, byte[] value, long expireAtEpochMs) throws IOException {
    byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
    byte[] expireBytes = Long.toString(expireAtEpochMs).getBytes(StandardCharsets.UTF_8);
    byte[] encoded = Base64.getEncoder().encode(value);
    int capacity = 4 + keyBytes.length + 1 + expireBytes.length + 1 + encoded.length + 1;
    ByteBuffer buffer = ByteBuffer.allocate(capacity);
    buffer.put("PUT ".getBytes(StandardCharsets.UTF_8));
    buffer.put(keyBytes);
    buffer.put((byte) ' ');
    buffer.put(expireBytes);
    buffer.put((byte) ' ');
    buffer.put(encoded);
    buffer.put((byte) '\n');
    buffer.flip();
    writeFully(buffer);
  }

  private void writeDel(String key) throws IOException {
    byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
    ByteBuffer buffer = ByteBuffer.allocate(4 + keyBytes.length + 1);
    buffer.put("DEL ".getBytes(StandardCharsets.UTF_8));
    buffer.put(keyBytes);
    buffer.put((byte) '\n');
    buffer.flip();
    writeFully(buffer);
  }

  private void writeFully(ByteBuffer buffer) throws IOException {
    while (buffer.hasRemaining()) {
      fileChannel.write(buffer);
    }
  }

  @Override
  public void replayInto(KvStore store) throws IOException {
    synchronized (channelLock) {
      ByteBuffer buffer = ByteBuffer.allocate(1024);
      fileChannel.position(0);
      ByteArrayOutputStream line = new ByteArrayOutputStream();
      while (true) {
        int n = fileChannel.read(buffer);
        if (n <= 0) {
          break;
        }
        buffer.flip();
        while (buffer.hasRemaining()) {
          byte b = buffer.get();
          if (b == '\n') {
            apply(store, line.toByteArray());
            line.reset();
          } else {
            line.write(b);
          }
        }
        buffer.clear();
      }
    }
  }

  private void apply(KvStore store, byte[] line) {
    if (line.length == 0) {
      return;
    }
    String lineStr = new String(line, StandardCharsets.UTF_8);
    String[] parts = lineStr.split(" ");
    if (parts[0].equals("PUT")) {
      store.put(parts[1], Base64.getDecoder().decode(parts[3]), Long.parseLong(parts[2]));
    } else if (parts[0].equals("DEL")) {
      store.delete(parts[1]);
    }
  }

  @Override
  public void close() throws IOException {
    int state;
    do {
      state = gate.get();
      if ((state & CLOSED) != 0) {
        return;
      }
    } while (!gate.compareAndSet(state, state | CLOSED));
    while ((gate.get() & ~CLOSED) != 0) {
      Thread.onSpinWait();
    }
    LockSupport.unpark(consumer);
    try {
      consumer.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted closing AOF", e);
    }
    scheduledExecutorService.shutdown();
    synchronized (channelLock) {
      fileChannel.force(true);
      fileChannel.close();
    }
  }

  Path aofPath() {
    return aofPath;
  }

  AofFsyncMode mode() {
    return mode;
  }
}
