import os
import requests
import json
import pymysql
import ast
import time
from dotenv import load_dotenv
from openai import OpenAI

# ---------------------------------------------------------
# [0] 데이터 정의 (보내주신 파일 내용 반영)
# ---------------------------------------------------------

# 1. 문서 표준 코드 (common_docs_mapping.py 내용)
DOC_STD_LIST = [
    {"code": "DOC_001", "name": "주민등록등본"},
    {"code": "DOC_002", "name": "주민등록초본"},
    {"code": "DOC_003", "name": "통장사본"},
    {"code": "DOC_004", "name": "가족관계증명서"},
    {"code": "DOC_005", "name": "사업자등록증"},
    {"code": "DOC_006", "name": "건강보험자격득실확인서"},
    {"code": "DOC_007", "name": "재학증명서"},
    {"code": "DOC_008", "name": "신분증사본"},
    {"code": "DOC_009", "name": "사실증명"},
    {"code": "DOC_010", "name": "재직증명서"},
    {"code": "DOC_011", "name": "근로계약서"},
    {"code": "DOC_012", "name": "임대차계약서"},
    {"code": "DOC_014", "name": "졸업증명서"},
    {"code": "DOC_015", "name": "혼인관계증명서"},
    {"code": "DOC_016", "name": "소득금액증명원"},
    {"code": "DOC_017", "name": "국민기초생활수급자증명서"},
    {"code": "DOC_018", "name": "건강보험납부확인서"},
    {"code": "DOC_019", "name": "건강보험자격확인서"},
    {"code": "DOC_020", "name": "법인등기부등본"},
    {"code": "DOC_021", "name": "경력증명서"},
    {"code": "DOC_022", "name": "병적증명서"},
    {"code": "DOC_023", "name": "건강보험료납부확인서"},
    {"code": "DOC_024", "name": "응시확인서"},
    {"code": "DOC_025", "name": "중소기업확인서"},
    {"code": "DOC_026", "name": "장애인증명서"},
    {"code": "DOC_027", "name": "결제영수증"},
    {"code": "DOC_028", "name": "고용임금확인서"},
    {"code": "DOC_029", "name": "차상위계층확인서"},
    {"code": "DOC_030", "name": "성적증명서"}
]

