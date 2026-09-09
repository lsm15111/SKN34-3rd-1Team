# GovBiz 서비스 호출·데이터 흐름

[문서 목록](README.md) · [아키텍처 README](architecture/README.md)

현재 production 코드의 서비스 경계와 실행 흐름을 설명합니다. 계층·DI·디자인 패턴은
[아키텍처 README](architecture/README.md), 기술·버전은 [기술 구성](technology.md),
완료 기능과 남은 제약은 [구현 현황](implementation-status.md), 환경 설정은
[인프라 README](../infrastructure/README.md)를 참고하세요.

## 서비스 경계

```text
브라우저 → React Web → Core API
                       ├→ MySQL: 현재 공개 공고 카탈로그·공고별 공식 원문
                       ├→ 공공데이터포털: 기업마당·K-Startup 공고 수집
                       ├→ 기업마당 공식 HTTPS 상세 페이지: 명시적 원문 질문 시 HTML 수집
                       └→ AI Service
                           ├→ OpenAI: 문서·질의 임베딩, 조건 변경 해석·후보 점수화·근거 답변
                           └→ Qdrant: 공고 검색·원문 근거 청크의 분리된 벡터 컬렉션
```

Core API는 공개 HTTP 계약, 기업마당·K-Startup 수집, MySQL 접근과 접수 상태 계산을 소유합니다. AI Service는
Core가 전달한 공고 문서·원문 청크의 색인·검색·점수화·근거 답변을 담당하며 MySQL에 직접 접근하지 않습니다.

브라우저는 Core API의 `/api`만 호출합니다. Compose에서 Vite는 `/api`를 `core-api:8080`으로 프록시하며,
AI Service는 호스트에 포트를 게시하지 않습니다. MySQL·Qdrant·Core API·Web의 개발용 포트는
`127.0.0.1`에 바인딩합니다. 기업마당·K-Startup 키는 Core API에, OpenAI 키는 AI Service에만 주입합니다.
이는 개발 환경의 서비스 배치이며 운영 인증·접근 제어가 구현됐다는 의미는 아닙니다.

## 검색·상세 조회·원문 근거 질문

공개 대화 해석·검색·근거 질문은 입력 검증 뒤 Controller에서 `SupportProgramRequestAdmissionService`를 거쳐
기존 업무 Service를 실행합니다. 하나의 Bean이 접속 주소별/전체 최근 60초 및 동시 작업 한도를 공유하며,
거절 시 하위 Service를 호출하지 않고 429 또는 503을 반환합니다. 잠금은 입장 판단·카운터 갱신에만 사용하고
외부 호출 중에는 유지하지 않습니다. `finally`로 정상·예외 종료 모두 동시 슬롯을 반환합니다.
준비 상태·상세 GET·Health·백그라운드 동기화·비웹 평가는 이 공개 제한과 분리합니다.
전달 헤더를 기본 신뢰하지 않으며 Compose 프록시/NAT 뒤에서는 주소별 한도를 공유할 수 있습니다.
단일 프로세스 보호이며 분산 한도·전역 비용 상한은 아닙니다. [설정·경계·검증](support-program-request-limits.md)을 참고하세요.

### 후속 대화의 조건 변경 해석

`POST /api/v1/support-programs/conversation/interpret`는 작은 현재 상태·새 메시지·선택적인 미확정 초안과
마지막 질문 하나만 처리합니다. Core의 전용 Service와 AI Client를 통해 AI Service의 조건 해석 Agent를 호출하고,
변경 목록을 검증·병합해 제안 또는 보완 질문을 반환합니다. 전체 상태 재작성으로 조건이 누락되지 않도록
변경 목록에 없는 필드는 코드로 유지합니다. 변경 전후 필드 목록은 Core가 계산합니다.

이 단계는 Search Service·Repository·MySQL·Qdrant를 호출하지 않습니다. Web은 미확정 제안을 별도로 보관하고
사용자가 확인했을 때만 조건을 적용해 아래 기존 POST 검색을 실행합니다. 해석과 확인 검색은 별개의 공개 요청입니다.
질문·오류·취소는 확정 조건을 바꾸지 않으며, 사용자가 확인한 검색 실패의 재시도는 다시 해석하지 않습니다.
서버 대화 세션·영구 프로필·무제한 이력·Agent graph는 추가하지 않습니다.
[C02 계약·검증 기록](conversation-condition-update.md)에 상태·문자·날짜·실패 경계를 명시합니다.

### 확인된 조건의 검색

```text
POST /api/v1/support-programs/search (조건 검색)
GET /api/v1/support-programs/search (기존 단문·최신 목록)
  → SupportProgramController
    → SupportProgramSearchService
      → SupportProgramRepository → MyBatis Mapper → Mapper XML → MySQL
        → 빈 검색: findPublishedPresent로 공개 세대·지문이 있는 DB 공고 선택
        → 자연어 검색: findSearchablePresent로 위 조건에 index_ready=true 추가
      → 접수 상태 계산·필터
      ├→ 빈 검색어: 최신순 최대 5개 반환
      └→ 검색어 있음:
          AiSupportProgramRetrievalFacade → AiSupportProgramIndexClient
            → AI Service → 현재 색인 검증 → 질의 임베딩 캐시/OpenAI → Qdrant 후보 최대 20개
            → Core: 전체 적격 공고의 키워드 상위 20개와 RRF 결합 → 후보 최대 20개
          AiSupportProgramRankingFacade → HttpAiSupportProgramRankingClient
            → AI Service → 정확일치 랭킹 응답 캐시, 없으면 단일 Agent → OpenAI 점수화·검증
          Core의 응답 검증 → 최종 추천 0~5개
```

Web의 POST 검색은 `query`와 선택적인 `companyConditions`를 따로 보냅니다. Core의 공개 DTO는
날짜·길이·문자 입력을 검증한 뒤 조건 Domain 모델로 변환합니다. 검색 Service는 요청별 서울 날짜를
한 번 정하고, 후보 검색에는 조건을 포함한 검색문을, Ranking Facade에는 원래 질의와 구조화된 조건을
전달합니다. Facade가 AI 전용 DTO로 바꿉니다. 조건은 Repository에 저장하지 않으며 SQL·동기화·스키마는
변경하지 않습니다. 조건이 없는 GET 및 비웹 평가 호출은 기존 단문 경로를 유지합니다.

1. Repository는 `is_source_present = TRUE`이고 제공처의 공개 세대·지문이 있는 공고를 읽습니다. 자연어 검색은
   `index_ready = TRUE`도 요구하며, 빈 검색은 공개 이후 색인 장애와 무관하게 기존 DB 목록을 유지합니다.
   저장된 신청 기간과 서울 날짜로 접수 상태를 다시 계산하고 `acceptingOnly=true`이면 `OPEN`만 남깁니다.
2. 검색어는 앞뒤 공백을 제거합니다. 검색어가 비어 있으면 `source_sort_timestamp` 내림차순·제공처 코드·원본 ID
   오름차순의 최대 5개를 AI 없이 반환합니다. 자연어 검색의 빈 후보가 전체 색인 장애 때문이면 503으로 알리고,
   최초 빈 DB나 준비된 제공처의 정상 0건이면 빈 목록을 반환합니다. 미공개 제공처 데이터는 노출하지 않습니다.
3. 비어 있지 않은 질의는 대상 공고 전체의 정확한 ID·내용 해시를 AI Service에 전달합니다.
   최신 공고 20개를 먼저 자르지 않습니다. Qdrant가 반환해야 할 개수는 `min(대상 공고 수, 20)`입니다.
