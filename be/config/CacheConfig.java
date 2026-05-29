package com.wellie.be.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
        public CacheManager cacheManager() {
            CaffeineCacheManager cacheManager = new CaffeineCacheManager();

            // 핵심 설정: 메모리 보호 장치
            cacheManager.setCaffeine(Caffeine.newBuilder()
                // 1. 최대 개수 제한: 500개만 저장 (개발 서버 사양 고려)
                // 지역+나이 조합이 500개를 넘어가면 잘 안 쓰는 것부터 삭제함
                .maximumSize(500)

                // 2. 시간 제한: 10분 지나면 삭제 (데이터 갱신 고려)
                .expireAfterWrite(10, TimeUnit.MINUTES)

                // 3. (선택) 메모리가 정말 부족하면 캐시부터 비움 (Weak Reference)
                // .weakKeys()
                // .softValues()
                .recordStats()); // 캐시 적중률 로그 보려고 설정 (선택사항)

        return cacheManager;
    }
}