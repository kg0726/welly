package com.wellie.be.domain.welfare.service;

import com.wellie.be.domain.member.dto.PolicyAmountResponse;
import com.wellie.be.domain.member.entity.*;
import com.wellie.be.domain.member.repository.*;
import com.wellie.be.domain.welfare.dto.*;
import com.wellie.be.domain.welfare.entity.Document;
import com.wellie.be.domain.welfare.entity.Policy;
import com.wellie.be.domain.welfare.entity.PolicyBenefit;
import com.wellie.be.domain.welfare.entity.PolicyRequiredDocument;
import com.wellie.be.domain.welfare.repository.DocumentRepository;
import com.wellie.be.domain.welfare.repository.PolicyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Key;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // policy 관련은 조회 전용(성능 최적화도 챙김)
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final MemberRepository memberRepository;
    private final PolicyScrapRepository policyScrapRepository;
    private final MemberDocumentRepository memberDocumentRepository;
    private final MemberInterestRepository memberInterestRepository;
    private final PolicyCacheService policyCacheService;
    private final AiDocumentService aiDocumentService;
    @Qualifier("aiExecutor")
    private final ExecutorService aiExecutor;
    private final MemberProfileRepository memberProfileRepository;
    private final DocumentRepository documentRepository;

    /**
     * 정책 목록 조회 (무한 스크롤)
     * 
     * @param keyword      키워드
     * @param categoryName 카테고리 명 (nullable)
     * @param pageable     페이징 정보 (0페이지부터 10개씩 등)
     */
    public Map<String, Object> getPolicyList(String keyword, String categoryName, Pageable pageable, Member member) {

        // 레포에서 키워드, 카테고리가 일치하는 값 탐색
        Slice<Policy> policySlice = policyRepository.searchPolicies(keyword, categoryName, pageable);
        // DTO 생성자 호출
        Slice<PolicyResponse> responseSlice = policySlice.map(PolicyResponse::new);
        // 사용자가 로그인했다면
        if (member != null) {
            // policyId만 추출
            List<String> policyIdList = new ArrayList<>();
            for (Policy policy : policySlice) {
                policyIdList.add(policy.getPolicyId());
            }
            // 레포를 통해서 사용자가 스크랩한 정책들만 검색
            Set<String> memberPolicyScrapList = policyScrapRepository.findByMemberIdAndPolicyId(member.getId(),
                    policyIdList);
            for (PolicyResponse policyResponse : responseSlice) {
                if (memberPolicyScrapList.contains(policyResponse.getPolicyId())) {
                    policyResponse.setIsScraped(true);
                }
            }
        }
        // 미리 정해둔 응답 형식에 맞춰서 반환
        Map<String, Object> response = new HashMap<>();
        response.put("policies", responseSlice.getContent());
        response.put("hasNext", responseSlice.hasNext());
        response.put("size", responseSlice.getSize());
        response.put("number", responseSlice.getNumber());
        response.put("first", responseSlice.isFirst());
        response.put("empty", responseSlice.isEmpty());
        return response;
    }

    /**
     * 정책 상세 조회
     * 
     * @param policyId 정책 pk
     * @return dto
     */
    public PolicyDetailResponse getPolicyDetail(String policyId, Member loginMember) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("해당 정책이 없습니다."));
        // dto 생성자 호출
        PolicyDetailResponse response = new PolicyDetailResponse(policy);
        if (loginMember != null) {
            Member member = memberRepository.findById(loginMember.getId())
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
            MemberProfile memberProfile = memberProfileRepository.findByMember(member)
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
            // 로그인한 사용자가 해당 정책을 스크랩 했다면 true 응답
            if (policyScrapRepository.existsByMemberIdAndPolicyId(member.getId(), policy.getPolicyId())) {
                response.setIsScraped(true);
            }
            response.setMemberRegionName(memberProfile.getRegionCode().getRegionName());
        }
        return response;
    }

    /**
     * 정책 신청 절차 요약
     */
    public ApplySummaryResponse applyStepSummary(String policyId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("해당 정책이 존재하지 않습니다."));
        // 요약된 신청 정보만 1., 2., 3.으로 파싱하여 DTO 생성자에 전달
        String aiApplySummary = policy.getAiApplySummary();
        // 헬퍼 함수를 호춣하여 문자열 -> 문자열 리스트로 파싱
        Map<String, String> applySummaryMap = applySummaryParser(aiApplySummary);
        System.out.println(applySummaryMap);
        // DTO 생성자 호출
        return new ApplySummaryResponse(policyId, applySummaryMap);
    }

    /**
     * 문자열로 저장된 지원 절차 요약 내용을 1. 2. 3. 을 기준으로 끊어서 (번호: 내용)맵으로 반환하는 헬퍼 함수
     * 
     * @param input -> DB에 있는 지원 절차 요약 문자열
     * @return resultMap -> 각 절차 : 내용으로 파싱 완료된 map
     */
    private static Map<String, String> applySummaryParser(String input) {
        // 정규표현식
        // ?= -> 전방 탐색: 문자를 삭제하지 않고 조건에 맞는 위치만 찾음
        // \d+ -> 숫자 1개 이상
        // \. -> 점
        // \s* -> 공백 0개 이상
        // \D -> 숫자가 아닌 문자 -> 한글, 영어, 특수문자 등
        // -> 숫자 + 점 + 공백 + 숫자가 아닌 문자 패턴이 나오면 그 앞에서 끊는다는 조건
        String regex = "(?=\\d+\\.\\s\\D)";
        String[] chucks = input.split(regex);
        List<String> result = new ArrayList<>();
        for (String chuck : chucks) {
            String trimmed = chuck.trim();
            if (!trimmed.isEmpty())
                result.add(trimmed);
        }
        System.out.println(result);
        // 응답 형식에 맞게 title: content 형식으로 변환
        Map<String, String> resultMap = new HashMap<>();
        for (String str : result) {
            // :를 기준으로 2칸으로 자름
            String[] parts = str.split(":", 2);
            if (parts.length < 2)
                continue; // 콜론이 없다면 스킵
            String key = parts[0];
            String value = parts[1].trim();
            resultMap.put(key, value);
        }
        return resultMap;
    }

    /**
     * 정책 상세조회 -> 정책 필요 서류 - 사용자가 등록한 서류 체크
     */
    public RequiredDocumentResponse checkDocs(String policyId, Member member) {
        // 혹시 모르는 예외처리 -> 로그인 하지 않으면 사용할 수 없는 기능
        if (member == null) {
            throw new RuntimeException("로그인 한 사용자만 접근 가능합니다.");
        }
        // 정책 조회 및 해당 정책의 필요 서류 확보
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("해당 정책이 없습니다."));// todo: 글로벌 에러 핸들러 작성
        List<PolicyRequiredDocument> requiredDocuments = policy.getRequiredDocuments();

        // 사용자 조회 및 사용자가 가지고 있는 서류 조회
        Member findMember = memberRepository.findById(member.getId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다.")); // todo: 글로벌 에러 핸들러 작성
        // 서류에서 서류 이름만 뽑아 set으로 변환
        Set<String> docNameSet = memberDocumentRepository.searchMemberDocumentByMemberId(findMember.getId());

        // DTO를 호출하여 필요 서류에 대한 응답 정보 생성
        RequiredDocumentResponse requiredDocumentResponse = new RequiredDocumentResponse(requiredDocuments);
        // 응답 정보를 순회하며 사용자가 가지고있는 서류가 있는지 확인
        for (RequiredDocumentResponse.Documents document : requiredDocumentResponse.getDocuments()) {
            if (docNameSet.contains(document.getDocName())) {
                document.setIsChecked(true);
            }

            if (documentRepository.existsByDocsName(document.getDocName())) {
                Document doc = documentRepository.findByDocsName(document.getDocName())
                        .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다."));
                String url = doc.getDocsIssuer();
                if (url.contains("https")) document.setIssueUrl(doc.getDocsIssuer());
            }
        }
        return requiredDocumentResponse;
    }

    /**
     * 정책 스크랩 토글
     *
     * @param policyId 정책 pk
     * @param memberId 사용자 pk
     * @return dto
     */
    @Transactional
    public PolicyScrapResponse policyScrap(String policyId, Long memberId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("해당 정책이 없습니다."));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        // 해당 사용자가 인자로 들어온 정책을 팔로우 하고 있는지 확인
        boolean scrapFlag;
        if (policyScrapRepository.existsByMemberIdAndPolicyId(memberId, policyId)) {
            scrapFlag = false;
            // 해당 데이터 삭제 (중복 데이터가 있어도 일괄 삭제됨)
            policyScrapRepository.deleteByMemberIdAndPolicyId(memberId, policyId);
        } else {
            scrapFlag = true;
            // 중복 생성 방지: 더블 체크
            if (!policyScrapRepository.existsByMemberIdAndPolicyId(memberId, policyId)) {
                // 해당 데이터 생성 (알림 설정 추가: 초기값 D-1 true, D-3 false)
                policyScrapRepository.save(PolicyScrap.builder()
                        .member(member)
                        .policy(policy)
                        .alarmD1(true)
                        .alarmD3(false)
                        .build());
            }
        }
        // DTO 생성자 호출 및 반환
        String url;
        if (policy.getThumbUrl() == null) {
            url = "/policythumb/" + policy.getCategoryName().getCategoryName() + ".png";
        } else url = policy.getThumbUrl();
        return new PolicyScrapResponse(policyId, scrapFlag, url);
    }

    /**
     * 정책 알림 설정 변경
     *
     * @param policyId 정책 pk
     * @param memberId 사용자 pk
     * @param request  알림 설정 요청
     */
    @Transactional
    public void updateScrapNotification(String policyId, Long memberId, PolicyNotifyRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 중복 데이터 처리 로직 (Self-healing)
        List<PolicyScrap> scraps = policyScrapRepository.findByMemberIdAndPolicy_PolicyId(member.getId(), policyId);

        if (scraps.isEmpty()) {
            throw new IllegalArgumentException("스크랩한 정책이 아닙니다.");
        }

        // 1. 가장 최신 데이터(0번 인덱스)만 유지하고 업데이트
        PolicyScrap policyScrap = scraps.get(0);

        // Entity에 update 메서드가 없다면 setter나 builder로 수정해야 함.
        // PolicyScrap이 Setter를 가지고 있는지 확인 필요하거나, update 메서드 추가 필요.
        // Entity 수정이 필요할 수 있음. 일단은 update 메서드를 가정하거나 Dirty Checking을 유도함.
        // 확인해보니 PolicyScrap Entity 코드를 안 봤음.
        // 그러나 update 로직을 넣어야 함. Builder는 새 객체 생성임.
        // Entity를 수정해야 할 수도 있음.
        // 일단 여기서는 repository에서 가져온 객체의 상태를 변경하는 코드를 작성.
        // PolicyScrap Entity에 setter가 있는지 모르므로, 확인 후 수정해야 할 수도 있음.
        // 만약 setter가 없다면 Entity에도 편의 메서드 추가 필요.
        // 여기서는 일단 setter가 없다고 가정하고 update 메서드를 사용하는 방식으로 작성 후 Entity 수정하겠음.
        policyScrap.updateNotification(request.getAlarmD1(), request.getAlarmD3());

        // 2. 나머지 중복 데이터가 있다면 삭제
        if (scraps.size() > 1) {
            for (int i = 1; i < scraps.size(); i++) {
                policyScrapRepository.delete(scraps.get(i));
            }
        }
    }

    // 추천 정책 리스트 조회
    public List<PolicyResponse> recommendWelfare(Member member, String sort) {
        Member findMember = memberRepository.findById(member.getId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        MemberProfile memberProfile = findMember.getMemberProfile();

        // 1. 캐시 호출
        List<Policy> candidates = policyCacheService.getCachedCandidates(
                memberProfile.getRegionCode().getRegionCode(),
                memberProfile.getAge());

        // 2. 사람마다 다른 사용자 관심사, 학력, 직업요건 등으로 정책마다 점수를 매김
        Set<String> interests = memberInterestRepository.findMemberInterestByMemberId(findMember.getId());

        // AI 호출이 필요한 (추천 응답에 포함될)정책들만 추림
        List<PolicyResponse> qualifiedList = new ArrayList<>();

        for (Policy candidate : candidates) {
            PolicyResponse response = new PolicyResponse(candidate, sort);
            // 점수 계산 로직 실행
            response.recommendScoreOperator(findMember, interests, candidate);

            if (response.getScore() >= 70) {  // todo: 배포 시 50 이상으로 설정
                String benefitDesc = "";
                if (candidate.getBenefit() != null) {
                    benefitDesc = candidate.getBenefit().getBenefitDesc();
                }
                AiRecommendPromptContext context = AiRecommendPromptContext.builder()
                        .policyTitle(candidate.getTitle())
                        .policyDetail(candidate.getAiDetailContent())
                        .benefitDesc(benefitDesc)
                        .categoryName(candidate.getCategoryName().getCategoryName())
                        .jobCode(candidate.getJobCode())
                        .eduCode(candidate.getEduCode())
                        .memberJobCode(memberProfile.getJobCd().getJobCode())
                        .memberSchoolCode(memberProfile.getSchoolCd().getEduCode())
                        .age(memberProfile.getAge())
                        .interests(interests)
                        .build();
                // policyresponse 객체에 추가하여 관리
                response.setAiRecommendPromptContext(context);
                qualifiedList.add(response);
            }
        }
        // 하이버네이트 엔티티는 영속성 컨텍스트에 묶여있음 해당 세션은 요청을 받은 스레드(메인 스레드)에 귀속됨
        // 메인 스레드에 묶인 엔티티를 다른 스레드로 넘기면 충돌이 발생함

        // 외부 AI api 호출 비동기 처리
        List<CompletableFuture<PolicyResponse>> futures = qualifiedList.stream()
                .map(response -> CompletableFuture.supplyAsync(() -> {
                    // 별도의 스레드가 작업을 시작함
                    try {
                        Policy policy = policyRepository.findById(response.getPolicyId())
                                .orElseThrow(() -> new IllegalArgumentException("해당 정책을 찾을 수 없습니다."));

                        AiRecommendPromptContext aiRecommendPromptContext = response.getAiRecommendPromptContext();

                        // 점수 통과시에만 AI 호출
                        String recommendReason = aiDocumentService.getRecommendReason(aiRecommendPromptContext);
                        response.setRecommendReason(recommendReason);

                        return response;
                    } catch (Exception e) {
                        log.error("정책 추천 처리 중 오류 발생 (Policy ID: {}): {}", response.getPolicyId(), e.getMessage());
                        // AI가 실패하더라도 전체 로직이 다운되지 않도록 null 반환
                        return null;
                    }
                    // 위의 로직이 별도의 스레드에서 수행
                }, aiExecutor)).collect(Collectors.toList());

        // 모든 스레드가 일을 마칠 때 까지 기다렸다가 join을 통해 결과를 수집하여 리턴
        return futures.stream()
                .map(CompletableFuture::join)
                .sorted()
                .collect(Collectors.toList());

        // 동기적 추천 로직
//        List<PolicyResponse> responses = new ArrayList<>();
//
//        for (Policy candidate : candidates) {
//            PolicyResponse response = new PolicyResponse(candidate, sort);
//            // 정책 점수 계산 메서드 호출
//            response.recommendScoreOperator(findMember, interests, candidate);
//            // 정책 점수가 50점 이상인 것만 응답에 포함
//            if (response.getScore() >= 10) {  // todo: 점수 수정
//                // AI를 한번 태워서 추천 이유를 뽑음
//                String recommendReason = aiDocumentService.getRecommendReason(candidate, findMember);
//                response.setRecommendReason(recommendReason);
//                responses.add(response);
//            }
//        }
//
//        // 점수를 기준으로 정렬
//        Collections.sort(responses);
//        return responses;
    }

    public PolicyAmountResponse getPolicyAmount(Member member, String sort) {
        Member findMember = memberRepository.findById(member.getId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        MemberProfile memberProfile = findMember.getMemberProfile();

        // 1. 캐시 호출
        List<Policy> candidates = policyCacheService.getCachedCandidates(
                memberProfile.getRegionCode().getRegionCode(),
                memberProfile.getAge());

        // 2. 사람마다 다른 사용자 관심사, 학력, 직업요건 등으로 정책마다 점수를 매김
        Set<String> interests = memberInterestRepository.findMemberInterestByMemberId(findMember.getId());
        List<PolicyResponse> policyResponses = new ArrayList<>();
        int maxAmount = 0;
        for (Policy candidate : candidates) {
            // 돈 관련 정책이 아니면 넘어감
            if (!candidate.getBenefit().getIsCashBenefit())
                continue;

            PolicyResponse policyResponse = new PolicyResponse(candidate, sort);
            // 정책 점수 계산 메서드 호출
            policyResponse.recommendScoreOperator(findMember, interests, candidate);
            // 정책 점수가 50점 이상인 것만 응답에 포함
            if (policyResponse.getScore() >= 50) {
                policyResponses.add(policyResponse);
                maxAmount += candidate.getBenefit().getMaxAmount();
            }
        }
        return new PolicyAmountResponse(maxAmount, policyResponses);
    }
}