4. `AiSupportProgramRetrievalFacade`는 의미 검색 응답의 질의·ID·해시·중복·유한 점수·내림차순·개수를
   검증한 뒤 전체 적격 공고의 동일 색인 본문에서 키워드 상위 20개를 구합니다. 질의와 본문을 NFC로
   정규화하고 `Locale.ROOT` 소문자 변환 후 `[a-z0-9가-힣]+` 토큰 집합의 교집합 수를 내림차순으로
   정렬합니다. 일치 토큰이 없는 공고는 제외하고, 동점은 정렬 시각 내림차순·제공처 포함 ID 오름차순입니다.
   의미 검색과 키워드의 1부터 시작하는 순위를 동일 가중치 RRF `1 / (60 + 순위)`로 합산하고,
   동점은 의미 검색 순위·제공처 포함 ID 오름차순으로 정렬해 중복 없는 최대 20개를 점수화에 전달합니다.
   의미 검색 응답이 실패하거나 잘못됐으면 오류를 반환합니다. 키워드 일치가 없으면 의미 검색 순서를 유지합니다.
5. AI Service는 전체 입력이 같은 검증된 랭킹 응답을 재사용하거나 모든 후보를 다시 평가합니다.
   AI는 후보의 의미·자격·세부 점수를 판단하고 총점은 출력하지 않습니다.
   요청별 strict schema의 `rankings`는 후보 ID 자체를 필수 키로 선언한 객체이며 다른 키는 금지합니다.
   Agent가 후보별 원문 조각 선택지를 제공하고, LLM은 인용문을 재작성하지 않고 근거 번호만 선택합니다.
   번호를 해당 후보의 원래 필드·문구로 복원한 뒤 검증된 키를 ID로 붙여 `AssessedSupportProgram` 목록으로 변환합니다.
   AI Service가 `2 × (semanticRelevance + supportTypeFit)`으로 관련도를 계산해 `ScoredSupportProgram`으로 변환한 뒤
   원문 인용과 자격·추천 기준을 검증하고 관련도 내림차순으로 정렬합니다. 자격 미확인은 별도로 표시합니다.
   Core도 최종 응답의 후보 ID·질의·계약 버전·점수·순서·추천 이유·실제 본문 인용을 재검증해
   비저장 검색 결과 `eligibilityReview`를 포함한 공개 응답으로 변환합니다.

AI의 두 캐시는 프로세스 안의 서비스 인스턴스에만 속합니다. 질의 임베딩은 정확한 입력 문자열별
최대 256개·300초, 랭킹 응답은 질의·조건·기준일·전체 후보 본문/메타데이터/순서·결과 제한·계약 버전이
같을 때 최대 128개·300초 재사용합니다. 모델·프롬프트·전처리 정책이 고정된 인스턴스끼리도 공유하지
않습니다. 전체 검색 결과 캐시는 아니며 Core의 현재 DB 조회·접수 상태 계산과 AI의 collection/전체 버전
검증·Qdrant 후보 조회는 매번 실행합니다. 준비 실패 시 캐시 조회 전에 오류를 반환합니다.
두 캐시는 검증된 정상 값만 복사 저장·반환하며 TTL은 성공 시점부터 계산합니다. 만료는 재사용 차단이며
물리적인 즉시 삭제 보장이 아닙니다. 같은 입력의 동시 요청만 합류하고 다른 입력은 병렬 처리합니다.
랭킹 대기자 하나의 취소는 공유 작업을 유지하고 마지막 대기자의 취소는 작업을 종료합니다. 임베딩 수행
요청이 취소되면 남은 요청이 다시 실행합니다. 실패는 저장하지 않으며 기존 오류·후보 수·배점·시간 상한은
유지합니다. 상세 범위는 [검색 지연 개선 기록](search-latency-20260908.md)을 참고하세요.

```text
GET /api/v1/support-programs/readiness
  → SupportProgramController → SupportProgramSearchReadinessService
  → SupportProgramRepository → MyBatis Mapper → Mapper XML → MySQL
  → 제공처별 공고 수·색인 준비·최근 동기화 시각과 전체 검색 범위를 반환
```

`support_program_sync_status`는 제공처별 공개 스냅샷의 세대·검색 문서 지문·공고 수와 색인 준비 상태를
최근 카탈로그 동기화 결과와 분리해 보관합니다. 제공처별 `indexReady=true`이면 공고 수가 0이어도 검색 가능합니다.
색인이 준비된 이전 스냅샷을 유지한 채 새 수집·사전 색인이 실패하면 `SEARCHABLE_WITH_SYNC_FAILURE`이며,
색인 준비가 확인되지 않은 공개/실패 상태는 `UNAVAILABLE`입니다.
공고 없는 초기 준비는 `PREPARING`, 상태 행 없이 공고가 남아 있는 복구 전 legacy는 `UNAVAILABLE`입니다.
시각은 서울 시계를 사용해 저장하고 API에서는 `+09:00` 오프셋이 포함된 ISO-8601 문자열로 반환합니다.

`SupportProgramSourceReadinessResult → SupportProgramSourceReadinessResponse` 변환으로 필수 `sources`와
전체 검색 범위를 제공합니다. 일부만 준비되면 `SEARCHABLE_WITH_PARTIAL_SOURCES`이며 외부 호출은 없습니다.
상태가 아직 없는 현재 공고의 제공처도 준비 미확인으로 표시합니다. 초기 빈 DB에서는 기업마당과
`KSTARTUP_SYNC_ENABLED=true`인 경우 K-Startup을 표시합니다. 하나가 준비 중이어도 준비된 제공처 검색은 유지합니다.
세부 정책과 검증은 [다중 제공처 준비](support-program-multi-source-preparation.md)에 있습니다.

점수화 계약은 `govbiz-support-program-ranking-v5`입니다. 의미 관련성 20/40점 이상을
충족해야 하며, `targetEligibility` 또는 `regionEligibility`가 `INCOMPATIBLE`이면 추천에서 제외합니다.
관련도는 `2 × (semanticRelevance 0~40 + supportTypeFit 0~10)`입니다. 자격·접수 상태를 가산점으로 쓰지 않습니다.
LLM 출력의 `targetAssessment`·`regionAssessment`는 자격 상태·설명·후보별 원문 조각 번호만 포함합니다.
미확인 자격 때문에 관련도가 낮아지거나 별도 총점 컷에서 탈락하지 않습니다.
Agent가 번호를 공식 API 본문의 `{field, quote}`로 복원하며 Core와 공개 HTTP 인용 계약은 유지합니다.
전체 `summary`·`targetDescription`도 그대로 모델에 제공해 조각 경계로 뒤쪽 조건·예외가 생략되지 않게 합니다.
후보 summary/targetDescription은 최대 6,000/2,000 code point이며 절단 여부를 별도로 전달합니다.
절단된 후보는 누락된 조건을 확인한 것처럼 판정하지 않도록 두 축 모두 `UNKNOWN`만 허용합니다.
`regions`는 후보 검색용 태그이지 자격 근거가 아닙니다. AI와 Core는 MATCH/INCOMPATIBLE의 본문 인용을 필수로
검증하며 실제 전달된 해당 필드의 정확한 부분 문자열만 인정합니다. 인용 존재 검증이 의미 판단을 보증하지는 않습니다.
지역 지침과 내부 schema는 회사·특정 사업장·개인 중 제한 주체를 먼저 구분하고 같은 주체의 주소를 비교합니다.
회사 주소로 본점·공장 주소나 개인 거주지를 확정하지 않습니다. 서울 기업·서초구 한정은 UNKNOWN이지만,
별도 허용 경로 없는 서울 기업·안산 관내 한정은 INCOMPATIBLE입니다. 본문에 없는 지점·이전 경로는 만들지 않고,
실제 허용된 대안 중 하나를 충족하면 다른 대안을 추가로 요구하지 않습니다. 충족 경로 없이 미확인 대안이 남으면
UNKNOWN이며, 확인된 회사 소재지 하나만으로 별도 사업장의 부재를 증명하지 않습니다.
이는 모델의 의미 판단 지침이며 독립적인 행정구역 필터가 아닙니다. 후속 변경은
[지역 충돌·Fast 기록](region-conflict-fast-20260908.md)에 정리합니다.
`UNKNOWN`은 확인 필요 배지로 구분하며 전체를 관련도순으로 표시합니다. 동점은 입력 후보 순서입니다.
검색당 최대 5개이며 공개 DTO의 `eligibilityReview`로 판정·설명·근거와 `OFFICIAL_API_TEXT` 범위를 노출합니다.
첨부파일을 자동 판독하거나 신청 자격을 확정하는 기능은 아닙니다. v5 점수화는 기존 DB·색인 구조와 호출 횟수를
유지합니다. C02 대화 해석은 사용자 확인 검색에 앞서는 별도 모델 호출입니다.
AI Service가 부적격 항목을 최종 응답에 넣으면 Core는 이를 응답 계약 위반으로 거부합니다.
결과가 0개인 것은 정상일 수 있으며 관련 없는 공고로 5개를 채우지 않습니다.