# 2. 지역 코드 (법정_시군구_코드.txt 내용 정제)
REGION_STD_LIST = [
    # ... (기존 데이터 유지) ...
    # 서울
    ('11110', '서울특별시 종로구'), ('11140', '서울특별시 중구'), ('11170', '서울특별시 용산구'),
    ('11200', '서울특별시 성동구'), ('11215', '서울특별시 광진구'), ('11230', '서울특별시 동대문구'),
    ('11260', '서울특별시 중랑구'), ('11290', '서울특별시 성북구'), ('11305', '서울특별시 강북구'),
    ('11320', '서울특별시 도봉구'), ('11350', '서울특별시 노원구'), ('11380', '서울특별시 은평구'),
    ('11410', '서울특별시 서대문구'), ('11440', '서울특별시 마포구'), ('11470', '서울특별시 양천구'),
    ('11500', '서울특별시 강서구'), ('11530', '서울특별시 구로구'), ('11545', '서울특별시 금천구'),
    ('11560', '서울특별시 영등포구'), ('11590', '서울특별시 동작구'), ('11620', '서울특별시 관악구'),
    ('11650', '서울특별시 서초구'), ('11680', '서울특별시 강남구'), ('11710', '서울특별시 송파구'),
    ('11740', '서울특별시 강동구'),
    # 부산
    ('26110', '부산광역시 중구'), ('26140', '부산광역시 서구'), ('26170', '부산광역시 동구'),
    ('26200', '부산광역시 영도구'), ('26230', '부산광역시 부산진구'), ('26260', '부산광역시 동래구'),
    ('26290', '부산광역시 남구'), ('26320', '부산광역시 북구'), ('26350', '부산광역시 해운대구'),
    ('26380', '부산광역시 사하구'), ('26410', '부산광역시 금정구'), ('26440', '부산광역시 강서구'),
    ('26470', '부산광역시 연제구'), ('26500', '부산광역시 수영구'), ('26530', '부산광역시 사상구'),
    ('26710', '부산광역시 기장군'),
    # 대구
    ('27110', '대구광역시 중구'), ('27140', '대구광역시 동구'), ('27170', '대구광역시 서구'),
    ('27200', '대구광역시 남구'), ('27230', '대구광역시 북구'), ('27260', '대구광역시 수성구'),
    ('27290', '대구광역시 달서구'), ('27710', '대구광역시 달성군'), ('27720', '대구광역시 군위군'),
    # 인천
    ('28110', '인천광역시 중구'), ('28140', '인천광역시 동구'), ('28177', '인천광역시 미추홀구'),
    ('28185', '인천광역시 연수구'), ('28200', '인천광역시 남동구'), ('28237', '인천광역시 부평구'),
    ('28245', '인천광역시 계양구'), ('28260', '인천광역시 서구'), ('28710', '인천광역시 강화군'),
    ('28720', '인천광역시 옹진군'),
    # 광주
    ('29110', '광주광역시 동구'), ('29140', '광주광역시 서구'), ('29155', '광주광역시 남구'),
    ('29170', '광주광역시 북구'), ('29200', '광주광역시 광산구'),
    # 대전
    ('30110', '대전광역시 동구'), ('30140', '대전광역시 중구'), ('30170', '대전광역시 서구'),
    ('30200', '대전광역시 유성구'), ('30230', '대전광역시 대덕구'),
    # 울산
    ('31110', '울산광역시 중구'), ('31140', '울산광역시 남구'), ('31170', '울산광역시 동구'),
    ('31200', '울산광역시 북구'), ('31710', '울산광역시 울주군'),
    # 세종
    ('36110', '세종특별자치시'),
    # 경기
    ('41111', '경기도 수원시 장안구'), ('41113', '경기도 수원시 권선구'), ('41115', '경기도 수원시 팔달구'),
    ('41117', '경기도 수원시 영통구'), ('41131', '경기도 성남시 수정구'), ('41133', '경기도 성남시 중원구'),
    ('41135', '경기도 성남시 분당구'), ('41150', '경기도 의정부시'), ('41171', '경기도 안양시 만안구'),
    ('41173', '경기도 안양시 동안구'), ('41190', '경기도 부천시'), ('41192', '경기도 부천시 원미구'),
    ('41194', '경기도 부천시 소사구'), ('41196', '경기도 부천시 오정구'), ('41210', '경기도 광명시'),
    ('41220', '경기도 평택시'), ('41250', '경기도 동두천시'), ('41271', '경기도 안산시 상록구'),
    ('41273', '경기도 안산시 단원구'), ('41281', '경기도 고양시 덕양구'), ('41285', '경기도 고양시 일산동구'),
    ('41287', '경기도 고양시 일산서구'), ('41290', '경기도 과천시'), ('41310', '경기도 구리시'),
    ('41360', '경기도 남양주시'), ('41370', '경기도 오산시'), ('41390', '경기도 시흥시'),
    ('41410', '경기도 군포시'), ('41430', '경기도 의왕시'), ('41450', '경기도 하남시'),
    ('41461', '경기도 용인시 처인구'), ('41463', '경기도 용인시 기흥구'), ('41465', '경기도 용인시 수지구'),
    ('41480', '경기도 파주시'), ('41500', '경기도 이천시'), ('41550', '경기도 안성시'),
    ('41570', '경기도 김포시'), ('41590', '경기도 화성시'), ('41610', '경기도 광주시'),
    ('41630', '경기도 양주시'), ('41650', '경기도 포천시'), ('41670', '경기도 여주시'),
    ('41800', '경기도 연천군'), ('41820', '경기도 가평군'), ('41830', '경기도 양평군'),
    # 충북
    ('43111', '충청북도 청주시 상당구'), ('43112', '충청북도 청주시 서원구'), ('43113', '충청북도 청주시 흥덕구'),
    ('43114', '충청북도 청주시 청원구'), ('43130', '충청북도 충주시'), ('43150', '충청북도 제천시'),
    ('43720', '충청북도 보은군'), ('43730', '충청북도 옥천군'), ('43740', '충청북도 영동군'),
    ('43745', '충청북도 증평군'), ('43750', '충청북도 진천군'), ('43760', '충청북도 괴산군'),
    ('43770', '충청북도 음성군'), ('43800', '충청북도 단양군'),
    # 충남
    ('44131', '충청남도 천안시 동남구'), ('44133', '충청남도 천안시 서북구'), ('44150', '충청남도 공주시'),
    ('44180', '충청남도 보령시'), ('44200', '충청남도 아산시'), ('44210', '충청남도 서산시'),
    ('44230', '충청남도 논산시'), ('44250', '충청남도 계룡시'), ('44270', '충청남도 당진시'),
    ('44710', '충청남도 금산군'), ('44760', '충청남도 부여군'), ('44770', '충청남도 서천군'),
    ('44790', '충청남도 청양군'), ('44800', '충청남도 홍성군'), ('44810', '충청남도 예산군'),
    ('44825', '충청남도 태안군'),
    # 전남
    ('46110', '전라남도 목포시'), ('46130', '전라남도 여수시'), ('46150', '전라남도 순천시'),
    ('46170', '전라남도 나주시'), ('46230', '전라남도 광양시'), ('46710', '전라남도 담양군'),
    ('46720', '전라남도 곡성군'), ('46730', '전라남도 구례군'), ('46770', '전라남도 고흥군'),
    ('46780', '전라남도 보성군'), ('46790', '전라남도 화순군'), ('46800', '전라남도 장흥군'),
    ('46810', '전라남도 강진군'), ('46820', '전라남도 해남군'), ('46830', '전라남도 영암군'),
    ('46840', '전라남도 무안군'), ('46860', '전라남도 함평군'), ('46870', '전라남도 영광군'),
    ('46880', '전라남도 장성군'), ('46890', '전라남도 완도군'), ('46900', '전라남도 진도군'),
    ('46910', '전라남도 신안군'),
    # 경북
    ('47111', '경상북도 포항시 남구'), ('47113', '경상북도 포항시 북구'), ('47130', '경상북도 경주시'),
    ('47150', '경상북도 김천시'), ('47170', '경상북도 안동시'), ('47190', '경상북도 구미시'),
    ('47210', '경상북도 영주시'), ('47230', '경상북도 영천시'), ('47250', '경상북도 상주시'),
    ('47280', '경상북도 문경시'), ('47290', '경상북도 경산시'), ('47730', '경상북도 의성군'),
    ('47750', '경상북도 청송군'), ('47760', '경상북도 영양군'), ('47770', '경상북도 영덕군'),
    ('47820', '경상북도 청도군'), ('47830', '경상북도 고령군'), ('47840', '경상북도 성주군'),
    ('47850', '경상북도 칠곡군'), ('47900', '경상북도 예천군'), ('47920', '경상북도 봉화군'),
    ('47930', '경상북도 울진군'), ('47940', '경상북도 울릉군'),
    # 경남
    ('48121', '경상남도 창원시 의창구'), ('48123', '경상남도 창원시 성산구'), ('48125', '경상남도 창원시 마산합포구'),
    ('48127', '경상남도 창원시 마산회원구'), ('48129', '경상남도 창원시 진해구'), ('48170', '경상남도 진주시'),
    ('48220', '경상남도 통영시'), ('48240', '경상남도 사천시'), ('48250', '경상남도 김해시'),
    ('48270', '경상남도 밀양시'), ('48310', '경상남도 거제시'), ('48330', '경상남도 양산시'),
    ('48720', '경상남도 의령군'), ('48730', '경상남도 함안군'), ('48740', '경상남도 창녕군'),
    ('48820', '경상남도 고성군'), ('48840', '경상남도 남해군'), ('48850', '경상남도 하동군'),
    ('48860', '경상남도 산청군'), ('48870', '경상남도 함양군'), ('48880', '경상남도 거창군'),
    ('48890', '경상남도 합천군'),
    # 제주
    ('50110', '제주특별자치도 제주시'), ('50130', '제주특별자치도 서귀포시'),
    # 강원
    ('51110', '강원특별자치도 춘천시'), ('51130', '강원특별자치도 원주시'), ('51150', '강원특별자치도 강릉시'),
    ('51170', '강원특별자치도 동해시'), ('51190', '강원특별자치도 태백시'), ('51210', '강원특별자치도 속초시'),
    ('51230', '강원특별자치도 삼척시'), ('51720', '강원특별자치도 홍천군'), ('51730', '강원특별자치도 횡성군'),
    ('51750', '강원특별자치도 영월군'), ('51760', '강원특별자치도 평창군'), ('51770', '강원특별자치도 정선군'),
    ('51780', '강원특별자치도 철원군'), ('51790', '강원특별자치도 화천군'), ('51800', '강원특별자치도 양구군'),
    ('51810', '강원특별자치도 인제군'), ('51820', '강원특별자치도 고성군'), ('51830', '강원특별자치도 양양군'),
    # 전북
    ('52111', '전북특별자치도 전주시 완산구'), ('52113', '전북특별자치도 전주시 덕진구'),
    ('52130', '전북특별자치도 군산시'), ('52140', '전북특별자치도 익산시'), ('52180', '전북특별자치도 정읍시'),
    ('52190', '전북특별자치도 남원시'), ('52210', '전북특별자치도 김제시'), ('52710', '전북특별자치도 완주군'),
    ('52720', '전북특별자치도 진안군'), ('52730', '전북특별자치도 무주군'), ('52740', '전북특별자치도 장수군'),
    ('52750', '전북특별자치도 임실군'), ('52770', '전북특별자치도 순창군'), ('52790', '전북특별자치도 고창군'),
    ('52800', '전북특별자치도 부안군')
]

