package com.wellie.be.domain.welfare.service;

import com.wellie.be.domain.welfare.entity.Policy;
import com.wellie.be.domain.welfare.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PolicyCacheService {

    private final PolicyRepository policyRepository;

    // DB 조회 결과만 캐싱함(점수 계산 전)
    // 캐싱 키: 지역 + 나이
    @Cacheable(value = "policyCandidates", key = "#regionCode + '-' + #age", unless = "#result.isEmpty()")
    public List<Policy> getCachedCandidates(String regionCode, int age) {
        return policyRepository.findRecommendPolicies(regionCode, age);
    }

}