직접 필터 검색은 AI 추천과 분리된 공개 목록 경로입니다.

```text
필터 검색 탭 → ViewModel → BrowseSupportProgramsUseCase → Repository → GET /api/v1/support-programs/catalog
  → SupportProgramCatalogController → SupportProgramCatalogService
  → SupportProgramRepository.findPublishedPresent → MyBatis Mapper → Mapper XML → MySQL
```

공개 DB 스냅샷을 한 번 조회해 키워드·지역·분야·접수 상태·제공처로 필터링하고 정렬·페이지 처리를 합니다.
K-Startup은 업력·신청 대상·연령의 전용 분류도 정확히 비교합니다. 분류 메타데이터는 V7의 nullable JSON에
저장하고 `CatalogSupportProgram.startupDetails`로 복원하며, 공개 카드 DTO에는 AI 판단으로 노출하지 않습니다.
AI·임베딩·Qdrant·외부 제공처 API를 호출하지 않으며 색인 장애 후에도 이미 공개된 목록을 읽을 수 있습니다.
지역·분야는 제공처의 정확한 태그 일치이지 기업 자격 판정이 아닙니다. 결과에는 추천 점수나 자격 판정을 넣지 않습니다.
현재 규모에서는 기존 전체 스냅샷 조회에 전용 분류 매핑을 더해 재사용합니다. 트래픽·데이터 증가 시
실측에 따라 DB 필터/페이지 조회를 검토합니다. 입력·정렬·응답 계약은 [직접 조건 검색](support-program-catalog.md)을 참고하세요.

검색 카드의 상세 링크는 `/support-programs/detail?sourceCode={sourceCode}&sourceProgramId={id}`로
이동합니다. 상세 화면은 해당 URL의 식별자로 공개 상세 API를 다시 호출하므로 직접 진입·새로고침이 가능합니다.

```text
GET /api/v1/support-programs/detail
  → SupportProgramController → SupportProgramDetailService
  → SupportProgramRepository → MyBatis Mapper → Mapper XML → MySQL
```

상세 GET은 외부 API·AI를 호출하지 않습니다. 현재 노출된 복합 식별자 행만 반환하며 없는·미노출 행은
`SUPPORT_PROGRAM_NOT_FOUND`(404)입니다. 검색 문맥이 없으므로 추천 이유는 빈 배열, 점수는 `null`입니다.
공개 입력 제한과 JSON·오류 코드의 전체 계약은 [지원사업 API 계약](support-program-search-contract.md)에 있습니다.

### 공고별 공식 원문 근거 질문

기업마당 상세 화면의 **이 공고에 질문하기** 링크는
`/support-programs/detail/question?sourceCode={sourceCode}&sourceProgramId={id}`로 이동합니다.
질문 페이지는 URL 식별자를 검증하므로 직접 접속·새로고침이 가능하며 상세 화면으로 돌아가는 링크를
제공합니다. 페이지 진입 시 API를 호출하지 않고, 사용자가 질문을 제출할 때 아래 기존 API를 호출합니다.
질문 입력·답변·근거 인용·취소 상태는 질문 페이지의 로컬 상태이며 새로고침 시 초기화됩니다.

```text
POST /api/v1/support-programs/detail/answers
  → SupportProgramController → SupportProgramEvidenceService
  → SupportProgramDetailService → 현재 공개 공고 확인
  → SupportProgramRepository → MySQL의 공고별 원문 캐시 조회
  → 캐시가 없거나 URL이 바뀌었거나 6시간이 지남:
      BizInfoSupportProgramSourceDocumentFacade → BizInfoSourceDocumentClient
        → 기업마당 공식 HTTPS 상세 페이지의 HTML만 수집·읽기 가능한 텍스트로 정규화
      → SupportProgramRepository → MySQL 원문 UPSERT
  → SupportProgramEvidenceChunker → 결정적 청크 최대 50개
  → AiSupportProgramEvidenceFacade → AI Service
      → 별도 Qdrant evidence 컬렉션에 청크 색인
      → 질문과 가까운 청크 최대 5개 검색
      → 단일 typed Agent → OpenAI 근거 답변·짧은 인용 번호 선택
      → Agent가 검증한 번호를 요청의 원래 청크 ID로 복원
  → Core가 청크·인용을 검증 → 답변과 원문 발췌·URL 반환
```

이 경로는 `BIZINFO` 현재 공고에만 제공됩니다. 기업마당 공식 `https://bizinfo.go.kr` 및 그 하위 도메인의
상세 HTML만 허용하며, URL에는 요청한 원본 공고 ID와 같은 `pblancId`가 정확히 하나 있어야 합니다.
자동 리디렉션은 끄고 각 이동 URL을 같은 조건으로 검증해 최대 3회 따릅니다. 따라서 기존 상세 URL에서
`/sii/siia/selectSIIA200Detail.do?pblancId=...`로 이동할 수 있으며, 외부 호스트·비 HTTPS·다른 공고 ID·순환 이동은
거부합니다. 원문 HTML은 최대 500KB로 읽고 jsoup `1.23.2`로 파싱합니다. `.support_project_detail` 안의
`.title_area .title`이 요청 공고 제목과 일치해야 하며, `.view_cont` 본문만 추출해 메뉴·다른 공고·푸터를
제외합니다. 정규화 본문은 최대 30,000자로 제한합니다. 공식 원문을 성공적으로 읽고 검증한 뒤에만
짧은 DB transaction으로 저장하므로 원문 수집·AI 오류가 공고 동기화·목록 검색·상세 GET을 바꾸지 않습니다.
현재 공고의 제공처가 `BIZINFO`가 아니면 422 `SUPPORT_PROGRAM_EVIDENCE_NOT_SUPPORTED`, 공식 원문 수집·검증에
실패하면 503 `SUPPORT_PROGRAM_EVIDENCE_UNAVAILABLE`을 반환합니다. AI 근거 색인·검색·답변의 연결·시간 초과·계약
오류는 일반 AI 경계와 같은 502/503/504 분류를 사용합니다.