# ---------------------------------------------------------
# [New] 학력/취업 코드 유효성 검증용 데이터 (DB Master 참조)
# ---------------------------------------------------------
SCHOOL_STD_SET = {
    "0049001", "0049002", "0049003", "0049004", "0049005",
    "0049006", "0049007", "0049008", "0049009", "0049010"
}

JOB_STD_SET = {
    "0013001", "0013002", "0013003", "0013004", "0013005",
    "0013006", "0013007", "0013008", "0013009", "0013010"
}

# (Logic 2용 참조 데이터 - 기존 유지)
try:
    from reference_data import REFERENCE_DATA
except ImportError:
    REFERENCE_DATA = {
        "2025_min_wage": 10030,
        "standard_income": {"1_person": 2336630}
    }

# ---------------------------------------------------------
# [0] 설정 및 상수 정의
# ---------------------------------------------------------
load_dotenv()

CONFIG = {
    "ontong": {
        "api_key": os.getenv("ONTONG_API_KEY"),
        "base_url": "https://www.youthcenter.go.kr/go/ythip/getPlcy",
    },
    "gms": {
        "api_key": os.getenv("GMS_KEY"),
        "base_url": "https://gms.ssafy.io/gmsapi/api.openai.com/v1",
        "model": "gpt-4o-mini", 
    },
    "db": {
        "host": "localhost",
        "port": 3306,
        "user": "root",
        "password": os.getenv("MYSQL_PASSWORD"),
        "db": "testv3", # DB명 확인
        "charset": "utf8mb4",
        "cursorclass": pymysql.cursors.DictCursor
    }
}

