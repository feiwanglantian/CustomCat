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
import java.net.URL;

/**
 * 静态资源处理类
 */
@Sharable
public class StaticHandler extends ChannelInboundHandlerAdapter  {

    private Class applicationClass;

    public StaticHandler(Class zlass) {
        applicationClass = zlass;
    }


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            String uri = httpRequest.uri();
            if(!uri.contains(".")) {
                //如果请求路径中不包含点，则证明为servlet，传递到下一个handler
                ctx.fireChannelRead(msg);
                return;
            }
            System.out.println(uri);
            URL resource = applicationClass.getClassLoader().getResource(uri.substring(1));
            if(resource != null) {
                // 获取本地路径
                String path = resource.getPath();
                //获取默认响应对象
                DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                ctx.write(response);
                // file 文件
                File file = new File(path);
                //使用DefaultFileRegion将文件写入到channel里
                DefaultFileRegion fileRegion = new DefaultFileRegion(file, 0, file.length());
                ctx.write(fileRegion);
                ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                ctx.close();
            }
        }

    }
}
