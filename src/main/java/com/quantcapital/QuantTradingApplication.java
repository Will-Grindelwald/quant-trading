package com.quantcapital;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * QuantCapital Java交易引擎启动类
 * 
 * 支持混合架构：Python负责数据获取与存储，Java负责事件驱动引擎及交易流程
 * 
 * 特性：
 * - 事件驱动架构
 * - 高性能低延迟
 * - ZGC垃圾收集器优化
 * - 虚拟线程支持
 * 
 * @author QuantCapital Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class QuantTradingApplication {

    public static void main(String[] args) {
        // 配置系统属性以优化性能
        configureSystemProperties();
        
        SpringApplication.run(QuantTradingApplication.class, args);
    }
    
    /**
     * 配置系统属性以优化性能
     */
    private static void configureSystemProperties() {
        // 启用虚拟线程
        System.setProperty("jdk.virtualThreadScheduler.parallelism", 
                          String.valueOf(Runtime.getRuntime().availableProcessors()));
        
        // 优化网络性能
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.awt.headless", "true");
        
        // 文件编码
        System.setProperty("file.encoding", "UTF-8");
        
        // 时区设置
        System.setProperty("user.timezone", "Asia/Shanghai");
    }
}