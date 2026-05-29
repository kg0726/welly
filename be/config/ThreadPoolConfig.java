package com.wellie.be.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ThreadPoolConfig {

    // AI 추천 전용 스레드 풀 빈 등록
    @Bean(name = "aiExecutor")
    public ExecutorService aiExecutor() {
        return Executors.newFixedThreadPool(30);
    }
}
