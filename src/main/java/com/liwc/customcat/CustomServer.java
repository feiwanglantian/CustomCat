package com.liwc.customcat;

import com.liwc.servlet.HeroServlet;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CustomCat核心业务类
 */
public class CustomServer {
    //key为servlet的简单类名，value为Servlet实例
    private Map<String, HeroServlet> nameServletMap=new ConcurrentHashMap<>();
    //key为servlet的简单类名，value为servlet全限定类名
    private Map<String, String > nameClassNameMap=new HashMap<>();
    //servlet存放位置
    private String basePackage;


    public CustomServer(String basePackage) {
        this.basePackage = basePackage;
    }

    public void start() throws Exception{
        //加载指定包下所有servlet的类名
        cacheClassName(basePackage);
        //启动Server服务
        runServer();
    }

    /**
     * 加载指定包下的所有servlet类名
     * @param basePackage
     */
    private void cacheClassName(String basePackage) {
        //com.liwc.webapp -> com/liwc/webapp
        URL resource=this.getClass().getClassLoader().getResource(basePackage.replaceAll("\\.","/"));
        //如果没有资源，则结束
        if(resource==null){
            return;
        }
        File dir=new File(resource.getFile());
        for(File file:dir.listFiles()){
            if(file.isDirectory()){
                //如果当前遍历的file资源为目录，则递归调用
                cacheClassName(basePackage+"."+file.getName());
            }else if(file.getName().endsWith(".class")){
                String simpleClassName=file.getName().replace(".class","").trim();
                nameClassNameMap.put(simpleClassName,basePackage+"."+simpleClassName);
            }
        }
    }

    /**
     * 启动CustomServer
     */
    private void runServer() throws Exception{
        EventLoopGroup parentGroup=new NioEventLoopGroup();
        EventLoopGroup childGroup=new NioEventLoopGroup();
        try{
            ServerBootstrap bootstrap=new ServerBootstrap();
            bootstrap.group(parentGroup,childGroup)
                    //指定存放请求队列的长度
                    .option(ChannelOption.SO_BACKLOG,1024)
                    //指定是否启用心跳机制检测长连接存活性
                    .childOption(ChannelOption.SO_KEEPALIVE,true)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            ChannelPipeline pipeline=socketChannel.pipeline();
                            //netty默认 http解码器
                            pipeline.addLast(new HttpServerCodec());
                            //自定义解析请求处理器
                            pipeline.addLast("staticHandler",new StaticHandler());
                            pipeline.addLast("customHandler",new CustomHandler(nameServletMap,nameClassNameMap));
                        }
                    });
            int port=initPort();
            ChannelFuture future=bootstrap.bind(port).sync();
            System.out.println("CustomerServer启动成功，端口号为："+port);
            future.channel().closeFuture().sync();
        }finally {
            parentGroup.shutdownGracefully();
            childGroup.shutdownGracefully();
        }
    }
    /**
     * 初始化端口，使用dom4j读取server.xml中的端口信息
     */
    private int initPort() throws DocumentException {
        InputStream inputStream=CustomServer.class.getClassLoader().getResourceAsStream("server.xml");
        SAXReader saxReader=new SAXReader();
        Document doc=saxReader.read(inputStream);
        Element portEle= (Element) doc.selectSingleNode("//port");
        return Integer.valueOf(portEle.getText());
    }
}