client = OpenAI(api_key=CONFIG["gms"]["api_key"], base_url=CONFIG["gms"]["base_url"])

# ---------------------------------------------------------
# [Helper] 유틸리티 함수
# ---------------------------------------------------------
def clean_date_str(date_str):
    """YYYYMMDD 형식이 아니면 NULL 반환"""
    if not date_str: return None
    clean = str(date_str).strip()
    return clean if (len(clean) == 8 and clean.isdigit()) else None

def parse_split_list(text):
    """쉼표로 구분된 문자열을 리스트로 변환"""
    if not text: return []
    return [x.strip() for x in str(text).split(',') if x.strip()]

def safe_json_parse(content, default_value):
    """JSON 파싱 실패 시 ast.literal_eval 시도 및 기본값 반환"""
    text = content.replace("```json", "").replace("```", "").strip()
    try:
        return json.loads(text)
    except:
        try:
            return ast.literal_eval(text)
        except:
            return default_value

# ---------------------------------------------------------
# [Pre-Check 1] 공통 서류 목록 DB 동기화
# ---------------------------------------------------------
def init_common_docs_db():
    print("🛠️ [Init] 공통 서류 목록 DB 동기화 중...")
    conn = pymysql.connect(**CONFIG["db"])
    cursor = conn.cursor()
    try:
        # docs_list 테이블에 INSERT (이미 있으면 무시)
        sql = """
            INSERT INTO docs_list (doc_code, docs_name) 
            VALUES (%s, %s)
            ON DUPLICATE KEY UPDATE docs_name = VALUES(docs_name)
        """
        for doc in DOC_STD_LIST:
            cursor.execute(sql, (doc['code'], doc['name']))
        conn.commit()
        print(f"   ✅ {len(DOC_STD_LIST)}개 서류 코드 동기화 완료.")
    except Exception as e:
        print(f"   ❌ 서류 코드 초기화 실패: {e}")
    finally:
        conn.close()

