package com.ruici.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI Interview Platform - Main Application
 * Ruici AI 平台 - 主启动类
 */
@EnableScheduling
@SpringBootApplication
public class RuiciAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuiciAiApplication.class, args);
    }
}
