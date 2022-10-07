package com.liwc.customcat;

import com.liwc.servlet.HeroRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.util.List;
import java.util.Map;

/**
 * tomcat对于herorequest的实现
 */
public class HttpCustomRequest implements HeroRequest {
    //将字节码数组转换为httpRequest对象 解码
    private HttpRequest httpRequest;

    public HttpCustomRequest(HttpRequest httpRequest) {
        this.httpRequest = httpRequest;
    }

    /**
     * 从request中获取uri
     * @return
     */
    @Override
    public String getUri() {
        return httpRequest.uri();
    }

    /**
     * 获取path路径
     * @return
     */
    @Override
    public String getPath() {
        QueryStringDecoder decoder=new QueryStringDecoder(httpRequest.uri());
        return decoder.path();
    }

    /**
     * 获取所有的方法名
     * @return
     */
    @Override
    public String getMethod() {
        return httpRequest.method().name();
    }

    @Override
    public Map<String, List<String>> getParameters() {
        QueryStringDecoder decoder=new QueryStringDecoder(httpRequest.uri());
        return decoder.parameters();
    }

    @Override
    public List<String> getParameters(String name) {
        return getParameters().get(name);
    }

    @Override
    public String getParameter(String name) {
        List<String> params=getParameters(name);
        if(params.size()==0 || params==null){
            return null;
        }
        return params.get(0);
    }
}
