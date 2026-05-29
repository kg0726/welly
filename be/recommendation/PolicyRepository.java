package com.wellie.be.domain.welfare.repository;

import com.wellie.be.domain.welfare.entity.Policy;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PolicyRepository extends JpaRepository<Policy, String>, PolicyRepositoryCustom {
    @Query("SELECT DISTINCT p " +  // 1:N 조인이므로 중복 방지를 위해 DISTINCT 필수
            "FROM Policy p " +
            "JOIN FETCH p.benefit pb " + // fetch 조인을 통해 연관된 benefit 데이터를 한번에 가져옴
            "JOIN FETCH p.regions pr " + // 마찬가지로 연관된 regions 데이터를 한번에 가져옴
            "WHERE " +
            // 1. 지역 필터
            "pr.region.regionCode = :memberRegionCode " +
            // 2. 나이 필터
            "AND (" +
            "   pb.ageInfo = 'Y' " +
            "   OR " +
            "   (pb.ageInfo = 'N' AND pb.minAge <= :memberAge AND pb.maxAge >= :memberAge)" +
            ")")
//            "AND (p.endDate >= CURRENT DATE Or p.endDate IS NULL )") // 3. 마감 필터(데이터가 많이 없어서 일단 주석)
    List<Policy> findRecommendPolicies(
            @Param("memberRegionCode") String memberRegionCode,
            @Param("memberAge") int memberAge
    );
}