Frontend는 `KSTARTUP`을 포함한 비 `BIZINFO` 상세에서 질문 페이지 링크 대신 미지원 안내와 원문 링크를
표시합니다. 미지원 제공처의 질문 페이지에 직접 접속해도 입력을 표시하지 않고 ViewModel에서 전송을 차단합니다.
K-Startup 공식 URL 표시 허용은 원문 수집·RAG 지원과 별개입니다.

원문은 제목·공식 URL을 포함한 텍스트로 저장하며, 같은 원문은 요청마다 다시 수집하지 않고 최대 6시간
재사용합니다. 청크는 내용·원문 해시·순서에서 결정적으로 만들며 각 청크는 최대 1,500 UTF-16 코드 단위입니다. AI Service는
일반 공고 검색 컬렉션과 다른 Qdrant 컬렉션만 사용하고, 요청 공고의 청크 집합으로 검색 범위를 제한합니다.
답변이 충분한 근거를 찾지 못하면 `INSUFFICIENT_EVIDENCE`와 인용 없는 안내를 반환합니다. `ANSWERED`에는
검색된 청크의 인용이 하나 이상 있어야 하며 Core는 인용이 전달한 청크 밖을 가리키면 응답을 거부합니다.
인용 발췌문은 선택한 청크 전체를 반환해 청크 뒤쪽의 답변 근거도 화면에서 확인할 수 있습니다.

모델에는 64자리 해시를 복사시키지 않습니다. Agent가 이번 요청 배열에 `index`(0~4)를 붙여 전달하고
`SupportProgramEvidenceAnswerSelection.citationChunkIndexes`를 검증한 뒤 원래 `citationChunkIds`로 변환합니다.
`index`는 원문의 `order`와 다르며 요청마다 새로 부여합니다. 범위 초과·중복·상태 모순을 보정하거나 무시하지
않고 기존 오류로 반환합니다. Core와 공개 HTTP의 인용 계약은 변경하지 않습니다.

첨부파일·PDF·OCR·다른 제공처 원문 수집은 이 흐름에 포함하지 않습니다. 공고 목록 검색의 의미·키워드 후보 선정·AI
점수화와도 별도 사용 사례이므로, 원문 질문을 하지 않으면 기업마당 상세 HTML을 수집하거나 evidence 컬렉션을
사용하지 않습니다.

## 검색 품질 평가 fixture 내보내기와 캡처

```text
evaluation-fixture-export profile (비웹 실행)
  → findSearchablePresent로 준비된 제공처의 공개 공고 조회 → 지정한 referenceDate 기준 OPEN 공고만 선정
  → SupportProgramIndexDocumentMapper와 같은 ID·내용 해시·검색 문서 생성
  → 기준 날짜·전체 적격 카탈로그와 cases: []인 미라벨 fixture 초안을 원자적으로 JSON 기록
  → 질문을 고정하고 선택한 AI-only·혼합·사람 검토 방식으로 참조 라벨 확정

evaluation-capture profile (비웹 실행)
  → 질문 묶음 JSON 검증
  → 같은 referenceDate로 SupportProgramSearchService.searchWithTrace
      → 준비된 제공처의 현재 공고 → 기준 날짜의 적격 공고 → 의미·키워드 RRF 후보 최대 20개 → AI 최종 추천 최대 5개
  → 기준 날짜·질문별 후보 ID·최종 ID·카탈로그 지문을 원자적으로 JSON 기록
  → 별도 Python 평가 도구가 선택한 판정 출처의 fixture와 대조
```

두 경로 모두 공개 Controller나 디버그 HTTP endpoint가 아닙니다. `evaluation-fixture-export`는 자신의 웹 서버와
두 동기화 스케줄러를 끄고 공고 데이터는 MySQL에서만 조회합니다. 따라서 Qdrant·AI Service·OpenAI를 호출하지
않으며, 전체 카탈로그 검증이 끝난 뒤에만 출력 파일을 원자적으로 교체합니다. `referenceDate`는 실행 시각의
오늘이 아니라 신청 시작·종료일로 접수 상태를 다시 계산하는 평가 기준이며 생성 fixture에 함께 기록됩니다.
생성된 `cases: []`에는 고정 질문의 `id`·`query`·`split`과 선택한 판정 방식의 `relevantIds`를 채웁니다.
현재 공유 실행은 AI-only이며 사람 검토 정답으로 표시하지 않습니다. 질문 묶음의 `name`과 각
`id`·`query`·`split`은 fixture의 `cases`와 같은 순서·내용으로 맞춥니다.

`evaluation-capture` profile도 자신의 웹 서버와 두 동기화 스케줄러를 끄며, 모든 질문이 성공하고 캡처 중
카탈로그 지문이 같을 때만 출력 파일을 교체합니다. capture는 fixture와 같은 `referenceDate`를 명시해 같은
접수 상태 집합을 검색하며, Python 평가기는 두 날짜가 다르면 평가를 거부합니다. 별도 Core API 인스턴스가 카탈로그를 갱신한 경우에는 지문
변화로 결과 파일 기록을 거부합니다. 후보 ID는 `sourceCode:sourceProgramId` 형태이며, 첫 번째 `:` 앞의
`[A-Z][A-Z0-9_]{0,63}` 제공처 코드와 뒤의 원본 ID를 함께 사용합니다. 같은 Search Service가 만든
후보·최종 결과를 기록하므로 평가 코드가 운영 검색 흐름을 별도로 재현하지 않습니다. 실제 AI Service를
호출할 수 있으므로 기본 실행·CI에는 포함하지 않습니다. fixture 내보내기·라벨·캡처·평가 실행 규칙은
[검색 평가 자료](../evaluation/support-program-search/README.md)를 따릅니다.
새 fixture/capture는 같은 준비된 제공처 범위를 사용합니다. 기존 고정 평가 스냅샷·원표·캡처는 당시의
입력과 실행 결과로 보존하며, 이번 상태 필터를 소급 적용하거나 과거 지표를 다시 해석하지 않습니다.

## 기업마당 동기화와 공개 순서

```text
BizInfoSupportProgramCatalogSyncScheduler (기본: 최초 PT0S, 완료 후 PT6H)
  → Repository: 수집 시작 세대 발급 [짧은 DB transaction]
  → BizInfoSupportProgramCatalogFacade → BizInfoClient: 전체 페이지 수집·검증
  → BizInfoProgramMapper: 필수 필드 검증·공고 정규화
  → SupportProgramIndexSyncService.indexSnapshot: 모든 공고의 벡터 준비
      → AiSupportProgramIndexClient → AI Service → OpenAI 임베딩 → Qdrant
  → Repository: 최신 시작 세대일 때만 MySQL에 공개 [짧은 DB transaction]
      → BIZINFO 기존 행 미노출 처리 + 수집 목록 UPSERT
      → 공개 세대·카탈로그 지문·공고 수·indexReady·성공 시각 기록
```

공공데이터포털의 전체 건수, 페이지 번호·크기, 페이지별 항목 수와 실제 수집 수가 일치해야 합니다.
기업마당 수집·응답 검증·필수 필드 정규화 중 하나라도 실패하면 카탈로그를 바꾸지 않습니다.
현재 Client는 페이지당 1,000건을 요청하며 최대 20페이지·20,000건으로 제한합니다.

색인은 전체 공고를 16개씩 나누어 요청합니다. AI Service는 동일 ID·해시의 벡터가 이미 있으면 재사용하고
없는 버전만 생성합니다. 모든 배치의 성공과 처리 건수를 확인한 후에만 DB 공개를 시도합니다.
색인 도중 실패하면 현재 세대일 때만 실패 시각을 기록하고 기존 공개 카탈로그·그 스냅샷의 색인 준비 상태를
유지합니다. 이미 준비된 벡터는 재시도 시 재사용할 수 있습니다. 더 최신 세대가 시작되면 이전 세대의
성공·실패 기록 모두 무시합니다.

