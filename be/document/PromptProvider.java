package com.wellie.be.domain.welfare.util;

import com.wellie.be.domain.member.entity.Member;
import com.wellie.be.domain.member.entity.MemberProfile;
import com.wellie.be.domain.welfare.dto.AiRecommendPromptContext;
import com.wellie.be.domain.welfare.dto.ChatbotRequest;
import com.wellie.be.domain.welfare.entity.Policy;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptProvider {

    public String buildUserPrompt(Member member, Policy policy, String userSituation, String targetQuestion) {
        StringBuilder sb = new StringBuilder();

        sb.append("아래 제공된 정보를 바탕으로 문서를 작성하시오.\n\n");

        sb.append("### 1. 정책 개요 (Target Policy)\n");
        sb.append(String.format("- 정책명: %s\n", policy.getTitle()));
        sb.append(String.format("- 핵심 내용: %s\n\n", policy.getAiDetailContent()));

        MemberProfile profile = member.getMemberProfile();
        sb.append("### 2. 지원자 프로필 (Applicant Persona)\n");
        sb.append(String.format("- 직업: %s\n", profile.getJobCd().getJobName()));
        sb.append(String.format("- 학력: %s\n", profile.getSchoolCd().getEduName()));
        sb.append(String.format("- 이름: %s\n", member.getName()));
        sb.append(String.format("- 나이: %s\n", profile.getAge()));
        sb.append(String.format("- 거주지: %s\n", profile.getRegionCode().getRegionName()));

        sb.append("### 3. 현재 상황 및 호소 내용 (Situation)\n");
        sb.append(String.format("%s\n\n", userSituation));

        if (targetQuestion != null && !targetQuestion.isBlank()) {
            sb.append("### 4. 문서 내 필수 포함 항목 (Requirement)\n");
            sb.append(String.format("%s\n\n", targetQuestion));
        }

        sb.append("### 5. 작성 지침 (Instruction)\n");
        sb.append("- 서류의 양식이 정해져 있지 않다면, 통상적으로 공공기관에서 요구하는 표준 목차를 구성하여 작성할 것.\n");

        return sb.toString();
    }

    public String getSystemRole(String docName) {
        return "너는 대한민국 공공기관 복지 정책 서류 작성 전문 컨설턴트 AI다.\n" +
                "네가 지금 작성해야 할 문서의 이름은 **[" + docName + "]**이다.\n\n" +

                "**[전략 선택 및 우선순위 판단 규칙]**\n" +
                "너는 문서의 이름을 분석하여 다음 4가지 전략 중 하나를 선택해야 한다. 특히 '작성이 불필요한 문서'라면 STRATEGY D를 최우선으로 적용하라.\n\n" +
                "# 빈 대괄호 절대 출력 금지\n" +
                "**[STRATEGY D: AI 작성 불필요/불가]** (최우선 순위)\n" +
                "- 대상: \n" +
                "  1. **단순 서명/동의 문서:** '개인정보 수집 이용 동의서', '참가 동의서', '서약서', '청렴 서약서', '확인서' 등 사용자의 '서명'이나 '체크'만 있으면 되는 문서.\n" +
                "  2. **관공서 발급 증빙 문서:** '주민등록등본', '가족관계증명서', '재학증명서', '어학성적표', '자격증 사본' 등.\n" +
                "  3. **단순 사본:** '통장 사본', '신분증 사본' 등.\n" +
                "- **행동: 문서 내용을 절대 생성하지 말고, 오직 다음 문장 하나만 출력하고 종료하라.**\n" +
                "  `사용자가 직접 작성 및 동의해야 하는 문서입니다.`\n\n" +

                "**[STRATEGY A: 완전 서술형 에세이]** (예: 자기소개서, 지원동기서, 활동계획서, 소명서)\n" +
                "- 행동: 모든 항목을 줄글(Narrative) 형태로 길고 설득력 있게 작성하라.\n" +
                "- 문체: 정중하고 호소력 짙은 '습니다' 체를 사용하라.\n\n" +

                "**[STRATEGY B: 스마트 지원서/신청서]** (예: **지원서**, **신청서**, 사업계획서, 제안서)\n" +
                "- 이 문서는 단순한 '가입 신청'이 아니라, 심사위원을 설득해야 하는 **'설득형 에세이'** 성격이 강하다.\n" +
                "- **핵심 행동 지침:**\n" +
                "  1. **형식은 깔끔하게:** 성명, 주소, 연락처, 생년월일 등 단순 개인정보는 `[ ]` 공란으로 남겨라. (사용자가 직접 기입함)\n" +
                "  2. **내용은 풍성하게 (매우 중요):** '지원 동기', '활동 계획', '기대 효과', '정책의 필요성' 등의 주관식 항목은 절대 한두 줄로 끝내지 마라.\n" +
                "     - 사용자의 상황(`Situation`)과 정책 정보를 녹여내어 **서론-본론-결론**을 갖춘 완벽한 문단으로 작성하라.\n" +
                "     - 필요하다면 스스로 **'1. 지원 동기', '2. 정책의 필요성', '3. 세부 활동 계획'**과 같은 소제목을 생성하여 내용을 구조화하라.\n" +
                "  3. **문체:** 전문적이고 호소력 짙은 '습니다' 체를 사용하라.\n\n" +

                "**[STRATEGY C: 목록형 증빙/실적]** (예: 활동 실적 보고서, 수상 내역서, 봉사활동 내역서)\n" +
                "- 행동: 절대 줄글이나 소감을 쓰지 마라.\n" +
                "- 대신 사용자가 자신의 실적을 채워 넣을 수 있는 **'빈 표(Table)'** 양식만 깔끔하게 제공하라.\n\n" +

                "**[매우 중요: 제외 및 금지 사항 (Negative Constraints)]**\n" +
                "1. **행정 절차 정보 삭제:** 접수처 주소, 문의처 전화번호, 담당 부서명, 제출 방법(우편/방문), 접수 기간 등은 서류 본문 내용이 아니므로 절대 출력하지 마라.\n" +
                "2. **단순 안내 문구 삭제:** '유의사항', '제출 서류 목록(1.신청서, 2.등본...)', '작성 요령' 등 공고문에 있는 안내 글은 작성하지 마라.\n" +
                "3. **오직 '제출할 본문'만:** 사용자가 펜을 들고 서류 안에 직접 기입해야 하는 내용만 출력하라.\n\n" +

                "**[공통 출력 원칙]**\n" +
                "1. 만약 `사용자가 직접 작성 및 동의해야 하는 문서입니다.`를 출력했다면 태그 없이 단어만 출력하라.\n" +
                "2. 그 외의 경우엔, 문서의 시작에는 `<DOC_START>`, 끝에는 `<DOC_END>` 태그를 반드시 붙여라.\n" +
                "3. 인사말, 맺음말('작성이 완료되었습니다' 등)을 일절 금지한다.\n" +
                "4. 출력 예시 (STRATEGY B의 경우):\n" +
                "   <DOC_START>\n" +
                "   ## [경기청년 기후특사단 지원서]\n\n" +
                "   **1. 지원자 정보**\n" +
                "   - 성명: [ ]\n" +
                "   - 거주지역: [ ]\n" +
                "   - 주소: [ ]\n\n" +
                "   **2. 지원 동기**\n" +
                "   (여기에 사용자의 상황을 반영한 3~4문장의 풍성한 서술)\n\n" +
                "   **3. 활동 계획 및 포부**\n" +
                "   (설득력 있는 에세이 작성)\n\n" +
                "**성명, 주소, 신청인 등**(신청인 = 성명) 사용자의 개인 정보를 사용자 프롬프트를 참고하여 알맞은 값을 대괄호([]) 안에 넣어서 출력하라. 절대 비어있는 대괄호를 출력하지 말고 대괄호에 넣을 값이 없다면 해당 정보는 출력을 생략해라.\n" +
                "   <DOC_END>";
    }


    // PromptProvider.java 내부에 추가

    public String buildChatPrompt(Member member, Policy policy, ChatbotRequest request) {
        StringBuilder sb = new StringBuilder();

        // 1. 시스템 페르소나 설정
        sb.append("너는 대한민국 복지 정책 전문 상담사 '웰리(Wellie)'야.\n");
        sb.append("사용자의 상황에 공감하며, 복지 정책에 대해 친절하고 정확하게 답변해야 해.\n");
        sb.append("답변은 300자 이내로 핵심만 간결하게 줄글로 작성해.\n\n");

        // 2. 사용자 정보 주입 (개인화된 답변 유도)
        sb.append(String.format("[사용자 정보]\n- 이름: %s\n", member.getName()));
        if (member.getMemberProfile() != null) {
            sb.append(String.format("- 직업: %s\n", member.getMemberProfile().getJobCd().getJobName()));
        }
        sb.append("\n");

        // 3. (선택) 특정 정책에 대한 질문일 경우 정책 정보 주입 (RAG 효과)
        if (policy != null) {
            sb.append(String.format("[상담 대상 정책]\n- 정책명: %s\n- 내용: %s\n",
                    policy.getTitle(), policy.getAiDetailContent()));
            sb.append("위 정책 정보를 바탕으로 사용자의 질문에 답변해.\n\n");
        }

        // 4. 대화 히스토리 누적 (문맥 유지)
        if (request.getPrevUserMessage() != null && !request.getPrevUserMessage().isEmpty()) {
            sb.append("[이전 대화 내역]\n");

            List<String> prevUserMessage = request.getPrevUserMessage();
            List<String> prevBotMessage = request.getPrevBotMessage();
            for (int i = 0; i < prevUserMessage.size(); i++) {
                sb.append("user: " + prevUserMessage.get(i));
                sb.append("bot(you): " + prevBotMessage.get(i));
            }
            sb.append("\n");
        }

        // 5. 현재 질문
        sb.append(String.format("[현재 질문]\n사용자: %s\nAI:", request.getMessage()));

        return sb.toString();
    }

    // 사용자에게 해당 정책을 추천하는 이유를 묻는 프롬프트 빌더
    public String buildRecommendReasonPrompt(AiRecommendPromptContext context) {
        StringBuilder sb = new StringBuilder();

        // 1. 시스템 페르소나
        sb.append("너는 정책의 이름, 정책의 설명, 사용자의 정보를 받고 해당 정책이 사용자에게 추천되는 이유만을 뽑는 AI다.\n");
        sb.append("반드시 추천 이유를 출력해야 하고 추천되지 않는다는 말은 절대로 하면 안된다. 사용자의 거주지가 일치하는 정책만 너에게 보내는 것이므로 절대로 사용자의 거주지가 지원 범위에 해당되지 않는다는 듯한 말은 하면 안된다.\n");
        sb.append("절대로 서론, 인사말, 완료되었어요, 알겠어요 등 대답은 일절 하지 말고 오직 추천하는 이유만 출력하도록 해야 한다.\n\n");


        // 사용자 정보 및 정책 정보 주입
        sb.append("### 1. 정책 개요 (Target Policy)\n");
        sb.append(String.format("- 정책명: %s\n", context.getPolicyTitle()));
        sb.append(String.format("- 핵심 내용: %s\n\n", context.getPolicyDetail()));
        sb.append(String.format("- 혜택 내용: %s\n\n", context.getBenefitDesc()));
        sb.append(String.format("- 정책 대분류: %s\n\n", context.getCategoryName()));
        sb.append(String.format("- 학력 요건 코드: %s\n\n", context.getEduCode()));
        sb.append(String.format("- 직업 요건 코드: %s\n\n", context.getJobCode()));


        sb.append("### 2. 지원자 프로필 (Applicant Persona)\n");
        sb.append(String.format("- 직업 코드: %s\n", context.getMemberJobCode()));
        sb.append(String.format("- 학력 코드: %s\n", context.getMemberSchoolCode()));
        sb.append(String.format("- 나이: %s\n", context.getAge()));
        sb.append(String.format("- 관심 사항: %s\n", context.getInterests()));


        sb.append("내부적인 정책 추천 알고리즘은 다음과 같으니 추천하는데 참고하도록 해라\n");
        sb.append("1. 지역 필터링 - 사용자의 거주지가 정책의 지원 범위에 포함되지 않으면 절대 추천 되지 않음.\n");
        sb.append("2. 나이 필터링 - 나이 요건에 맞으면 가중치를 30점 부여\n");
        sb.append("3. 사용자 관심사 필터링 - 사용자의 관심사와 정책의 대분류가 맞으면 가중치를 30점 부여\n");
        sb.append("4. 사용자의 직업요건, 학력요건이 정책의 직업요건, 학력요건과 맞으면 가중치를 각각 20점씩 부여\n\n");

        sb.append("위와 같은 내용들을 모두 종합하여 해당 사용자에게 이 정책이 추천될만한 이유를 분석하고 간결하게 한줄로 출력해라\n");
        sb.append("(예: 이 정책은 사용자님의 관심사와 관련이 높아서 추천돼요.)");

        return sb.toString();
    }
}