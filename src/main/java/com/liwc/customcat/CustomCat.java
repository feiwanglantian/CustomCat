package com.liwc.customcat;

import com.liwc.customcat.CustomServer;

/**
 * 启动类
 */
public class CustomCat {
    /**
     * 服务器启动方法
     * @param args
     * @throws Exception
     */
    public static void run(String[] args) throws Exception {
        //指定web容器地址
        CustomServer server=new CustomServer("com.liwc.webapp");
        server.start();
    }
}