MySQL의 `support_program_sync_generation`은 제공처별 최신 **시작** 세대를 관리합니다. 이전 실행이 늦게
끝나도 더 최근에 시작된 작업이 있으면 공개를 건너뜁니다. 후발 작업이 실패하더라도 이전 세대가 다시
공개 권한을 얻지는 않으며 마지막으로 공개된 카탈로그를 다음 성공까지 유지합니다.

시작 세대 발급과 공개는 각각 행 잠금을 사용하는 짧은 DB transaction입니다. 공개 transaction 안의
미노출 처리·UPSERT·상태 행 성공 기록 중 하나라도 실패하면 전체를 rollback합니다. 외부 HTTP 수집·색인은 DB
transaction 밖에서 실행하며, 수집 실패를 이유로 기존 행을 삭제하거나 다른 제공처 데이터를 변경하지 않습니다.
동기화 Service가 수집·사전 색인·공개 과정의 RuntimeException을 한 번 기록한 뒤 Scheduler가 다음 주기에 계속 실행합니다.

## 벡터 정합성과 복구

`SupportProgramIndexDocumentMapper`가 제목·기관·지원 대상·분야·지역·신청 기간 원문·요약으로 검색 문서를
구성합니다. 제어·형식 문자는 개행·탭을 제외하고 정리하며 Unicode 코드 포인트 기준 최대 12,000자로
제한합니다. UTF-8 문서의 SHA-256이 내용 해시이고, 내부 문서 ID는
`{sourceCode}:{sourceProgramId}`입니다.

AI Service는 문서 ID·내용 해시에서 Qdrant point ID를 결정하며, 임베딩 모델·차원·색인 규격에 따라
컬렉션을 분리합니다. 현재 DB의 정확한 문서 버전에 해당하는 point ID만 검색하도록 필터링하므로
이전 버전·미노출 공고·아직 공개하지 않은 세대의 벡터는 결과에 섞이지 않습니다.

```text
SupportProgramIndexSyncScheduler (기본: 최초 PT0S, 완료 후 PT1M)
  → SupportProgramIndexSyncService.repair
  → Repository: 현재 MySQL 공개 공고 목록·제공처별 상태 조회
  → 제공처별로 AI Service: 해당 버전의 누락 벡터 생성·저장
  → 해당 제공처 상태의 공개 세대·지문·공고 수가 읽은 스냅샷과 같을 때만 indexReady 갱신
  → 한 제공처 실패 이후에도 다른 제공처를 처리하고, 완료 후 실패를 오류로 전달
```

복구는 제공처 수집과 별도 단일 스레드에서 실행합니다. `SUPPORT_PROGRAM_INDEX_ENABLED=false`는
이 복구 작업만 끄며, 새 카탈로그 공개 전의 필수 색인은 끄지 않습니다.
공고가 0개인 공개 스냅샷도 제공처 상태를 기준으로 처리하고, legacy 채택도 제공처별로 수행합니다.

복구 색인이 실패하면 자신이 읽은 스냅샷과 상태 행이 여전히 같을 때만 `indexReady=false`로 바꾸며,
최근 카탈로그 동기화 성공·실패 기록은 바꾸지 않습니다. 복구가 늦게 끝난 동안 새 스냅샷이 공개되면 조건부
UPDATE가 0행이 되어 새 스냅샷 상태를 건드리지 않습니다. 이 상태는 마지막 전체 색인 준비 결과이며 실시간
Qdrant Health를 뜻하지는 않습니다.

공개 준비와 복구는 모두 `prune`을 호출하지 않습니다. 이전 스냅샷 기준의 삭제가 공개 준비 중인 새 벡터를
지우는 상황을 피하기 위해 현재 자동 삭제를 연결하지 않았습니다. 내부 `prune` API는 존재하지만,
안전한 보존·삭제 수명주기와 전체 다중 인스턴스 운영 검증은 후속 과제입니다.

검색 대상의 벡터가 하나라도 없거나 Qdrant·AI Service가 실패하면 일부 후보만으로 성공하지 않고 오류를
반환합니다. 별도 복구 작업의 성공 후 다시 검색할 수 있습니다. 빈 검색어 목록과 상세는 AI에 의존하지 않습니다.
조회 때 변하는 접수 상태는 벡터에 고정하지 않으며, DB의 `content_hash` 컬럼도 영속 색인 완료 기록으로
사용하지 않습니다. 현재 검색은 매번 전체 대상 공고와 ID·해시 목록을 읽고 전송하므로 대규모 데이터 성능은
별도 개선이 필요합니다.

## 데이터와 접수 상태

MySQL의 `support_program`은 `(source_code, source_program_id)` 고유키로 원본 공고를 식별합니다.
`categories`·`regions`는 JSON, 신청 기간은 원문과 nullable 날짜로 저장합니다. 원본에서 사라진 공고는
삭제하지 않고 `is_source_present=false`로 바꿉니다. UPSERT는 대소문자만 바뀐 원본 ID도 최신 표기로
갱신하여 MySQL과 벡터 식별자를 맞춥니다. 고유키 비교는 MySQL의 `utf8mb4_0900_ai_ci` collation을 따릅니다.

`support_program_source_document`는 원문 근거 답변에만 쓰는 공고별 공식 HTML 정규화 텍스트·원문 URL·해시·수집
시각을 같은 복합 식별자로 저장하고 공고를 FK로 참조합니다. 조회 시 공고의 공개 상태를 확인하므로 미노출 공고에는
원문 질문을 제공하지 않습니다. 이 테이블은 정기 목록 동기화에서 채우지 않고 명시적 원문 질문의 수집·검증이
성공했을 때 UPSERT합니다.

`support_program_sync_status`는 제공처별 스냅샷의 공개 세대·지문·공고 수를 기록합니다.
V4 적용 전부터 있던 공고는 과거 공개 세대를 복원하지 않습니다. 대신 해당 제공처의 현재 공개 공고가 1건 이상인 경우에
한해, 전체 복구 색인이 성공한 뒤 그때 읽은 지문·공고 수를 sentinel 세대 `0`으로 조건부 채택할 수 있습니다.
빈 초기 DB는 `PREPARING`, 복구 전 legacy 공고는 `UNAVAILABLE`이며, 실제 지문이 있는 새 스냅샷은 bootstrap이 덮어쓰지 않습니다.

`account`·`account_session`(V5)은 회원 계정을 저장합니다. `account`는 소문자로 정규화한 이메일을 고유키로 두고
비밀번호 해시, 역할(`USER`·`ADMIN`), 이메일 인증·정지·삭제 시각과 약관 동의 시각을 저장합니다. 삭제된 계정은 모든
조회에서 제외하고 정지된 계정은 로그인과 세션 확인을 403으로 막습니다. `account_session`은 세션 JWT의 SHA-256
해시와 절대 만료·마지막 사용 시각만 저장하고 계정 삭제 시 함께 삭제됩니다. 이메일 중복은 DB UNIQUE 제약이 막고,
절대·유휴 만료 여부는 서울 기준 시계로 조회 시점에 판단합니다. 기업 테이블은 기업 등록 단계에서 추가합니다.

접수 상태는 `SupportProgramStatusResolver`가 읽을 때 계산합니다. 파싱된 시작일 전은 `UPCOMING`,
종료일 이후는 `CLOSED`, 시작일·종료일 범위 안은 `OPEN`입니다. 날짜 경계는 포함합니다.
날짜만으로 결정되지 않은 경우 예정 표현, 남아 있는 종료일, 명시적 종료 표현, 상시 접수 표현 등의
규칙을 순서대로 적용하고 판단 근거가 없으면 `UNKNOWN`을 유지합니다. 따라서 `접수 종료` 표현이
상시 접수보다 우선하더라도 파싱된 날짜를 무조건 덮어쓰지는 않습니다.

