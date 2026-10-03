package com.kvzero.api;

import com.kvzero.engine.KvEngine;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Netty HTTP server skeleton: IO threads decode HTTP; business work runs on a separate pool. */
public final class HttpKvServer implements AutoCloseable {
  /** Close a connection that has neither read nor written for this long. */
  private static final int IDLE_SECONDS = 60;
  private final int port;
  private final KvEngine engine;
  private final EventLoopGroup boss;
  private final EventLoopGroup worker;
  private final ExecutorService business;
  private Channel serverChannel;

  public HttpKvServer(int port, KvEngine engine) {
    this.port = port;
    this.engine = engine;
    this.boss = new NioEventLoopGroup(1, new DefaultThreadFactory("kv-boss"));
    this.worker = new NioEventLoopGroup(0, new DefaultThreadFactory("kv-io"));
    this.business = Executors.newFixedThreadPool(
        Math.max(4, Runtime.getRuntime().availableProcessors()),
        new DefaultThreadFactory("kv-biz", true));
  }

  public void start() throws InterruptedException {
    ServerBootstrap b = new ServerBootstrap();
    b.group(boss, worker)
        .channel(NioServerSocketChannel.class)
        .childOption(ChannelOption.TCP_NODELAY, true)
        .childOption(ChannelOption.SO_KEEPALIVE, true)
        .childHandler(new ChannelInitializer<SocketChannel>() {
          @Override
          protected void initChannel(SocketChannel ch) {
            ch.pipeline()
                .addLast(new IdleStateHandler(0, 0, IDLE_SECONDS))
                .addLast(new HttpServerCodec())
                .addLast(new HttpObjectAggregator(16 * 1024 * 1024))
                .addLast(new KvHttpHandler(engine, business));
          }
        });
    serverChannel = b.bind(port).sync().channel();
  }

  @Override
  public void close() {
    if (serverChannel != null) {
      serverChannel.close();
    }
    business.shutdownNow();
    boss.shutdownGracefully();
    worker.shutdownGracefully();
  }
}
