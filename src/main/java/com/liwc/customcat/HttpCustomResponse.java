package com.liwc.customcat;

import com.liwc.servlet.HeroResponse;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.internal.StringUtil;

import java.nio.charset.StandardCharsets;

/**
 * tomcatdemo对于HeroResponse的实现
 */
public class HttpCustomResponse implements HeroResponse {
    //请求对象
    private HttpRequest request;
    private ChannelHandlerContext channelHandlerContext;

    public HttpCustomResponse(HttpRequest request, ChannelHandlerContext channelHandlerContext) {
        this.request = request;
        this.channelHandlerContext = channelHandlerContext;
    }

    @Override
    public void write(String content) throws Exception {
        /**
         * 如果请求为空，啥也不干
         */
        if(StringUtil.isNullOrEmpty(content)){
            return;
        }
        //创建响应对象-netty
        FullHttpResponse response=new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                //根据响应体内容大小，为respose分配存储空间
                Unpooled.wrappedBuffer(content.getBytes("UTF-8")));
        //获取响应头
        HttpHeaders headers = response.headers();
        if(request.uri().contains("/html")){
            headers.set(HttpHeaderNames.CONTENT_TYPE,"text/html");
        }else{
            //设置响应体类型
            headers.set(HttpHeaderNames.CONTENT_TYPE,"text/json");
        }


        //设置响应体长度
        headers.set(HttpHeaderNames.CONTENT_LENGTH,
                response.content().readableBytes());
        // 设置缓存过期时间
        headers.set(HttpHeaderNames.EXPIRES, 0);
        // 若HTTP请求是长连接，则响应也使用长连接
        if (HttpUtil.isKeepAlive(request)) {
            headers.set(HttpHeaderNames.CONNECTION,
                    HttpHeaderValues.KEEP_ALIVE);
        }
        //把响应体写入到channel中
        channelHandlerContext.writeAndFlush(response);
    }
}
