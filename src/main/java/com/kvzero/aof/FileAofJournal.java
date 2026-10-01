package com.kvzero.aof;

import com.kvzero.config.AofFsyncMode;
import com.kvzero.store.KvStore;

import io.netty.buffer.ByteBuf;

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

/**
 * LEARNER CORE #2 — implement this class.
 *
 * <p>Constructor receives AOF path + {@link AofFsyncMode}. Start a daemon fsync ticker when mode is
 * {@link AofFsyncMode#EVERYSEC}.
 */
public final class FileAofJournal implements AofJournal {
  private final Path aofPath;
  private final AofFsyncMode mode;
  //单线程的定时任务线程池
  private final ScheduledExecutorService scheduledExecutorService=Executors.newSingleThreadScheduledExecutor();
  //文件channel
  private final FileChannel fileChannel;

  public FileAofJournal(Path aofPath, AofFsyncMode mode) {
    this.aofPath = aofPath;
    this.mode = mode;
    // TODO(learner): open channel / create parent dirs / start everysec ticker
    try {
      this.fileChannel=FileChannel.open(aofPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND
        ,StandardOpenOption.WRITE,StandardOpenOption.READ);

      if(mode==AofFsyncMode.EVERYSEC){
        scheduledExecutorService.scheduleAtFixedRate(()->{
          try {
            fileChannel.force(true);
          } catch (IOException e) {
            System.err.println("Error fsyncing AOF file: " + e.getMessage());
          }
        }, 0, 1, TimeUnit.SECONDS);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void appendPut(String key, byte[] value, long expireAtEpochMs) throws IOException {
    //PUT &lt;key&gt; &lt;expireAtEpochMs&gt; &lt;base64(value)&gt;
    //计算缓冲容量
    int capacity = 4
    + key.getBytes(StandardCharsets.UTF_8).length
    + 1
    + Long.toString(expireAtEpochMs).length()
    + 1
    + ((value.length + 2) / 3) * 4
    + 1;
    ByteBuffer buffer=ByteBuffer.allocate(capacity);
    buffer.put("PUT ".getBytes(StandardCharsets.UTF_8));
    buffer.put(key.getBytes(StandardCharsets.UTF_8));
    buffer.put(" ".getBytes(StandardCharsets.UTF_8));
    buffer.put(Long.toString(expireAtEpochMs).getBytes(StandardCharsets.UTF_8));
    buffer.put(" ".getBytes(StandardCharsets.UTF_8));
    buffer.put(Base64.getEncoder().encode(value));
    buffer.put("\n".getBytes(StandardCharsets.UTF_8));
    buffer.flip();

    while (buffer.hasRemaining()) {
      fileChannel.write(buffer);
    }
    if(mode==AofFsyncMode.ALWAYS){
      fileChannel.force(true);
    }
  }

  @Override
  public void appendDel(String key) throws IOException {
    //DEL &lt;key&gt;
    int capacity = 4
    + key.getBytes(StandardCharsets.UTF_8).length
    + 1;
    ByteBuffer buffer=ByteBuffer.allocate(capacity);
    buffer.put("DEL ".getBytes(StandardCharsets.UTF_8));
    buffer.put(key.getBytes(StandardCharsets.UTF_8));
    buffer.put("\n".getBytes(StandardCharsets.UTF_8));
    buffer.flip();
    while (buffer.hasRemaining()) {
      fileChannel.write(buffer);
    }
    if(mode==AofFsyncMode.ALWAYS){
      fileChannel.force(true);
    }
  }

  @Override
  public void replayInto(KvStore store) throws IOException {
    //重启时，从aof文件中读取数据，并写入到store中
    ByteBuffer buffer=ByteBuffer.allocate(1024);
    fileChannel.position(0);
    ByteArrayOutputStream line=new ByteArrayOutputStream();
    while(fileChannel.read(buffer)!=-1){
      buffer.flip();
      while(buffer.hasRemaining()){
        byte b = buffer.get();
        if(b=='\n'){
          apply(store,line.toByteArray());
          line.reset();
        }else{
          line.write(b);
        }
      }
    }
  }

  private void apply(KvStore store,byte[] line){
    //转换为StandardCharsets.UTF_8的字符串
    String lineStr = new String(line, StandardCharsets.UTF_8);
    //分割字符串
    String[] parts = lineStr.split(" ");
    if(parts[0].equals("PUT")){
      store.put(parts[1], Base64.getDecoder().decode(parts[3]), Long.parseLong(parts[2]));
    }else if(parts[0].equals("DEL")){
      store.delete(parts[1]);
    }
  }

  @Override
  public void close() throws IOException {
    scheduledExecutorService.shutdown();
    fileChannel.close();
  }

  Path aofPath() {
    return aofPath;
  }

  AofFsyncMode mode() {
    return mode;
  }
}
