package com.liwc.customcat;

import com.liwc.servlet.HeroServlet;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CustomServer服务端处理器
 * 1、从请求url中解析访问的Servlet名称
 * 2、从nameServletMap中查找是否存在该名称的key。若存在，则直接使用该实例，否则执行第3步
 * 3、从nameClassNameMap中查找是否存在该名称的key，若存在，则获取到其对应的全限定性类名，使用反射机制创建相应的serlet实例，并写入到nameToServletMap中，若不存在，则直接访问默认Servlet
 */
public class CustomHandler extends ChannelInboundHandlerAdapter {
    //key为servlet的简单类名，value为Servlet实例
    private Map<String, HeroServlet> nameServletMap=new ConcurrentHashMap<>();
    //key为servlet的简单类名，value为servlet全限定类名
    private Map<String, String> nameClassNameMap=new HashMap<>();

    public CustomHandler(Map<String, HeroServlet> nameServletMap, Map<String, String> nameClassNameMap) {
        this.nameServletMap = nameServletMap;
        this.nameClassNameMap = nameClassNameMap;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if(msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            String uri = httpRequest.uri();
            //获取当前servlet的名称/aaa/bbb/oneservlet?name=liwc
            String servletName = "";
            if (uri.contains("?") && uri.contains("/")) {
                servletName = uri.substring(uri.lastIndexOf("/") + 1, uri.indexOf("?"));
                HeroServlet heroServlet = new DefaultCustomServlet();
                if (nameServletMap.containsKey(servletName)) {
                    heroServlet = nameServletMap.get(servletName);
                } else if (nameClassNameMap.containsKey(servletName)) {
                    if (nameServletMap.get(servletName) == null) {
                        synchronized (this) {
                            if (nameServletMap.get(servletName) == null) {
                                //从名称map中获取Servlet的全限定名
                                String className = nameClassNameMap.get(servletName);
                                //通过反射，创建当前Servlet实例
                                heroServlet = (HeroServlet) Class.forName(className).newInstance();
                                //将Servlet实例写入到实例map中
                                nameServletMap.put(servletName, heroServlet);
                            }
                        }
                    }
                }
                HttpCustomRequest request = new HttpCustomRequest(httpRequest);
                HttpCustomResponse response = new HttpCustomResponse(httpRequest, ctx);
                //根据不同请求类型，调用不同servlet的方法
                if (httpRequest.method().name().equalsIgnoreCase("GET")) {
                    heroServlet.doGet(request, response);
                } else if (httpRequest.method().name().equalsIgnoreCase("POST")) {
                    heroServlet.doPost(request, response);
                }
                ctx.close();
            }
//            else if(uri.contains("/html")){
//                channelRead();
//            }
        }
    }
}