수집 Client·동기화는 `BIZINFO`·`KSTARTUP`·`MSIT`·`CNTRADE_NOTICE`별로 구현됩니다. production 검색·색인·AI 점수화는
`sourceCode:sourceProgramId`를 내부 식별자로 사용하고 검색은 준비된 제공처 범위에서 실행합니다.
K-Startup은 별도 구체 Client·Facade·SyncService·Scheduler를 사용하고 기존 색인 Service·Repository를 공유합니다.
제공처별 Facade는 명시적 Qualifier로 구분하며 제공처 Registry나 새 production 의존성은 추가하지 않습니다.

## K-Startup 수집 범위와 추가 분류

`KStartupSupportProgramCatalogSyncScheduler → KStartupSupportProgramCatalogSyncService →
KStartupSupportProgramCatalogFacade → KStartupClient → KStartupProgramMapper`에서 수집·검증한 뒤,
기업마당과 같은 `SupportProgramIndexSyncService → AI Service → OpenAI 임베딩 → Qdrant` 경로를 거칩니다.
전체 색인 성공 후 Repository가 `KSTARTUP` 범위만 UPSERT·누락 비활성화·공개 상태 갱신합니다.

- 공식 API: `getAnnouncementInformation01`. 안정 ID는 `pbanc_sn`이며 화면 순번 `id`를 쓰지 않습니다.
- `KSTARTUP_SYNC_ENABLED=false`가 기본입니다. `KSTARTUP_API_KEY`와 초기 색인 비용을 확인한 뒤 켭니다.
- `RECENT_YEAR`는 서울 기준 오늘에서 1년 전 이후 **접수 시작** 공고를 수집합니다. 마감도 포함하지만
  그보다 먼저 접수한 장기 공고는 범위 밖입니다. `OPEN`은 API의 모집 중 공고만 수집합니다.
  `ALL` 상태 필터는 전체 과거 이력이 아니라 이렇게 수집·공개된 범위 전체를 뜻합니다.
- 첫 페이지의 날짜 조건을 전체 수집 동안 고정합니다. 전체 이력 `totalCount`가 아닌 조건 일치 `matchCount`로
  페이지 수를 계산하고 각 페이지의 번호·크기·건수·전체 건수·ID 중복을 검증합니다. 실패 시 기존 목록을 보존합니다.
- 페이지당 1,000건 요청, 최대 20,000건·200페이지 안전 한도를 적용합니다. 전체 제공처 검색 후보 한도도 20,000건이므로
  전체 과거 이력 확장은 별도 성능·계약 검토가 필요합니다.
- 원문 `aply_trgt_ctnt`·`aply_excl_trgt_ctnt`만 대상 설명에 보존해 후보 자격 검토에 전달합니다.
  대상·업력·연령 분류는 별도 메타데이터와 벡터 검색 문서에만 넣고, 자격 근거 필드에는 합치지 않습니다.
  분류는 자격 충족의 증거가 아니며 원문 조건을 우선합니다.
- 공식 HTTPS 상세 URL의 호스트와 `pbancSn`이 원본 ID와 일치해야 합니다. 상세 HTML 추가 질문은 여전히 미지원입니다.
- 게시일이 없어 접수 시작일로 최신 정렬합니다. 제공처 간 같은 사업을 자동 병합하지 않습니다.

## 과기정통부·충청남도 수출입공지 수집

각 `Msit`/`CnTradeNotice` Scheduler → SyncService → CatalogFacade → Client → Mapper에서 전체 수집을
검증하고, 기존 `SupportProgramIndexSyncService → AI Service → OpenAI 임베딩 → Qdrant`를 거쳐
Repository가 해당 `source_code`만 UPSERT·누락 비활성화·스냅샷 공개합니다. DB migration이나 신규 의존성은 없습니다.

- MSIT의 `response` 배열(header/body)과 충남의 최상위 `09/RETURN_SUCCESS` 응답을 별도로 검증합니다.
  첫 페이지와 각 페이지의 전체 건수·번호·실제 크기·행 수·중복 ID가 일치해야 하며 HTTP 200의 게이트웨이 오류도 실패입니다.
- MSIT는 공식 `msit.go.kr` 사업공고 상세 URL의 게시판 100/`nttSeqNo`를 검증해 ID로 사용합니다.
  실응답의 10건 페이지를 끝까지 읽으며 20,000건/2,000페이지 한도를 적용합니다. 장시간 수집은 전용 단일 스레드로 격리합니다.
- 충남은 `lbbNo`를 ID로 사용하며 1,000건을 요청하되 실제 응답 페이지 크기를 기준으로 완전성을 검증합니다.
  20,000건/200페이지 한도입니다. 숫자 ID로 암호화된 웹 상세 `idx`를 추측하지 않고 검증된 공식 공지 목록에 연결합니다.
- API에 없는 접수 기간/지역/분야는 추정하지 않습니다. 게시일은 정렬용이고 상태는 `UNKNOWN`입니다.
  MSIT는 본문 없이 제목·담당 부서 기반 검색이며 충남은 원문 본문 기반입니다. 선정 결과·일반 공지도 원본대로 포함됩니다.
- 두 수집기 기본 비활성, 전용 키 생략 시 승인된 `DATA_GO_KR_SERVICE_KEY`를 재사용합니다.
  평가 fixture/capture 프로필은 개발 환경변수와 관계없이 두 수집기도 끕니다.
- 수집기 구현과 실제 서비스 정상화는 별개입니다. 2026-09-09 CNTRADE_NOTICE 실 API의 `04 HTTP_ERROR`는
  빈 정상 결과로 숨기지 않으며, 제공처 복구 전에는 실제 수집 성공을 주장하지 않습니다.

## Frontend와 내부 계약

Frontend의 업무 호출 흐름은 `View → ViewModel Hook → UseCase → Repository → data/api → Core API`이며,
채팅 검색에서는 페이지 ViewModel과 UseCase 사이에 내부 채팅 Hook을 둡니다.
Awilix의 `app/di`에서 Repository·UseCase·외부 함수를 구성하고 `appContainer`가 앱 단위 인스턴스를
제공합니다. ViewModel 또는 내부 Hook은 UseCase·외부 함수 토큰을 조회하며 Repository를 직접 생성하지 않습니다.
`data/api`의 함수가 요청 URL·Fetch·Zod 응답 검증을 담당합니다.

화면 기능은 `presentation/features/chat`의 채팅 검색, `presentation/features/support-program-detail`의
상세 조회·원문 근거 질문, 그리고 `auth`(로그인·회원가입)·`pricing`·`partner-recruitment`·`company-profile`·`admin`의
계정 관련 화면으로 나눕니다. 각 feature가 전용 View·스타일·ViewModel·테스트를 소유하고,
서로의 화면 구현을 import하지 않습니다. 검색 카드와 상세 화면은 기존 상세 URL·복합 식별자로 연결합니다.
검색과 근거 질문이 함께 쓰는 안전한 오류 문구는 `presentation/shared/support-program`에 둡니다.
`support-program-detail` 안에서도 상세 조회와 질문은 별도 페이지입니다. `SupportProgramDetailPage`는
`useSupportProgramDetailViewModel`, `SupportProgramEvidenceQuestionPage`는
`useSupportProgramEvidenceQuestionViewModel`을 각각 사용하고 URL의 복합 식별자로 이동합니다.