# ---------------------------------------------------------
# [Pre-Check 2] 지역 코드 DB 동기화 (★추가됨)
# ---------------------------------------------------------
def init_region_codes_db():
    print("🛠️ [Init] 지역 코드(region_codes) DB 동기화 중...")
    conn = pymysql.connect(**CONFIG["db"])
    cursor = conn.cursor()
    try:
        # region_codes 테이블에 INSERT (이미 있으면 무시)
        sql = """
            INSERT INTO region_codes (region_code, region_name) 
            VALUES (%s, %s)
            ON DUPLICATE KEY UPDATE region_name = VALUES(region_name)
        """
        for code, name in REGION_STD_LIST:
            cursor.execute(sql, (code, name))
        conn.commit()
        print(f"   ✅ {len(REGION_STD_LIST)}개 지역 코드 동기화 완료.")
    except Exception as e:
        print(f"   ❌ 지역 코드 초기화 실패: {e}")
    finally:
        conn.close()

# ---------------------------------------------------------
# [AI 1] Task A & B: 상세 내용 및 신청 요약 생성
# ---------------------------------------------------------
def generate_content_and_summary(context_text):
    system_msg = """
    너는 청년 정책 데이터를 가공하는 AI 에디터다.
    제공된 정책 원문 데이터를 바탕으로 아래 두 가지 포맷의 결과물을 JSON으로 생성하라.

    [요구사항 1: ai_detail_content (Markdown)]
    - 긴 줄글을 읽기 쉽게 구조화된 Markdown으로 변환하라.
    - 목차: "💡 정책 한줄 요약", "💰 지원 혜택", "🧑‍🤝‍🧑 신청 자격", "📅 기간 및 절차"
    - 혜택 금액이나 중요 날짜는 볼드체(**) 처리하라.

    [요구사항 2: ai_apply_summary (Text)]
    - 신청자가 가장 궁금해할 행동 지침을 3줄로 요약하라.
    - 이모지 사용 가능. HTML 태그 금지.
    - 형식:
      1. 🔗 신청 방법: (온라인/방문 등 + 사이트명)
      2. 📝 핵심 서류: (대표 서류 2~3개)
      3. 🚨 마감 기한: (날짜 또는 '예산 소진 시')

    [Output Format (JSON)]
    {
        "ai_detail_content": "# 💡 정책 한줄 요약...",
        "ai_apply_summary": "1. 🔗 신청 방법: ..."
    }
    """
    
    try:
        res = client.chat.completions.create(
            model=CONFIG["gms"]["model"],
            messages=[
                {"role": "system", "content": system_msg},
                {"role": "user", "content": f"[정책 원문 데이터]\n{context_text}"}
            ],
            temperature=0.3
        )
        return safe_json_parse(res.choices[0].message.content, {"ai_detail_content": "", "ai_apply_summary": ""})
    except Exception as e:
        print(f"    ⚠️ AI(Task A/B) Error: {e}")
        return {"ai_detail_content": "", "ai_apply_summary": ""}

