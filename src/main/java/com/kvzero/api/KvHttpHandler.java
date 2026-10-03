package com.kvzero.api;

import com.kvzero.engine.KvEngine;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.timeout.IdleStateEvent;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Minimal HTTP API:
 * <ul>
 *   <li>{@code PUT /kv/{key}?ttlMs=} body = raw bytes</li>
 *   <li>{@code GET /kv/{key}}</li>
 *   <li>{@code DELETE /kv/{key}}</li>
 *   <li>{@code GET /health}</li>
 * </ul>
 */
public final class KvHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
  private final KvEngine engine;
  private final ExecutorService business;

  public KvHttpHandler(KvEngine engine, ExecutorService business) {
    this.engine = engine;
    this.business = business;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
    req.retain();
    business.execute(() -> handle(ctx, req));
  }

  private void handle(ChannelHandlerContext ctx, FullHttpRequest req) {
    boolean keepAlive = HttpUtil.isKeepAlive(req);
    try {
      QueryStringDecoder decoder = new QueryStringDecoder(req.uri());
      String path = decoder.path();
      if ("/health".equals(path) && req.method() == HttpMethod.GET) {
        write(ctx, HttpResponseStatus.OK, Unpooled.copiedBuffer("ok", StandardCharsets.UTF_8), "text/plain", keepAlive);
        return;
      }
      if (!path.startsWith("/kv/")) {
        write(ctx, HttpResponseStatus.NOT_FOUND, Unpooled.EMPTY_BUFFER, "text/plain", keepAlive);
        return;
      }
      String key = path.substring("/kv/".length());
      if (key.isEmpty()) {
        write(ctx, HttpResponseStatus.BAD_REQUEST, text("missing key"), "text/plain", keepAlive);
        return;
      }

      if (req.method() == HttpMethod.PUT) {
        byte[] body = toBytes(req.content());
        long expireAt = parseExpireAt(decoder);
        engine.put(key, body, expireAt);
        write(ctx, HttpResponseStatus.NO_CONTENT, Unpooled.EMPTY_BUFFER, "application/octet-stream", keepAlive);
        return;
      }
      if (req.method() == HttpMethod.GET) {
        Optional<byte[]> value = engine.get(key);
        if (value.isEmpty()) {
          write(ctx, HttpResponseStatus.NOT_FOUND, Unpooled.EMPTY_BUFFER, "application/octet-stream", keepAlive);
        } else {
          write(ctx, HttpResponseStatus.OK, Unpooled.wrappedBuffer(value.get()), "application/octet-stream", keepAlive);
        }
        return;
      }
      if (req.method() == HttpMethod.DELETE) {
        boolean removed = engine.delete(key);
        write(ctx,
            removed ? HttpResponseStatus.NO_CONTENT : HttpResponseStatus.NOT_FOUND,
            Unpooled.EMPTY_BUFFER,
            "application/octet-stream",
            keepAlive);
        return;
      }
      write(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, Unpooled.EMPTY_BUFFER, "text/plain", keepAlive);
    } catch (IllegalArgumentException e) {
      write(ctx, HttpResponseStatus.BAD_REQUEST, text(e.getMessage()), "text/plain", keepAlive);
    } catch (Exception e) {
      write(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, text(e.toString()), "text/plain", keepAlive);
    } finally {
      req.release();
    }
  }

  private static long parseExpireAt(QueryStringDecoder decoder) {
    List<String> ttlMs = decoder.parameters().get("ttlMs");
    if (ttlMs == null || ttlMs.isEmpty()) {
      return -1L;
    }
    long ttl = Long.parseLong(ttlMs.getFirst());
    if (ttl < 0) {
      throw new IllegalArgumentException("ttlMs must be >= 0");
    }
    if (ttl == 0) {
      return -1L;
    }
    return System.currentTimeMillis() + ttl;
  }

  private static byte[] toBytes(ByteBuf buf) {
    byte[] bytes = new byte[buf.readableBytes()];
    buf.getBytes(buf.readerIndex(), bytes);
    return bytes;
  }

  private static ByteBuf text(String s) {
    return Unpooled.copiedBuffer(s == null ? "" : s, StandardCharsets.UTF_8);
  }

  private static void write(ChannelHandlerContext ctx, HttpResponseStatus status, ByteBuf content, String ctype,
      boolean keepAlive) {
    FullHttpResponse res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content);
    res.headers().set(HttpHeaderNames.CONTENT_TYPE, ctype);
    res.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
    if (keepAlive) {
      res.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
      ctx.writeAndFlush(res);
    } else {
      res.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
      ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
    }
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
    if (evt instanceof IdleStateEvent) {
      ctx.close();
      return;
    }
    ctx.fireUserEventTriggered(evt);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    ctx.close();
  }
}
