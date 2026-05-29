package com.wellie.be.domain.welfare.service;

import com.wellie.be.domain.member.entity.Member;
import com.wellie.be.domain.member.repository.MemberRepository;
import com.wellie.be.domain.welfare.dto.*;
import com.wellie.be.domain.welfare.entity.Policy;
import com.wellie.be.domain.welfare.repository.PolicyRepository;
import com.wellie.be.domain.welfare.util.PromptProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiDocumentService {

    private final PromptProvider promptProvider;
    private final RestTemplate restTemplate;
    private final PolicyRepository policyRepository;
    private final MemberRepository memberRepository;

    private final String[] gmsConfig = getGmsConfig();


    public AiGenerateResponse generateDocument(Member loginMember, String policyId, DocumentRequest request) {
        Member member = memberRepository.findById(loginMember.getId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("정책을 찾을 수 없습니다."));

        // 1. 시스템 역할과 사용자 내용을 하나의 텍스트로 결합 (가이드 curl 방식)
        String systemRole = promptProvider.getSystemRole(request.getDocName());
        String userPrompt = promptProvider.buildUserPrompt(
                member, policy, request.getSituation(), request.getTargetQuestion()
        );
        String combinedInput = systemRole + "\n\n" + userPrompt;

        // 2. 가이드 규격(input 필드)에 맞춰 요청 객체 생성
        GmsRequest gmsRequest = new GmsRequest(gmsConfig[2], combinedInput);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + gmsConfig[1]);

        HttpEntity<GmsRequest> entity = new HttpEntity<>(gmsRequest, headers);

        try {
            ResponseEntity<GmsResponse> responseEntity = restTemplate.postForEntity(
                    gmsConfig[0], entity, GmsResponse.class
            );

            GmsResponse body = responseEntity.getBody();

            // GMS 전용 추출 로직: output[0] -> content[0] -> text
            if (body != null && body.getOutput() != null && !body.getOutput().isEmpty()) {
                GmsResponse.OutputItem firstOutput = body.getOutput().get(0);

                if (firstOutput.getContent() != null && !firstOutput.getContent().isEmpty()) {
                    return new AiGenerateResponse(firstOutput.getContent().get(0).getText());
                }
            }

            log.error("GMS 응답 데이터 추출 실패. 전체 응답: {}", body);
            return new AiGenerateResponse("AI 응답 형식 분석에 실패했습니다.");

        } catch (Exception e) {
            log.error("GMS API 호출 실패: {}", e.getMessage());
            return new AiGenerateResponse("서류 생성 중 오류가 발생했습니다.");
        }
    }

    public ChatbotResponse chat(Member loginMember, ChatbotRequest request) {
        // 1. 사용자 조회
        Member member = memberRepository.findById(loginMember.getId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 2. (선택) 정책 조회 - 사용자가 특정 정책 상세 페이지에서 질문한 경우
        Policy policy = null;
        if (request.getPolicyId() != null && !request.getPolicyId().isBlank()) {
            policy = policyRepository.findById(request.getPolicyId()).orElse(null);
        }

        // 3. 프롬프트 조립 (시스템 + 사용자정보 + 정책 + 히스토리 + 현재질문)
        String combinedInput = promptProvider.buildChatPrompt(
                member, policy, request);

        // 4. GMS 요청 객체 생성 (채팅용 가벼운 모델 추천: gpt-4o-mini or gpt-3.5-turbo)
        GmsRequest gmsRequest = new GmsRequest(gmsConfig[2], combinedInput);

        // 5. 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + gmsConfig[1]);

        HttpEntity<GmsRequest> entity = new HttpEntity<>(gmsRequest, headers);

        try {
            // 6. API 호출
            ResponseEntity<GmsResponse> responseEntity = restTemplate.postForEntity(
                    gmsConfig[0], entity, GmsResponse.class
            );

            GmsResponse body = responseEntity.getBody();

            // 7. 응답 추출 (AiDocumentService와 동일한 DTO 구조 사용)
            if (body != null && body.getOutput() != null && !body.getOutput().isEmpty()) {
                GmsResponse.OutputItem firstOutput = body.getOutput().get(0);
                if (firstOutput.getContent() != null && !firstOutput.getContent().isEmpty()) {
                    String aiReply = firstOutput.getContent().get(0).getText().trim();

                    // (옵션) 챗봇에서도 태그가 보이면 제거하는 로직 추가 가능
                    return new ChatbotResponse(aiReply);
                }
            }

            log.error("GMS 챗봇 응답 데이터 없음: {}", body);
            return new ChatbotResponse("죄송해요, 잠시 생각할 시간이 필요해요. 다시 말씀해 주시겠어요?");

        } catch (Exception e) {
            log.error("GMS 챗봇 API 호출 실패: {}", e.getMessage());
            return new ChatbotResponse("서버 연결이 원활하지 않습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    // 사용자의 정보와 정책의 정보를 받아 추천하는 이유를 반환하는 함수
    public String getRecommendReason(AiRecommendPromptContext context) {
        String input = promptProvider.buildRecommendReasonPrompt(context);

        // GMS 요청 객체 생성
        GmsRequest gmsRequest = new GmsRequest(gmsConfig[2], input);

        // 5. 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + gmsConfig[1]);

        HttpEntity<GmsRequest> entity = new HttpEntity<>(gmsRequest, headers);

        try {
            // 6. API 호출
            ResponseEntity<GmsResponse> responseEntity = restTemplate.postForEntity(
                    gmsConfig[0], entity, GmsResponse.class
            );

            GmsResponse body = responseEntity.getBody();

            // 7. 응답 추출 (AiDocumentService와 동일한 DTO 구조 사용)
            if (body != null && body.getOutput() != null && !body.getOutput().isEmpty()) {
                GmsResponse.OutputItem firstOutput = body.getOutput().get(0);
                if (firstOutput.getContent() != null && !firstOutput.getContent().isEmpty()) {
                    String aiReply = firstOutput.getContent().get(0).getText().trim();

                    // (옵션) 챗봇에서도 태그가 보이면 제거하는 로직 추가 가능
                    return aiReply;
                }
            }

            log.error("GMS 챗봇 응답 데이터 없음: {}", body);
            return "죄송해요, 잠시 생각할 시간이 필요해요. 다시 말씀해 주시겠어요?";

        } catch (Exception e) {
            log.error("GMS 챗봇 API 호출 실패: {}", e.getMessage());
            return "서버 연결이 원활하지 않습니다. 잠시 후 다시 시도해 주세요.";
        }
    }

    // gmsurl과 키를 가져오는 헬퍼 함수
    public String[] getGmsConfig() {
        String[] config = new String[3];

        config[0] = "https://gms.ssafy.io/gmsapi/api.openai.com/v1/responses";
//        config[0] = "https://gms.ssafy.io/gmsapi/api.openai.com/v1/chat/completions";
        config[1] = System.getenv("GMS_KEY");
        config[2] = "gpt-4o-mini";
//        config[2] = "gpt-4o";
        return config;
    }
}