채팅 메시지·검색 조건은 Redux Toolkit으로 관리하고 검색 요청 흐름은 내부 채팅 Hook의 thunk에 둡니다.
기업 조건은 폼에서 직접 적용하거나 C02 변경 제안을 확인해 적용하며 현재 대화의 메모리에만 보관합니다.
검색 요청마다 그 시점의 적용 조건을 사용하고, 새 대화·브라우저 새로고침으로 초기화됩니다.
C02는 작은 현재 상태와 새 발화만 해석하며 화면의 전체 메시지를 다시 전송하지 않습니다.
미확정 초안·질문·검색 의도와 적용 조건을 구분합니다. 로그인 세션은 아래 계정과 세션 절에 있고 프로필 영속 저장은 후속 범위입니다.
`ChatPage`는 페이지 ViewModel인 `viewmodel/useChatPageViewModel` 하나를 사용합니다. 이 ViewModel은
`hooks/useSupportProgramChat`의 Redux 상태·검색 요청 수명과 `hooks/useSupportProgramSearchReadiness`의
준비 상태 조회·polling을 조합합니다. 페이지 ViewModel은
검색 확인·검색 재시도만 준비 상태에 따라 제한하며 메시지 제출·추천 질문·다시 해석은 이와 독립적으로 처리합니다.
페이지 ViewModel은 DOM 참조·입력 조합·
포커스·스크롤도 Hook 로컬로 관리합니다. View는 렌더링·이벤트 연결·순수 표시용 포맷을 담당합니다.
조건 제안은 ViewModel의 `chatConversationProposal` 순수 변환이 요청 당시 조건을 기준으로 변경·유지·적용 값을
계산하며, `ChatPage`는 원본 해석 결과나 미확정 질문 대신 최종 `displayProposal`을 받습니다.
`ConversationProposal`과 `ProgramResults`는 각각 제안·결과를 표시하는 View 컴포넌트입니다.
카드·결과 집계의 자격 분류는 기존 `supportProgramEligibility`에서, 상세 URL 생성은 공용 routes에서 공유합니다.
React Router는 `/` 아래 공개 화면(검색·요금제·공개 파트너 모집·지원사업 상세), 로그인·회원가입, `/app` 아래 회원 세션이 필요한
작업 화면(작업 채팅·요금제·파트너 모집·프로필·관리자)과 두 SampleItem 예제 화면을 연결합니다. SampleItem은 업무 기능이 아니라 같은 UseCase의
Hook 상태와 Redux 상태 차이를 비교하는 예제입니다.

Core의 공개 계약은 기능별 `controller/dto`, 외부 계약은 시스템별 `client/dto`, 검증된 실행 결과는
`service/dto`, 업무 모델은 `domain`에 둡니다. 관계형 DB 접근은 `Repository → Mapper → XML`이며
`DbRow`를 Repository 밖으로 노출하지 않습니다. 같은 필드가 있어도 외부 입력과 공개 응답을 하나의
타입으로 합치지 않습니다. 상세 배치 규칙은 [Core API README](../backend/core-api/README.md)에 있습니다.

AI Service는 조건 변경 해석·점수화·원문 근거 답변에서 각각 `HTTP API → Service → Agent → OpenAI → Response` 흐름으로
실행합니다. `bootstrap.py`가 클라이언트와 서비스 수명주기를 구성하고, 역할이 다른 typed Agent를 각각
`max_turns=1`로 실행합니다. 현재 tool·handoff·multi-agent orchestration은 없습니다. 일반 공고 색인·검색은
`support_program_index`, 원문 청크 색인·검색은 `support_program_evidence`가 OpenAI 임베딩과 분리된 Qdrant
컬렉션을 직접 사용합니다.

랭킹 모델은 `OPENAI_RANKING_MODEL`로 지정하고 미설정이면 공통 `OPENAI_MODEL`을 상속합니다.
`OPENAI_RANKING_REASONING_EFFORT`는 `none`/`low`만 허용합니다. 정확도 우선 프로필은 랭킹만
Sol/low를 사용하며 대화·원문 답변 모델은 바꾸지 않습니다. 모델 객체는 분리하되 동일한 OpenAI
클라이언트·인증·재시도 정책을 공유하며 새 provider나 orchestration 계층은 없습니다.
출력 축약은 미채택이며 기존 후보 ID·필드명·출력 계약을 유지합니다. 축약 구현은 평가 경로에만 남깁니다.
`OPENAI_RANKING_SERVICE_TIER` 미설정 시 코드·Compose 기본값은 `default`입니다. 제공 `.env.example`은
사용자 승인에 따른 Fast 상시 사용 프로필인 `priority`를 명시하며, 현재 Sol의 랭킹 토큰 단가는 일반 처리의 2배입니다.
랭킹 요청에만 적용하고 대화 해석·RAG 답변·임베딩 설정은 바꾸지 않습니다. 모델·후보·점수·출력/시간 상한은 유지합니다.
설정 프로필과 배포·실측 상태는 [지역 충돌·Fast 기록](region-conflict-fast-20260908.md)에서 구분합니다.

두 색인 Service가 실제로 공유하는 입력 토큰 상한 처리는 `support_program_embedding.py`의 함수 하나로
유지합니다. 토크나이저 준비·인코딩·잘라내기를 작업 스레드에서 실행해 HTTP 이벤트 루프를 막지 않으며,
OpenAI 호출·응답 검증·오류 처리는 각 Service에 남겨 둡니다.

추천 점수화는 전용 설정으로 모델 `45s` → Agent 실행 `50s` → Core 읽기 `55s`를 사용합니다.
조건 해석·근거 답변은 기존 모델 `25s` → Agent 실행 `30s` → Core 읽기 `35s`를 유지합니다.
AI Health도 Core의 기존 공유 읽기 설정을 사용합니다. 공고 의미 검색 전체는 AI에서 `25s`, Core 읽기는
`30s`이며, 검색 화면은 의미 검색과 점수화의 순차 호출을 고려해 `90s` 후 요청을 취소합니다.
C02 해석은 별도 `40s` 제한이며 사용자 확인을 사이에 두므로 검색 요청에 해석을 합치지 않습니다.
이 값은 시간 예산이며 성능 목표가 아닙니다. 랭킹의 전용 RestClient 외에는 기존 의존 방향을 유지합니다.

## 계정과 세션