# ---------------------------------------------------------
# [AI 2] Logic 2: 최대 이익금 계산
# ---------------------------------------------------------
def analyze_benefit_amount(benefit_text):
    # 상단 import 변수 사용
    ref_json = json.dumps(REFERENCE_DATA, ensure_ascii=False)
    
    system_msg = f"""
    당신은 정책 데이터를 분석하여 '1인당 받을 수 있는 이론상 최대 총액(KRW)'을 산출하는 **수석 회계사**입니다.
    아래 [참조 데이터]와 [계산 프로토콜]을 엄격히 준수하여 **최종 결과값(정수)**만 도출하세요.

    [참조 데이터 (기준값)]
    {ref_json}

    [계산 프로토콜 (Strict Logic)]
    1. **단가(Unit Price) 확정**:
       - 텍스트에 명시된 금액(예: 월 20만원) 최우선 적용.
       - '최저임금', '생활임금' 키워드 등장 시 **[참조 데이터]** 값 대입. (미래 연도인 경우 최신 데이터 사용)
    
    2. **수량(Quantity) & 기간(Duration) 확정**:
       - '주 5일', '1일 6시간' -> 1주 = 30시간.
       - '4주간' -> 4주. (한 달은 4주로 계산하지 말고 텍스트 그대로 적용)
       - 기간 미명시 '월 지원금' -> '12개월' 간주.
       - 기간 미명시 '일회성' -> '1회' 간주.

    3. **함정 방어**:
       - "총 사업비 10억", "2,000명 선발" 등 전체 예산 사용 금지.
       - 대출 상품: (대출한도 × 이자지원율 × 지원기간)을 계산. 이자율 불명확 시 0원.

    4. **★중요★ 계산 수행 (Calculation Execution)**:
       - AI 내부적으로 곱셈/덧셈 연산을 **완료**할 것.
       - **절대 수식(예: 10000 * 12)을 출력하지 마세요.**
       - **무조건 연산이 완료된 하나의 정수(Integer) 값만 반환하세요.**

    [Output Validation (Examples)]
    - ❌ Bad: {{"max_amount": "10000 * 12", ...}} (수식 금지)
    - ❌ Bad: {{"max_amount": "120,000", ...}} (콤마/문자열 금지)
    - ✅ Good: {{"max_amount": 120000, ...}} (순수 정수)

    [Output Format (JSON Only)]
    {{
        "max_amount": 1348800, 
        "is_cash_benefit": true, 
        "calc_logic": "2025 대전생활임금(11,240원) x 6시간 x 5일 x 4주 = 1,348,800원"
    }}
    """
    
    try:
        res = client.chat.completions.create(
            model=CONFIG["gms"]["model"], 
            messages=[
                {"role": "system", "content": system_msg},
                {"role": "user", "content": f"혜택 텍스트 분석:\n{benefit_text}"}
            ],
            temperature=0.0 # 계산 정확도 최우선
        )
        
        # 1차 파싱
        parsed = safe_json_parse(res.choices[0].message.content, {"max_amount": 0, "is_cash_benefit": False})
            
        return parsed

    except Exception as e:
        print(f"    ⚠️ Benefit Calc Error: {e}")
        return {"max_amount": 0, "is_cash_benefit": False}

# ---------------------------------------------------------
# [AI 3] Logic 3: 서류 분류 및 Action Type 지정
# ---------------------------------------------------------
def analyze_documents(doc_text):
    if not doc_text or len(str(doc_text)) < 2: return []
    
    std_docs = json.dumps(DOC_STD_LIST, ensure_ascii=False)
    system_msg = f"""
    [표준 서류 목록] {std_docs}
    
    제출 서류 텍스트를 분석하여 JSON 리스트로 변환하라.
    
    [규칙]
    1. 표준 목록에 있는 서류와 유사하면 -> code 매핑, action_type="ISSUE"(발급)
    2. 신청서, 계획서, 자소서 등 작성 필요 서류 -> code=null, action_type="FORM"(서식)
    3. 파일 업로드만 필요한 기타 서류 -> action_type="UPLOAD"
    
    Output Example: 
    [{{"name": "주민등록등본", "code": "DOC_001", "action_type": "ISSUE"}}, 
     {{"name": "사업계획서", "code": null, "action_type": "FORM"}}]
    """
    try:
        res = client.chat.completions.create(
            model=CONFIG["gms"]["model"],
            messages=[
                {"role": "system", "content": system_msg},
                {"role": "user", "content": f"제출서류: {doc_text}"}
            ],
            temperature=0.1
        )
        return safe_json_parse(res.choices[0].message.content, [])
    except Exception:
        return []

