package com.liwc.customcat;

import com.liwc.servlet.HeroRequest;
import com.liwc.servlet.HeroResponse;
import com.liwc.servlet.HeroServlet;

/**
 * CustomCat对servlet的默认实现
 */
public class DefaultCustomServlet extends HeroServlet {

    @Override
    public void doGet(HeroRequest request, HeroResponse response) throws Exception {
        // http://localhost:8080/aaa/bbb/oneservlet?name=liwc
        // path：/aaa/bbb/oneservlet?name=liwc
        String uri = request.getUri();
        String name = uri.substring(0, uri.indexOf("?"));
        response.write("404 - no this servlet : " + name);
    }

    @Override
    public void doPost(HeroRequest request, HeroResponse response) throws Exception {
        doGet(request,response);
    }
}