계정 흐름은 `AccountAuthController → AccountSignupService · AccountLoginService · AccountSessionService → AccountRepository → MySQL`입니다.
회원가입은 이메일·비밀번호만 받아 BCrypt 해시와 약관 동의 시각을 저장하고 같은 요청에서 세션을 발급합니다. 이메일 중복은 DB unique
제약의 `DuplicateKeyException`을 Service가 409로 바꿉니다.
사업자등록번호 확인은 `BusinessLookupController → BusinessLookupService → BiznoClient`로 외부 HTTP를 한 번 부르고,
`BiznoClient`가 응답 검증과 오류를 `BiznoClientException`으로 바꿔 API 키가 담긴 URL이 로그·응답에 남지 않게 합니다.
기업 등록은 `CompanyController → CompanyService → BiznoClient(사업자등록번호 조회) · CompanyRepository → MySQL`입니다. 서버가 등록 시점에
사업자등록번호를 다시 조회해 계속사업자만 저장하고, 계정 조회는 `company`를 LEFT JOIN해 요약과 `tier=COMPANY`를 계산합니다.
파트너 모집글은 `partner` 기능의 `PartnerRecruitmentController → PartnerRecruitmentService → PartnerRecruitmentRepository → MySQL`입니다.
Service가 세션 계정의 기업, `support_program`에 현재 있는 공고, 접수 상태(`SupportProgramStatusResolver`), 마감일 규칙을 확인한 뒤 저장하고,
공고당 한 건은 DB UNIQUE 제약이 보장합니다. 목록·상세는 기업·계정·공고를 JOIN해 읽고 모집 상태는 저장하지 않고 조회 시점에 계산합니다.
파트너 제안은 `PartnerProposalController · PartnerProposalBoxController → PartnerProposalService → PartnerProposalRepository → MySQL`입니다.
Service가 제안자의 기업, 모집글의 모집 상태, 당사자 여부를 확인하고, 수락·거절·철회는 `decision IS NULL AND withdrawn_at IS NULL`
조건의 UPDATE 영향 행 수로 동시 처리를 막습니다. 제안 상태와 담당자 연락처 공개 여부는 `PartnerProposal` 도메인이 응답·철회·경과 시간·모집 상태로
계산하며, 모집글 응답의 제안 수·내 제안은 `PartnerRecruitmentService`가 두 Repository를 묶어 붙입니다.
로그인 성공 시 `SessionTokenHelper`가 계정 ID를 `sub`로 하는 HS256 JWT를 발급하고, DB에는 토큰의 SHA-256 해시와
만료 시각만 저장합니다. 로그인이 필요한 Controller는 `Account` 파라미터를 선언하며
`AuthenticatedAccountArgumentResolver`가 HttpOnly 세션 쿠키(`govbiz_session`)의 서명·만료를 검사한 뒤 세션 행으로 계정을
채웁니다. 토큰은 응답 본문에 싣지 않고 쿠키로만 전달하며, 쿠키가 붙은 상태 변경 요청은 `SameSite=Lax`와
`SessionOriginInterceptor`의 Origin 검사로 CSRF를 막습니다. 로그인 시도는 `AccountLoginAttemptGuard`가 계정·접속 주소
기준으로 제한합니다. 세션 행이 없으면(로그아웃) JWT가 유효해도 401이고, "로그인 상태 유지" 여부에 따라 30일 또는 12시간의
절대 만료와 7일 유휴 만료를 함께 검사합니다. 화면 권한 단계(`tier`)는 `Account`가 역할·인증 상태로 계산해 `/me`에
내려 주고, 프런트의 `RequireAuth`는 이 값으로만 `/app` 아래 라우트를 나누며 서버가 모든 쓰기 API에서 다시 검사합니다.
Spring Security filter chain은 쓰지 않고 `spring-security-crypto`의 BCrypt만 사용합니다. 개발용 시드 로그인은
설정이 켜졌을 때만 별도 Controller가 등록되며 관리자·회원 시드 계정을 만듭니다.

Frontend에서 로그인 상태는 헤더와 여러 화면이 함께 읽으므로 `presentation/shared/auth`의 Redux slice와
`useAuthSession`·`useRestoreAuthSession` Hook이 소유하고, 로그인 화면은 `presentation/features/auth`가 소유합니다.
세션 토큰은 브라우저의 HttpOnly 쿠키가 관리하므로 앱은 다루지 않고, `data/storage`에는 앱 시작 시 `/me`를 부를지
정하는 힌트만 둡니다. Repository가 로그인·로그아웃과 함께 힌트를 저장·삭제합니다.
소셜 로그인은 `AccountOAuthController → AccountOAuthService → GoogleOAuthClient · KakaoOAuthClient(제공처 HTTP) · AccountRepository`로,
Bizno처럼 외부 호출을 Client 경계 뒤에 두고 Service가 계정 찾기·연결·생성 규칙을 맡습니다. 브라우저는 시작 endpoint로 이동해
제공처 동의 뒤 Core 콜백으로 돌아오고, 서명한 state 쿠키로 요청을 잇습니다. 프런트는 `SocialLoginButtons`(링크)와
`/oauth/callback` 화면(`CompleteOAuthLogInUseCase`로 `/me` 조회)만 가지며 액세스 토큰·시크릿을 다루지 않습니다.
화면은 로그인 전 `/` 아래 공개 경로(공용 헤더)와 로그인 뒤 `/app` 아래 내부 경로(사이드바)로 나뉩니다. `PublicOnly`는
로그인한 사용자를 공개 URL에서 같은 내용의 `/app` 화면으로, `GuestOnly`는 로그인·회원가입에서 복귀 경로로, `RequireAuth`는
비로그인 사용자를 `/login?next=`로 보냅니다. 경로 상수와 공개↔내부 대응은 `presentation/shared/routes/appPaths.ts`가 소유합니다.

## 오류 경계

AI Service의 랭킹·조건 해석 모델·HTTP·Agent 시간 초과는 내부 504로 반환되고 Core는 공개 `504 AI_SERVICE_TIMEOUT`으로
전달합니다. 화면은 해당 endpoint의 검증된 오류 계약에 한해 AI 처리 시간 초과와 수동 재시도를 안내합니다.
조건 해석 실패는 일반 오류로 합치지 않고 시간 초과·일시 이용 불가를 구분하며, AI 로그에는 실패 종류·오류 클래스명·
경과 시간만 남깁니다. 요청 문장·기업 조건·모델 응답과 원문 예외는 기록하지 않습니다.
의미 검색의 준비·임베딩·Qdrant 시간과 랭킹의 준비·모델·검증 시간, 캐시 상태·후보 수·총시간은
`app` INFO로 기록하며 캐시 키는 남기지 않습니다. lifespan은 기존 app/root handler를 재사용하거나
stderr handler 하나를 추가합니다. 전역·OpenAI·HTTP 로그 수준은 바꾸지 않습니다.
시간 초과 외 LLM 실행 실패·색인 미준비·Qdrant 실패는 내부 503으로 반환되고 Core는 공개
`503 AI_SERVICE_UNAVAILABLE`로 변환합니다. Core가 관측한 연결·읽기 timeout 및 점수화·색인 API의
내부 408·504는 504, 예상하지 않은 HTTP 상태나 잘못된 응답 계약은 502로 분류합니다.
공개 응답은 `application/problem+json`이며 내부 URL·원본 라이브러리 예외를 노출하지 않습니다.

Core의 Health는 프로세스 상태, AI Health는 AI Service의 정해진 Health 응답을 확인하는 기능입니다.
이들이 성공했다고 MySQL·Qdrant·OpenAI를 포함한 실제 검색 전체가 준비됐음을 보장하지 않습니다.
전체 연결 동작은 [Compose 검증 절차](../infrastructure/README.md)로 확인합니다.

현재 제품은 공고 요약의 의미·키워드 결합 검색·구조화된 추천과, 기업마당 공식 HTML 한 종류의 공고별 근거 답변을 제공합니다.
실제 검색 후보·최종 추천의 캡처, AI-only 참조 판정과 변경 전후 보고서는
[공유 평가 자료](../evaluation/support-program-search/runs/support-program-catalog-20260906-v1/README.md)에 있습니다.
평가 가능한 질문은 6개, 그중 양성 질문은 2개뿐이며 독립적인 사람 검토 품질 증거는 아닙니다.
근거 답변은 [5단계 후속 검증](../evaluation/support-program-evidence/runs/official-flow-20260907-v1/README.md)에서
공식 HTML 2건·질문 6개의 실제 MySQL·Qdrant·모델 연결과 인용을 확인했습니다. 고정 HTML을 재생한
소규모 AI-only 검토이며 공고당 청크 1개여서 일반적인 검색 품질 근거는 아닙니다.
PDF·첨부·다른 제공처 확장은 후속 범위입니다.
