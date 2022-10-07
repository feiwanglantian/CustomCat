package com.liwc.customcat;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * 静态资源处理类
 */
@Sharable
public class StaticHandler extends SimpleChannelInboundHandler<FullHttpRequest>  {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
        System.out.println("request uri: " + msg.uri());
        if ("/".equals(msg.uri()) || "/index.html".equals(msg.uri())) {
            handleResource(ctx, msg, "index.html");
        } else if (msg.uri().startsWith("/static")) {
            handleResource(ctx, msg, msg.uri().substring(1));
        } else {
            //处理请求链接不存在的情况
            handleNotFound(ctx, msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    private void handleResource(ChannelHandlerContext ctx, FullHttpRequest msg, String resource) throws IOException {
        String url = this.getClass().getResource("/").getPath() + resource;
        File file = new File(url);
        if (!file.exists()) {
            handleNotFound(ctx, msg);
            return;
        }
        if (file.isDirectory()) {
            handleDirectory(ctx, msg, file);
            return;
        }
        handleFile(ctx, msg, file);
    }

    private void handleDirectory(ChannelHandlerContext ctx, FullHttpRequest msg, File file) {
        StringBuilder sb = new StringBuilder();
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isHidden() || !f.canRead()) {
                    continue;
                }
                String name = f.getName();
                sb.append(name).append("<br/>");
            }
        }
        ByteBuf buffer = ctx.alloc().buffer(sb.length());
        buffer.writeCharSequence(sb.toString(), CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(msg.protocolVersion(), HttpResponseStatus.OK, buffer);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        ChannelFuture future = ctx.writeAndFlush(response);
        future.addListener(ChannelFutureListener.CLOSE);
    }

    private void handleFile(ChannelHandlerContext ctx, FullHttpRequest msg, File file) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        HttpHeaders headers = getContentTypeHeader(file);
        HttpResponse response = new DefaultHttpResponse(msg.protocolVersion(), HttpResponseStatus.OK, headers);
        ctx.write(response);
        ctx.write(new DefaultFileRegion(raf.getChannel(), 0, raf.length()));
        ChannelFuture future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        future.addListener(ChannelFutureListener.CLOSE);
    }

    private HttpHeaders getContentTypeHeader(File file) {
        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
        HttpHeaders headers = new DefaultHttpHeaders();
        String contentType = mimeTypesMap.getContentType(file);
        if (contentType.equals("text/plain")) {
            //由于文本在浏览器中会显示乱码，此处指定为utf-8编码
            contentType = "text/plain;charset=utf-8";
        }
        headers.set(HttpHeaderNames.CONTENT_TYPE, contentType);
        return headers;
    }



    private void handleNotFound(ChannelHandlerContext ctx, FullHttpRequest msg) {
        ByteBuf content = Unpooled.copiedBuffer("URL not found", CharsetUtil.UTF_8);
        HttpResponse response = new DefaultFullHttpResponse(msg.protocolVersion(), HttpResponseStatus.NOT_FOUND, content);
        ChannelFuture future = ctx.writeAndFlush(response);
        future.addListener(ChannelFutureListener.CLOSE);
    }
}