# ---------------------------------------------------------
# [Main] DB 적재 파이프라인
# ---------------------------------------------------------
def run_pipeline():
    init_common_docs_db()
    init_region_codes_db()

    conn = pymysql.connect(**CONFIG["db"])
    page = 1
    total_processed = 0
    total_skipped = 0

    print("🚀 [Ontong ETL] 파이프라인 가동 시작...")

    try:
        while True:
            print(f"\n📡 Page {page} 데이터 수집 중...")
            
            params = {
                "apiKeyNm": CONFIG["ontong"]["api_key"],
                "pageNum": page,
                "pageSize": 20, 
                "rtnType": "json"
            }
            
            # [수정] 재시도(Retry) 로직
            max_retries = 3
            retry_delay = 5
            success = False
            data = None
            
            for attempt in range(max_retries):
                try:
                    res = requests.get(CONFIG["ontong"]["base_url"], params=params, timeout=30)
                    if res.status_code != 200:
                        print(f"    ⚠️ [Retry {attempt+1}/{max_retries}] 서버 상태 이상 ({res.status_code})...")
                        time.sleep(retry_delay)
                        continue
                    
                    try:
                        data = res.json()
                        success = True
                        break
                    except json.JSONDecodeError:
                        print(f"    ⚠️ [Retry {attempt+1}/{max_retries}] JSON 파싱 실패 (HTML 응답 가능성)")
                        time.sleep(retry_delay)
                        continue
                        
                except Exception as e:
                    print(f"    ⚠️ [Retry {attempt+1}/{max_retries}] 네트워크 오류: {e}")
                    time.sleep(retry_delay)
                    continue

            if not success:
                print(f"❌ Page {page} 조회 최종 실패. 다음 페이지로 이동.")
                page += 1
                continue

            # 데이터 추출
            raw_policies = []
            if "youthPolicyList" in data:
                raw_policies = data["youthPolicyList"]
            elif "result" in data and "youthPolicyList" in data["result"]:
                raw_policies = data["result"]["youthPolicyList"]
            
            if not raw_policies:
                print("🏁 더 이상 데이터가 없습니다. (End of Data)")
                break

            # --- 배치 처리 ---
            for p in raw_policies:
                policy_id = p.get('plcyNo')
                title = p.get('plcyNm')
                prd_code = p.get('aplyPrdSeCd')
                
                # 🛑 [Filter 1] 마감된 정책(0057003) 스킵
                if prd_code == '0057003':
                    print(f"    🚫 [SKIP-Closed] {title}")
                    total_skipped += 1
                    continue

                # 🛑 [Filter 2] ★ DB 중복 체크 (AI 비용 절약)
                check_cursor = conn.cursor()
                check_cursor.execute("SELECT 1 FROM policies WHERE policy_id = %s", (policy_id,))
                exists = check_cursor.fetchone()
                check_cursor.close()

                if exists:
                    print(f"    ⏭️ [SKIP-Exists] {title} (이미 DB에 존재)")
                    total_skipped += 1
                    continue
                
                print(f"    ▶️ [Processing] {title}")
                
                # 1. 텍스트 데이터 준비
                doc_text = p.get('sbmsnDcmntCn', '')
                context_text = f"""
                정책명: {title}
                소개: {p.get('plcyExplnCn')}
                지원내용: {p.get('plcySprtCn')}
                신청기간: {p.get('aplyYmd')}
                신청방법: {p.get('plcyAplyMthdCn')}
                제출서류: {doc_text}
                자격요건: {p.get('addAplyQlfcCndCn')} {p.get('earnEtcCn')}
                기타사항: {p.get('etcMttrCn')}
                """
                
                # 2. AI 호출
                ai_content = generate_content_and_summary(context_text)
                ai_benefit = analyze_benefit_amount(p.get('plcySprtCn', ''))
                ai_docs = analyze_documents(doc_text)
                
                # 3. DB Transaction
                cursor = conn.cursor()
                try:
                    # (1) Policies Table
                    sql_policy = """
                        INSERT INTO policies (
                            policy_id, title, introduction, category_name,
                            apply_period, apply_period_code, start_date, end_date, biz_period_text,
                            host_inst, submission_doc_text, add_qual_text, apply_method_text,
                            screening_method_text, etc_text,
                            ai_detail_content, ai_apply_summary,
                            site_link, ref_link_1, ref_link_2,
                            job_code, edu_code, major_code, splz_code, marriage_code,
                            view_count
                        ) VALUES (
                            %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                            %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                            %s
                        ) ON DUPLICATE KEY UPDATE
                            title=VALUES(title),
                            ai_detail_content=VALUES(ai_detail_content),
                            ai_apply_summary=VALUES(ai_apply_summary),
                            view_count=VALUES(view_count)
                    """
                    cursor.execute(sql_policy, (
                        policy_id, title, p.get('plcyExplnCn'), p.get('lclsfNm'),
                        p.get('aplyYmd'), prd_code, clean_date_str(p.get('bizPrdBgngYmd')), clean_date_str(p.get('bizPrdEndYmd')), p.get('bizPrdEtcCn'),
                        p.get('sprvsnInstCdNm'), doc_text, p.get('addAplyQlfcCndCn'), p.get('plcyAplyMthdCn'),
                        p.get('srngMthdCn'), p.get('etcMttrCn'),
                        ai_content['ai_detail_content'], ai_content['ai_apply_summary'],
                        p.get('aplyUrlAddr'), p.get('refUrlAddr1'), p.get('refUrlAddr2'),
                        p.get('jobCd'), p.get('schoolCd'), p.get('plcyMajorCd'), p.get('sBizCd'), p.get('mrgSttsCd'),
                        int(p.get('inqCnt', 0))
                    ))

                    # (2) Policy_Regions
                    cursor.execute("DELETE FROM policy_regions WHERE policy_id=%s", (policy_id,))
                    regions = parse_split_list(p.get('zipCd'))
                    if regions:
                        cursor.executemany("INSERT INTO policy_regions (policy_id, region_code) VALUES (%s, %s)", [(policy_id, r) for r in regions])

                    # (3) Policy_Benefits
                    cursor.execute("DELETE FROM policy_benefits WHERE policy_id=%s", (policy_id,))
                    min_age = int(p['sprtTrgtMinAge']) if str(p['sprtTrgtMinAge']).isdigit() else None
                    max_age = int(p['sprtTrgtMaxAge']) if str(p['sprtTrgtMaxAge']).isdigit() else None
                    
                    cursor.execute("""
                        INSERT INTO policy_benefits (
                            policy_id, benefit_desc, max_amount, is_cash_benefit,
                            min_age, max_age, age_info, income_range_txt, income_code
                        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
                    """, (
                        policy_id, p.get('plcySprtCn'), ai_benefit['max_amount'], ai_benefit['is_cash_benefit'],
                        min_age, max_age, p.get('sprtTrgtAgeLmtYn'), p.get('earnEtcCn'), p.get('earnCndSeCd')
                    ))

                    # (4) Policy_Required_Docs
                    cursor.execute("DELETE FROM policy_required_docs WHERE policy_id=%s", (policy_id,))
                    if ai_docs:
                        doc_values = []
                        for d in ai_docs:
                            d_code = d.get('code')
                            doc_values.append((policy_id, d.get('name'), d_code, d.get('action_type', 'UPLOAD')))
                        cursor.executemany("INSERT INTO policy_required_docs (policy_id, doc_name, doc_type_code, action_type) VALUES (%s, %s, %s, %s)", doc_values)

                    # ------------------------------------------------------------------------------------------
                    # [NEW] 중개 테이블 로직 추가 (학력 및 취업 코드 파싱/필터링/적재)
                    # ------------------------------------------------------------------------------------------
                    
                    # (5) Policy_Target_Education (학력 중개 테이블)
                    cursor.execute("DELETE FROM policy_target_education WHERE policy_id=%s", (policy_id,))
                    raw_edu = parse_split_list(p.get('schoolCd')) # "0049001,0049002" -> List
                    valid_edu = [c for c in raw_edu if c in SCHOOL_STD_SET] # 마스터 코드 유효성 검사
                    if valid_edu:
                        cursor.executemany(
                            "INSERT INTO policy_target_education (policy_id, school_cd) VALUES (%s, %s)",
                            [(policy_id, c) for c in valid_edu]
                        )

                    # (6) Policy_Target_Job (취업 중개 테이블)
                    cursor.execute("DELETE FROM policy_target_job WHERE policy_id=%s", (policy_id,))
                    raw_job = parse_split_list(p.get('jobCd')) # "0013001,0013002" -> List
                    valid_job = [c for c in raw_job if c in JOB_STD_SET] # 마스터 코드 유효성 검사
                    if valid_job:
                        cursor.executemany(
                            "INSERT INTO policy_target_job (policy_id, job_cd) VALUES (%s, %s)",
                            [(policy_id, c) for c in valid_job]
                        )
                    # ------------------------------------------------------------------------------------------

                    conn.commit()
                    total_processed += 1
                    print("        ✅ Saved")

                except Exception as db_err:
                    conn.rollback()
                    print(f"        ❌ DB Fail: {db_err}")

            page += 1
            time.sleep(1)

    finally:
        conn.close()
        print(f"\n✨ ETL 완료 Report")
        print(f" - 저장된 정책: {total_processed}건")
        print(f" - 건너뜀(마감/중복): {total_skipped}건")

if __name__ == "__main__":
    run_pipeline()