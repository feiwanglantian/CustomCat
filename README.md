# 基于Netty实现一个简单的Web服务器

## 设计思路

### 1、实现servlet中的HttpRequest和HttpResonse

### 2、在Handler中找到对应的Servlet，调用对应的doGet与doPost方法

### 3、得到方法的处理了结果之后，我们会创建一个响应对象，设置响应头和响应体，并通过Handler上下文中的Channel写入内容，Netty会自动编码成一个标准的http响应。

### 4、静态资源处理Handler中，如果请求中包含“.”，说明请求的是静态资源，则直接拿到资源的path，使用DefaultFileRegion将文件写入到channel里，即可处理静态资源文件

### 核心代码

CustomHandler

```java
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
        }
    }
}

```

StaticHandler

```java
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

```

Netty服务器核心代码

```java
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
        runServer(CustomServer.class);
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
    private void runServer(Class zclass) throws Exception{
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
                            pipeline.addLast("staticHandler",new StaticHandler(zclass));
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

```

### 测试，实现servlet类的代码

```java
import com.liwc.servlet.HeroRequest;
import com.liwc.servlet.HeroResponse;
import com.liwc.servlet.HeroServlet;

/**
 * helloservlet
 */
public class HelloServlet extends HeroServlet {
    @Override
    public void doGet(HeroRequest heroRequest, HeroResponse heroResponse) throws Exception {
        String uri=heroRequest.getUri();
        String method=heroRequest.getMethod();
        String path=heroRequest.getPath();
        String name=heroRequest.getParameter("name");
        heroResponse.write("uri="+uri+"\n"+
                "path="+path+"\n"+
                "param="+name+"\n"+
                "method="+method+"\n");
    }

    @Override
    public void doPost(HeroRequest heroRequest, HeroResponse heroResponse) throws Exception {
        doGet(heroRequest, heroResponse);
    }
}
```

