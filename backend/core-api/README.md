# GovBiz Core API

브라우저에 공개하는 Spring Boot API입니다. 기업마당·K-Startup·과기정통부·충청남도 수출입공지 수집기를 제공하며, 벡터 색인을 준비한 뒤 MySQL에
공개하고, 저장된 공고의 검색·상세 조회와 기업마당 공식 원문 근거 질문을 담당합니다.

프로젝트 전체 설명은 [메인 README](../../README.md), 계층·Facade·DI 설계는
[아키텍처 README](../../docs/architecture/README.md#core-api-업무-흐름과-외부-경계), 기술 선택과 구현 범위는
[기술 구성](../../docs/technology.md)과 [구현 현황](../../docs/implementation-status.md),
실제 실행 순서는 [호출·데이터 흐름](../../docs/architecture.md)을 참고하세요.

## 실행

기업 맞춤 리포트는 `ai.govbiz.core.dailyreport`에서 저장된 기업 조건·지원 목적을 기존 검색과 HTML 근거 답변에
연결합니다. `V12`는 수신 설정, 날짜별 리포트와 일일 생성 예산을 저장합니다. 수신 주소 확인·동의 후 SMTP로
발송하며 자동 발송은 기본 비활성화입니다. [API·설정·장애 경계와 사용 순서](../../docs/daily-reports.md)를 참고하세요.
관련도는 선정 확률이 아니며 PDF/HWP 전체 분석·뉴스 브리핑은 포함하지 않습니다.

중복 지원 검토는 `ai.govbiz.core.combinationreview`에 세션 인증 기반 생성·목록·상세·입력 수정 API를 구현했습니다.
V6는 검토 입력, V7은 실행 스냅샷·원본 파일 이력을 저장합니다. 공식 첨부 자동 수집·PDF/HWPX 파싱과
단일 Agent 분석을 연결했으며 사용자 화면은 아직 미연결입니다.
[기능 설계와 구현 경계](../../docs/duplicate-support-review-design.md)를 참고하세요.

| 중복 지원 검토 API | 동작 |
|---|---|
| `POST /api/v1/combination-reviews` | 제목·2~3개 사업의 현재 입력 생성. 201과 상세 본문·Location 반환 |
| `GET /api/v1/combination-reviews?size=20&beforeId=123` | 본인 목록, 생성 ID 내림차순. size 1~50, beforeId 생략 가능 |
| `GET /api/v1/combination-reviews/{id}` | 본인 상세 입력·버전 조회 |
| `PUT /api/v1/combination-reviews/{id}/inputs` | 제목·사업 목록 전체 교체. expectedRevision 일치 시 204, 충돌 시 409 |
| `POST /api/v1/combination-reviews/{id}/runs` | expectedRevision·requestKey·선택적 additionalFacts로 동기 분석. 새 성공 201, 동일 요청 재조회 200 |
| `GET /api/v1/combination-reviews/{id}/runs` | 본인 실행 목록, size/beforeId 커서 |
| `GET /api/v1/combination-reviews/{id}/runs/{runId}` | 당시 입력·근거·설정·결과 또는 실패 조회 |
| `GET /api/v1/combination-reviews/{id}/runs/{runId}/sources/{documentIndex}` | 실행 당시 원본 파일 다운로드. documentIndex는 0부터 시작 |

소유자는 기존 세션 쿠키를 검증한 Account로 결정하며, 관리자도 타인 검토를 조회·수정할 수 없습니다.
없는 검토와 타인 검토는 같은 404를 반환합니다. 성공 응답은 `Cache-Control: no-store`이며 시각은 `+09:00`입니다.
쓰기 요청의 기존 Origin 방어를 유지하고 CORS 허용 메서드에 PUT을 추가했습니다. 상세 JSON·오류 코드는 위 설계 문서에 있습니다.

자동 수집은 BIZINFO의 숫자형 `PBLN_...` ID와 세부사업 ID가 없는 공고를 지원합니다. 공식 상세에 직접 연결된
기업마당 PDF/HWPX 및 중기부 사업공고의 PDF/HWPX를 읽습니다. 사용자 URL·HWP·스캔 PDF/OCR·ZIP 내부 탐색은 지원하지 않습니다.
원문·입력·결과는 실행마다 보존하며 기존 검색 Qdrant 색인과 분리됩니다. 자동 수집 근거를 사람 검수 완료로 표시하지 않습니다.
같은 요청 키는 AI를 재호출하지 않습니다. 새 실행은 계정 식별자로 기존 공개 요청 제한을 공유하며,
검토별 DB 동시 실행 1개·Core 프로세스별 중복 검토 2개 한도를 추가로 적용합니다.

`CombinationReviewRunService`는 실행 순서와 저장을, `AiCombinationReviewFacade`는 AI 호출·계약 검증 경계를 담당합니다.
첨부 파싱·문서 변환과 AI DTO 변환은 각각 `client/mapper/CombinationReviewDocumentMapper`, `AiCombinationReviewMapper`에 둡니다.
업무 실패는 `domain/exception`, 외부 시스템 실패는 `client/exception`에 두어 Repository·Client가 Service에 역으로 의존하지 않습니다.
이는 프로젝트의 기능 중심 레이어드 구조이며 범용 port/interface나 전달만 하는 Facade를 추가한 구조는 아닙니다.

JDK 21과 MySQL 8.4가 필요합니다. 실제 공고 동기화·의미 검색에는 실행 중인 AI Service와 Qdrant도
필요합니다. 전체 서비스를 함께 실행하는 방법은 [인프라 README](../../infrastructure/README.md)를 참고하세요.

저장소 루트에서 다음 명령으로 실행합니다. 네이티브 실행은 루트 `.env`를 자동으로 읽지 않으므로
필요한 환경변수를 현재 프로세스에 설정해야 합니다.

```bash
cd backend/core-api
./gradlew bootRun
```

기본 주소는 `http://127.0.0.1:8080`입니다. 실행 시 Flyway가 MySQL 스키마를 적용합니다.
`DATA_GO_KR_SERVICE_KEY`가 비어 있으면 수집 작업은 실패를 기록하며 다음 주기에 다시 시도합니다.
빈 검색어 목록은 이미 공개된 제공처의 DB 스냅샷에서 최신 공고를 반환하며, 상세 조회와 함께 이후
제공처별 색인 장애에도 사용할 수 있습니다. 검색어가 있는 검색은 AI Service와 해당 공고 버전의
색인이 있어야 성공합니다. 새 카탈로그 공개에도 색인 준비가 필수이므로 기업마당 키만으로 동기화가
완료되지는 않습니다.

## 검색 품질 평가 fixture 내보내기와 캡처

### 실제 공고 fixture 초안 내보내기

실데이터 평가의 시작점은 `evaluation-fixture-export` 프로필입니다. 이 비웹 프로필은 현재 MySQL에서
색인 준비가 확인된 제공처의 공개 공고 중 지정한 `referenceDate` 기준 `OPEN` 공고만 읽고, 운영 색인과 같은 `SupportProgramIndexDocumentMapper.fromCatalog`로
`id`·`contentHash`·`text`를 만듭니다. 공고 수와 카탈로그 지문을 포함한 전체 fixture 초안을 기록하므로,
이후 캡처 결과가 같은 공고 스냅샷에서 나왔는지 확인할 수 있습니다.

이 프로필은 웹 서버·기업마당/K-Startup 동기화·누락 색인 복구를 끄며 Qdrant, AI Service, OpenAI를 호출하지 않습니다.
`name`, `reference-date`, `output-path`는 반드시 지정해야 합니다. 기준 날짜는 실행 시점의 오늘이 아니라
저장된 신청 시작·종료일로 접수 상태를 다시 계산하는 기준입니다.

```bash
cd backend/core-api
./gradlew bootJar

SPRING_PROFILES_ACTIVE=evaluation-fixture-export \
APP_SUPPORT_PROGRAM_SEARCH_FIXTURE_EXPORT_NAME=support-program-catalog-20260905-v1 \
APP_SUPPORT_PROGRAM_SEARCH_FIXTURE_EXPORT_REFERENCE_DATE=2026-09-05 \
APP_SUPPORT_PROGRAM_SEARCH_FIXTURE_EXPORT_OUTPUT_PATH=/absolute/path/support-program-fixture.json \
java -jar build/libs/govbiz-core-api-0.0.1-SNAPSHOT.jar
```

생성 파일의 `cases`는 빈 배열(`[]`)입니다. 질문의 `id`·`query`·`split`을 고정하고, 평가 전에 선택한
AI-only·혼합·사람 검토 방식으로 `relevantIds`와 판정 출처를 확정합니다. 미확정 항목을 무관으로
바꾸지 않으며 현재 공유 실행은 AI-only 기준입니다. 질문 묶음의 `name`과 각 `cases`의 `id`·`query`·`split`은
fixture와 순서·내용까지 같아야 합니다. 내보내기는 빈 적격 카탈로그, 누락된 정렬 시각, 중복 검색 문서 ID 등
검증에 실패하면 기존 출력 파일을 바꾸지 않으며, 모든 검증이 끝난 결과만 원자적으로 교체합니다.

### 실제 검색 흐름 캡처

fixture와 같은 질문 묶음을 준비한 뒤에는 공개 API를 반복 호출하지 않고
`evaluation-capture` 프로필을 실행합니다. 이 프로필은 웹 서버·기업마당/K-Startup 동기화·누락 색인 복구를 끈 뒤,
질문 묶음의 각 항목을 현재 `SupportProgramSearchService`에 전달합니다. 따라서 MySQL의 적격 공고 선정,
Qdrant·키워드 순위를 결합한 후보 최대 20개, AI 최종 추천 최대 5개라는 운영 검색 흐름에서 나온 ID를
그대로 JSON 파일에 기록합니다. fixture와 capture는 모두 `findSearchablePresent`로 준비된 제공처의
공고만 읽습니다. 기존 고정 평가 자료는 당시 스냅샷·실행 조건의 기록이며 이번 조회 범위로 다시 해석하지 않습니다.

질문 파일은 [예시](../../evaluation/support-program-search/query-set.example.json)를 복사해 준비합니다.
fixture 내보내기는 지정한 기준 날짜의 `OPEN` 공고만 담으므로, 캡처도 기본값인 `acceptingOnly=true`와
**같은 기준 날짜**로 실행해야 합니다.
실행 환경의 MySQL·AI Service·Qdrant는 실제 검색과 같은 상태여야 하며, AI 점수화 호출 비용이 발생할 수
있으므로 기본 실행이나 CI에는 포함하지 않습니다.

```bash
cd backend/core-api
./gradlew bootJar
SPRING_PROFILES_ACTIVE=evaluation-capture \
APP_SUPPORT_PROGRAM_SEARCH_CAPTURE_QUERY_SET_PATH=/absolute/path/query-set.json \
APP_SUPPORT_PROGRAM_SEARCH_CAPTURE_OUTPUT_PATH=/absolute/path/capture.json \
APP_SUPPORT_PROGRAM_SEARCH_CAPTURE_REFERENCE_DATE=2026-09-05 \
java -jar build/libs/govbiz-core-api-0.0.1-SNAPSHOT.jar
```

질문은 최대 100개이며, 하나라도 실패하거나 실행 중 카탈로그가 바뀌면 결과 파일을 쓰지 않습니다.
성공한 v2 캡처에는 기준 날짜·공고 수·카탈로그 지문·후보 ID·최종 추천 ID가 포함됩니다. 평가기는 fixture와
capture의 기준 날짜가 다르면 점수 계산을 거부합니다. 실제 공고의 검색 문서가 들어갈 수 있는 파일은
[평가 실행 보관 폴더](../../evaluation/support-program-search/runs/README.md)에서 관리합니다.
비밀정보를 제외한 고정 공유 실행의 스냅샷·판정·캡처·보고서는 Git에 포함하며, 새 실행과 임시 출력은
기본적으로 제외합니다. 선택한 판정 출처의 fixture와 비교해 지표를 계산하는 방법은
[검색 평가 자료](../../evaluation/support-program-search/README.md)를 참고하세요.

## 공개 API

검색, 대화 조건 해석, 공고별 근거 답변은 한 Core 프로세스에서 요청량·동시 실행 한도를 공유합니다.
기본값은 접속 주소별 최근 60초 6건, 전체 60건, 동시 4건이며, 초과 시 DB·AI 호출 전에
`429 SUPPORT_PROGRAM_RATE_LIMITED` 또는 `503 SUPPORT_PROGRAM_BUSY`로 거절합니다.
`Retry-After`·`retryAfterSeconds`를 반환하고 실제 작업 종료 시 동시 슬롯을 해제합니다.
Controller의 `SupportProgramRequestAdmissionService.execute`가 공개 요청 입장을 담당하고
기존 Search/Evidence Service의 업무 흐름은 유지합니다. 구현은 `supportprogram/service/admission`,
설정은 그 아래 `config`, 거절 예외는 `exception`에 둡니다.
설정·프록시/NAT 공유·비웹 평가 제외·다중 서버 한계는 [요청 제한 안내](../../docs/support-program-request-limits.md)에 있습니다.

| 메서드·경로 | 용도 |
|---|---|
| `GET /api/v1/health` | Core API 자체 생존 상태 |
| `GET /api/v1/health/ai-service` | AI Service의 내부 Health 응답 확인 |
| `GET /api/v1/support-programs/readiness` | 공개 공고 스냅샷·검색 색인·최근 동기화 결과 상태 |
| `GET /api/v1/support-programs/catalog` | AI 없이 키워드·지역·분야·접수 상태로 공고 목록을 필터링·정렬·페이지 조회 |
| `GET /api/v1/support-programs/search` | 현재 MySQL 공고 카탈로그의 검색 또는 최신 목록 |
| `POST /api/v1/support-programs/search` | 이번 검색에만 기업 조건을 반영한 자연어 검색 |
| `POST /api/v1/support-programs/conversation/interpret` | 현재 발화로 조건 변경 초안을 만들며 사용자 확인 전에는 검색하지 않음 |
| `GET /api/v1/support-programs/detail` | 제공처 코드와 원본 ID로 현재 공고 상세 조회 |
| `POST /api/v1/support-programs/detail/answers` | 특정 공고의 공식 원문 근거 질문·답변 |
| `POST /api/v1/sample-items/prepare` | 계층 연결 학습용 예제 |
| `POST /api/v1/auth/signup` | 이메일·비밀번호 회원가입(201). 계정을 만들고 바로 브라우저 세션 쿠키 발급, 중복 이메일은 409 |
| `POST /api/v1/auth/login` | 이메일·비밀번호 로그인. 세션 JWT를 HttpOnly 쿠키로만 내려줌 |
| `POST /api/v1/auth/logout` | 세션 행 삭제와 쿠키 만료 |
| `GET /api/v1/auth/me` | 세션 쿠키로 현재 계정·권한 단계 조회 |
| `PUT /api/v1/me/password` | 현재 비밀번호 확인 뒤 변경. 요청한 세션만 남기고 다른 기기 세션 종료 |
| `GET /api/v1/me/deletion-preview`, `DELETE /api/v1/me` | 삭제 시 닫히는 모집글·제안 수 미리 보기와 계정 삭제(제안 철회·모집글 마감·기업 삭제·세션 삭제·`deleted_at`) |
| `POST /api/v1/auth/dev-login` | `ACCOUNT_DEV_LOGIN_ENABLED=true`일 때만 등록되는 개발용 시드 로그인. `tier`(ADMIN·MEMBER·COMPANY)로 관리자·회원·예시 기업 회원을 고름 |
| `GET /api/v1/me/company/lookup` | 로그인한 회원이 사업자등록번호로 국세청 등록 여부·상호·사업자 상태를 미리 보기(Bizno) |
| `GET` `POST` `PUT /api/v1/me/company` | 내 기업 조회·등록(계속사업자만, 201)·담당자 입력 항목 수정 |
| `GET` `PUT /api/v1/me/company/partner-profile` | 협업·파트너 설정(참여 역할·관심 분야·한 줄 소개·역량 태그) 조회·저장. 기업당 한 행 UPSERT |
| `GET /api/v1/partners/recruitments`, `GET .../{id}` | 파트너 모집글 목록(검색·찾는 역할·지역·내 글·정렬·페이지)과 상세. 세션 없이도 읽기 가능 |
| `POST /api/v1/partners/recruitments` | 기업을 등록한 회원이 접수 중인 공고 하나에 모집글 작성(201). 공고당 하나 |
| `POST /api/v1/partners/recruitments/{id}/proposals` | 기업을 등록한 회원이 남의 모집글에 참여 제안 보내기(201). 모집글당 하나 |
| `GET /api/v1/partners/proposals/{id}`, `POST .../accept` `.../decline` `.../withdraw` | 당사자만 제안 조회, 작성자의 수락·거절, 제안자의 철회 |
| `GET /api/v1/me/proposals?box=received\|sent` | 받은·보낸 제안함과 대기 건수 |

### 직접 조건으로 찾기

`SupportProgramCatalogController → SupportProgramCatalogService → SupportProgramRepository → MyBatis Mapper → MySQL`
흐름으로 이미 공개된 DB 공고를 읽습니다. 기존 `findPublishedPresent()`를 사용하며 AI Service·OpenAI·Qdrant·
기업마당·K-Startup 외부 API를 호출하지 않습니다. 이후 색인 장애와 무관하게 목록을 읽고, AI 요청량 제한 슬롯은 사용하지 않습니다.

`keyword`는 공고명 또는 기관명의 대소문자를 구분하지 않는 단순 포함 검색이며, `region`·`category`는 제공처
태그의 정확한 일치입니다. 필터는 AND로 결합합니다. 지역 선택은 자격 판정이 아니며 서울을 선택해도 전국 태그를
자동 포함하지 않습니다. `status` 기본값은 `OPEN`, `sort`는 `RECENT`, `page`는 1, `pageSize`는 12입니다.
접수 상태는 기존 Repository의 서울 기준 현재 날짜 계산을 그대로 사용합니다.

`sourceCode`로 전체(빈 값)/`BIZINFO`/`KSTARTUP`/`MSIT`/`CNTRADE_NOTICE`를 구분하고, `KSTARTUP` 선택 시 `startupStage`·`applicantType`·
`founderAge`를 추가할 수 있습니다. 공고의 원본 분류를 정확히 비교하며 자격을 추정하지 않습니다.
응답의 `startupStages`·`applicantTypes`·`founderAges`는 전체 공개 K-Startup 스냅샷의 선택지입니다.

현재는 한 번 읽은 공개 스냅샷을 Service에서 필터링·정렬·페이지 분할합니다. 응답에는 총건수와 전체 스냅샷의
지역·분야 선택지도 함께 포함하며, 목록에 AI 추천 이유·점수·자격 검토를 붙이지 않습니다. 목록 요청의 페이지 크기는
최대 50입니다. 요청·응답·정렬 및 확장 시 고려사항은 [직접 조건 검색 계약](../../docs/support-program-catalog.md)에 있습니다.

### 후속 대화 조건 해석

`SupportProgramConversationController → SupportProgramConversationService → AiSupportProgramConversationClient`
흐름으로 내부 `/internal/v1/support-program-conversation/interpret`를 한 번 호출합니다.
대화 상태는 브라우저 메모리에만 두며 Core는 DB·검색·색인을 호출하거나 상태를 저장하지 않습니다.
새 메시지와 전체 키를 갖춘 현재 context, 선택적인 마지막 질문·미확정 draftContext만 전달합니다.
nullable 조건도 키 자체는 필수이며 미입력은 명시적 null입니다. boolean은 JSON boolean만 허용합니다.

Core가 서울 기준일과 `govbiz-support-program-conversation-v1`을 보내고, 응답의 최대 6개 SET/CLEAR 변경에서
중복 필드·현재 메시지의 정확한 근거 인용·실제 날짜·문자 및 길이 제한을 검증합니다. 새 계약은 UTF-16 기준으로
message/query 500, region 50, industry/supportPurpose 100, 날짜 10, 질문/근거 160입니다. 상대 업력으로 설립일을
생성할 수 없습니다. 미변경 필드는 유지하고, 직전 초안이 있으면 여기에 병합하되 changedFields는 확정 context와
비교해 계산합니다. 공개 DTO·내부 AI DTO·도메인·검증 결과는 각 경계의 타입으로 분리합니다.

READY도 제안일 뿐이며 사용자가 확인한 뒤 기존 POST 검색을 별도로 호출합니다. 정보가 부족하면
CLARIFICATION_REQUIRED와 새 질문·초안을 반환합니다. 잘못된 AI 응답은 명시적 502 오류이며 질문이나
단문 검색으로 우회하지 않습니다. 해석과 확인 검색은 공유 요청 제한에서 각각 한 건입니다.
상세 계약과 상태 흐름은 [C02 안내](../../docs/conversation-condition-update.md)를 참고하세요.

### 검색·상세·근거 질문

- GET 검색: 필수 `query`는 최대 500 UTF-16 코드 단위이며 빈 문자열을 허용합니다. 탭·줄바꿈·캐리지 리턴을 제외한
  Unicode C 범주 문자(예: NUL·제로폭 문자·단독 surrogate)는 DB·AI 호출 전에 400으로 거부합니다.
  `acceptingOnly`의 기본값은 `true`이고
  이때 `OPEN` 공고만 대상으로 삼습니다. 검색어가 있으면 검증된 의미 검색 상위 20개와 전체 적격 공고의
  키워드 상위 20개를 같은 가중치의 RRF(`1 / (60 + 순위)`)로 결합하고, 최대 20개를 AI가 점수화하여
  기준을 통과한 0~5개를 반환합니다. 키워드는 색인 본문의 NFC·소문자 토큰 집합 교집합 수로 정렬하고
  동점은 최신순·제공처 포함 ID순입니다. RRF 동점은 의미 검색 순위·제공처 포함 ID순입니다.
  의미 검색 실패는 오류로 반환합니다. 게시된 공고나 미복구 기존 공고가 있지만 준비된 색인이 하나도 없으면
  자연어 검색은 빈 결과가 아니라 503을 반환합니다. 최초 빈 DB와 준비된 제공처의 정상 0건은 구별합니다.
  빈 검색어는 AI를 호출하지 않고 이미 공개된 DB 스냅샷에서 최신순 최대 5개를 반환하므로 이후 Qdrant 장애에도
  목록을 유지합니다. 아직 공개 세대·지문이 없는 신규/미검증 제공처의 공고는 이 최신 목록에 포함하지 않습니다.
- POST 검색: JSON의 `query`는 비어 있지 않은 최대 500 UTF-16 코드 단위 문자열이고,
  `acceptingOnly`는 생략 시 `true`입니다. 명시한 값은 JSON 부울만 허용하며 `null`·문자열·숫자는 거부합니다.
  선택 객체 `companyConditions`에는 `region`(50), `industry`(100), `supportPurpose`(100),
  `establishedOn`(10)을 넣을 수 있습니다. 상한은 앞뒤 공백을 포함한 입력의 UTF-16 코드 단위입니다.
  조건 텍스트는 제어·제로폭 문자 등 Unicode C 범주를 거부한 뒤 trim하고 빈 값은 미입력으로 처리합니다.
  설립일은 실제 달력의 `YYYY-MM-DD`로 `1900-01-01`부터 서울 기준 오늘까지이며, 빈 문자열·ASCII 공백만
  있는 값은 미입력입니다. 공백이 붙은 날짜·timestamp·존재하지 않는 날짜는 400입니다.
  모든 조건이 미입력이면 기존 질의만 전송합니다. 조건이 있으면 `SupportProgramSearchService`가
  질의와 지역·업종·지원 목적의 값만 최대 1,000자 내부 검색문으로 만들어 후보 조회에 반영하고,
  `AiSupportProgramRankingFacade`는 원질의를 바꾸지 않고 별도의 `companyConditions`와 ISO `referenceDate`를
  점수화 요청에 전달합니다. 설립일·서울 기준일·표제는 후보 검색어에 넣지 않으며 사용자 질의의 날짜는 보존합니다.
  지역 정보가 없거나 다르다는 이유만으로 Core에서 후보를 제외하지 않습니다.
  조건은 저장·응답 메타데이터에 포함하지 않으며, 공개 `query`는 trim한 원질의 그대로입니다.
  GET 검색·POST 검색·대화 조건 해석·원문 근거 질문은 같은 요청 제한을 공유합니다.
- 자격 검토: 조건 유무와 관계없이 점수화 계약은 `govbiz-support-program-ranking-v5`입니다.
  저장된 공식 API 본문 `summary` 최대 6,000, 지원대상 `targetDescription` 최대 2,000 Unicode code point를
  AI에 전달합니다. 둘 중 하나라도 잘리면 `sourceTextTruncated=true`이며 대상·지역 모두 `UNKNOWN`만 허용합니다.
  태그의 지역·분야는 후보 검색 보조 정보이고 자격 충족 근거로 인용할 수 없습니다.
  AI 응답의 대상·지역 판정에는 설명(1~160자)과 근거 배열(0~1개)이 필요하며 `MATCH`는 근거 1개가 필수입니다.
  근거는 `SUMMARY` 또는 `TARGET_DESCRIPTION`에서 실제 전송된 문장의 정확한 부분 문자열(1~240자)만 인정합니다.
  설명·인용의 상한은 Unicode code point이고 제어·제로폭 문자 등 Unicode C 범주는 거부합니다.
  원문에 없는 인용·누락된 검토·불충족 `INCOMPATIBLE`·잘못된 정렬은 정상 추천으로 숨기지 않고 내부 계약 오류로 반환합니다.
  관련도는 `2 × (semanticRelevance + supportTypeFit)`로 계산하며 자격 `UNKNOWN`을 감점하지 않습니다.
  의미 관련성 20/40점 이상, 명백한 자격 불일치 제외 후 관련도순으로 최대 5개입니다. 기존 총점 60점 컷과 MATCH 우선은 제거했습니다.
  자연어 검색 결과의 `eligibilityReview`는 전체 상태 `MATCH`/`REVIEW_REQUIRED`, 기준 `OFFICIAL_API_TEXT`,
  대상·지역별 `status`, `explanation`, `evidence[{field,quote}]`를 추천 이유와 별도로 반환합니다.
  이는 HTML을 정리한 **공식 API 본문 기준**의 검토이며 상세 페이지 전체·첨부 PDF/HWP를 확인했다는 뜻이 아닙니다.
  최신 목록·상세의 `eligibilityReview`는 `null`입니다. 자격 검토는 검색 결과에만 존재하며 DB에 저장하지 않습니다.
- 검색 준비 상태: `readiness`는 필수 `sources` 배열로 제공처별 저장 공고 수·색인 준비·동기화 성공/실패를
  반환합니다. 전체 공고 수는 검색 가능한 제공처의 합계이고 `indexReady`는 한 제공처 이상 준비되었는지입니다.
  `SEARCHABLE_WITH_PARTIAL_SOURCES`는 일부만 준비된 상태이며 검색은 준비된 범위에서 가능합니다.
  `SEARCHABLE`은 공고 수가 0인 성공 스냅샷도 포함하며,
  `SEARCHABLE_WITH_SYNC_FAILURE`은 이전 스냅샷은 검색 가능하지만 최신 수집·사전 색인 시도가 실패한 경우입니다.
  `PREPARING`은 공개 공고 없는 초기 상태 또는 결과가 아직 없는 첫 동기화이고, `UNAVAILABLE`은 현재
  공개 스냅샷의 색인 준비가 확인되지 않은 경우입니다. 상태 행 없는 현재 공고의 제공처도 `UNAVAILABLE`로
  표시합니다. 초기 빈 DB에는 `BIZINFO`와 각각 수집을 활성화한 `KSTARTUP`·`MSIT`·`CNTRADE_NOTICE`를 포함합니다.
  시각은 `Asia/Seoul` 오프셋을 포함한 ISO-8601 문자열입니다.
- 상세: 필수 `sourceCode`는 `[A-Z][A-Z0-9_]{0,63}` 형식, `sourceProgramId`는 최대 255자이며 공백만 있는 값은 허용하지
  않습니다. 현재 노출된 행만 반환하며, 없는·미노출 공고는 404입니다. 검색 문맥이 없으므로 추천 이유는
  빈 배열, 추천 점수는 `null`입니다.
- 원문 근거 질문: `sourceCode`, `sourceProgramId`, 최대 500자의 `question`을 JSON body로 보냅니다. 현재 공개된
  `BIZINFO` 공고에만 제공하며, 사용자가 이 endpoint를 호출했을 때만 공식 HTTPS 상세 HTML을 수집합니다.
  MySQL 원문 캐시가 같은 URL로 6시간 이내면 재사용하고, 아니면 읽기 가능한 텍스트를 검증·저장한 뒤 최대 50개
  결정적 청크를 별도 Qdrant evidence 컬렉션에서 검색합니다. 답변은 `ANSWERED`(공식 원문 인용 1개 이상) 또는
  `INSUFFICIENT_EVIDENCE`(인용 없음)와 최대 5개 인용 발췌·공식 URL·청크 순서를 반환합니다. 첨부파일·PDF·OCR·다른
  제공처 원문은 지원하지 않습니다. 공식 원문 수집 실패는 503, 지원하지 않는 현재 제공처는 422입니다.
  상세 URL의 리디렉션은 매번 공식 HTTPS 호스트와 같은 `pblancId`인지 검증하며 최대 3회 따릅니다.
  HTML은 jsoup `1.23.2`로 파싱하고 `.support_project_detail`의 제목이 요청한 공고와 일치할 때
  `.view_cont` 본문만 추출합니다. 인용에는 검색된 청크 전체를 반환하며 청크당 최대 1,500 UTF-16 코드 단위입니다.
- 수집기는 `BIZINFO`·`KSTARTUP`·`MSIT`·`CNTRADE_NOTICE`이며 기업마당 외 수집기는 명시적으로 켜야 합니다. 자연어 검색·평가 fixture/capture는
  `findSearchablePresent`의 제공처 상태 JOIN으로 `index_ready=true`인 공고만 읽고, 색인 복구는
  미준비 공고도 제공처별로 처리합니다. 최신 목록은 `findPublishedPresent`로 공개된 스냅샷만 읽되
  이후 색인 장애와 분리합니다. 내부 식별자 `sourceCode:sourceProgramId`로 같은 원본 ID를 구분합니다.
  K-Startup도 같은 벡터 색인과 자연어 추천·필터·상세 조회를 사용합니다. 별도 공식 HTML 원문 질문은 기업마당만 지원합니다.

제공처별 복구 실패 격리와 구현/미구현 범위는 [6단계 다중 제공처 준비](../../docs/support-program-multi-source-preparation.md)에 정리합니다.

요청·응답 JSON과 상세 오류 계약은 [지원사업 API 계약](../../docs/support-program-search-contract.md),
SampleItem 예제는 [별도 계약](../../docs/sample-item-contract.md)에 있습니다.

## 설정

### 과기정통부·충청남도 수출입공지

- `MsitSupportProgramCatalogSyncService`와 `CnTradeNoticeSupportProgramCatalogSyncService`는 각각의
  구체 Facade/Client에서 전체 페이지를 검증한 뒤 기존 `SupportProgramIndexSyncService`로 색인하고
  `SupportProgramRepository`에서 해당 출처만 공개합니다. 중간 수집·색인 실패는 기존 스냅샷을 유지합니다.
- `MSIT_SYNC_ENABLED`와 `CNTRADE_NOTICE_SYNC_ENABLED`는 기본 `false`입니다. 각각 활용 승인과 최초
  임베딩 비용을 확인한 뒤 켭니다. 전용 `MSIT_API_KEY`/`CNTRADE_NOTICE_API_KEY`를 생략하면
  `DATA_GO_KR_SERVICE_KEY`를 사용합니다. 같은 키라도 API 활용 승인은 각각 필요합니다.
- 두 출처의 `*_API_BASE_URL` 기본값은 `https://apis.data.go.kr`, 연결/응답 제한시간은 `2s`/`20s`,
  `*_SYNC_INITIAL_DELAY`/`*_SYNC_FIXED_DELAY`는 `PT0S`/`PT6H`입니다.
- MSIT는 `businessAnnouncMentList`의 공식 상세 URL에 있는 `nttSeqNo`를 ID로 사용합니다.
  실응답이 페이지 크기를 10건으로 제한하므로 전체 수집에 시간이 걸립니다. 별도 스케줄러로 기존 수집과 격리합니다.
  API가 본문·접수 기간·지역·분야를 제공하지 않아 제목·담당 부서 기반으로 검색하며 신청 자격은 원문 확인이 필요합니다.
- CNTRADE_NOTICE는 `getNotiList`의 `lbbNo`가 ID이며 API 본문을 보존합니다. 지원사업뿐 아니라
  일반 수출입 공지도 포함합니다. API에 개별 상세 URL이 없어 확인된 **공식 공지 목록**을 제공하며 제목으로 찾습니다.
- 두 출처 모두 게시일을 접수일로 간주하거나 기관명으로 지역을 추정하지 않습니다. 접수 상태는 `UNKNOWN`,
  지역·분야는 빈 배열입니다. 필터 검색은 `ALL`/`UNKNOWN`에서, AI 검색은 접수 중 제한을 해제한 경우에 조회할 수 있습니다.
  K-Startup 전용 필터와 기업마당 전용 원문 추가 질문은 확장하지 않습니다.
- 2026-09-09 실호출 확인: MSIT 1·2페이지 성공(전체 4,248건 메타데이터). CNTRADE_NOTICE는 HTTP 200의
  `04 HTTP_ERROR`를 반환해 실제 수집은 미확인입니다. 문서 기반 스텁 통과를 실제 제공처 정상 동작으로 간주하지 않습니다.

공식 명세: [과기정통부 사업공고](https://www.data.go.kr/data/15074634/openapi.do),
[충청남도 수출입공지](https://www.data.go.kr/data/15097093/openapi.do).

기본값의 기준은 [`application.properties`](src/main/resources/application.properties)입니다.
Compose는 일부 주소·CORS 값을 내부 네트워크에 맞게 덮어씁니다.

| 환경변수 | 기본값 | 용도 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://127.0.0.1:3306/govbiz` | MySQL JDBC 주소 |
| `SPRING_DATASOURCE_USERNAME` | `govbiz` | MySQL 사용자 |
| `SPRING_DATASOURCE_PASSWORD` | `govbiz-local` | 로컬 개발용 MySQL 비밀번호 |
| `SUPPORT_PROGRAM_REQUEST_PER_CLIENT_PER_MINUTE` | `6` | 검색·대화 조건 해석·근거 답변의 접속 주소별 최근 60초 한도 |
| `SUPPORT_PROGRAM_REQUEST_GLOBAL_PER_MINUTE` | `60` | 한 Core 프로세스의 검색·근거 답변 최근 60초 한도 |
| `SUPPORT_PROGRAM_REQUEST_MAX_CONCURRENT` | `4` | 검색·근거 답변 동시 처리 한도 |
| `DATA_GO_KR_SERVICE_KEY` | 빈 값 | 기업마당 수집용 공공데이터포털 키 |
| `KSTARTUP_API_KEY` | 빈 값 | K-Startup 조회서비스 활용 승인을 받은 공공데이터포털 키 |
| `KSTARTUP_API_BASE_URL` | `https://apis.data.go.kr` | K-Startup API origin |
| `KSTARTUP_API_CONNECT_TIMEOUT` / `KSTARTUP_API_READ_TIMEOUT` | `2s` / `10s` | K-Startup 외부 호출 제한시간 |
| `KSTARTUP_SYNC_SCOPE` | `RECENT_YEAR` | API에 1년 전 날짜 조건 전달. `RECENT_THREE_MONTHS`는 3개월 전, `OPEN`은 API 모집 중 공고만. 실응답에는 장기 공고도 포함될 수 있음 |
| `KSTARTUP_SYNC_ENABLED` | `false` | 최초 임베딩 비용 확인 뒤 활성화 |
| `KSTARTUP_SYNC_INITIAL_DELAY` / `KSTARTUP_SYNC_FIXED_DELAY` | `PT0S` / `PT6H` | 첫 수집 지연 / 완료 후 다음 수집 지연 |
| `ACCOUNT_SESSION_TTL` | `P30D` | "로그인 상태 유지"를 켠 세션의 절대 만료 기간 |
| `ACCOUNT_SESSION_SHORT_TTL` | `PT12H` | "로그인 상태 유지"를 끈 세션의 절대 만료 기간. 쿠키는 브라우저 세션 쿠키 |
| `ACCOUNT_SESSION_IDLE_TTL` | `P7D` | 마지막 사용 뒤 세션을 끝내는 유휴 기간 |
| `ACCOUNT_JWT_SECRET` | 없음(필수) | 세션 JWT HS256 서명 비밀키(32자 이상). 코드에 기본값이 없어 비어 있으면 기동 실패. Compose·`.env.example`은 로컬 개발용 값을 넣음 |
| `ACCOUNT_COOKIE_SECURE` | `true` | 세션 쿠키 `Secure` 속성. HTTPS가 없는 로컬 개발에서만 `false` |
| `ACCOUNT_DEV_LOGIN_ENABLED` | `false` | `true`이면 `POST /api/v1/auth/dev-login`이 등록되어 비밀번호 없이 시드 계정 세션 발급 |
| `ACCOUNT_DEV_LOGIN_EMAIL` | `admin@govbiz.local` | 개발용 관리자 시드 계정 이메일. 없으면 ADMIN 역할·이메일 인증 완료로 생성 |
| `ACCOUNT_DEV_LOGIN_MEMBER_EMAIL` | `member@govbiz.local` | `{"tier":"MEMBER"}`로 부를 때 쓰는 기업 없는 회원 시드 계정 이메일 |
| `ACCOUNT_DEV_LOGIN_COMPANY_EMAIL` | `company@govbiz.local` | `{"tier":"COMPANY"}`로 부를 때 쓰는 기업 회원 시드 계정 이메일. 예시 기업(가상 사업자등록번호)을 함께 등록 |
| `ACCOUNT_DEV_LOGIN_PASSWORD` | `govbiz-admin1` | 시드 계정 생성 시 저장하는 비밀번호 |
| `BIZNO_API_KEY` | 빈 값 | 기업 등록 시 사업자등록번호를 확인하는 Bizno API 키. 비어 있으면 조회·등록이 503 `BIZNO_NOT_CONFIGURED` |
| `BIZNO_URL` | `https://bizno.net/api/fapi` | Bizno 조회 endpoint |
| `BIZNO_API_CONNECT_TIMEOUT` / `BIZNO_API_READ_TIMEOUT` | `2s` / `10s` | Bizno 연결·응답 제한시간 |
| `BIZINFO_API_BASE_URL` | `https://apis.data.go.kr` | 기업마당 API 주소 |
| `BIZINFO_API_CONNECT_TIMEOUT` | `2s` | 기업마당 연결 제한시간 |
| `BIZINFO_API_READ_TIMEOUT` | `10s` | 기업마당 응답 제한시간 |
| `BIZINFO_SOURCE_DOCUMENT_BASE_URL` | `https://www.bizinfo.go.kr` | RestClient 기본 origin. 실제 요청은 검증된 공고 `sourceUrl`의 절대 URI를 사용하므로 이 값으로 요청 주소를 변경하지 않음 |
| `BIZINFO_SOURCE_DOCUMENT_CONNECT_TIMEOUT` | `2s` | 공식 원문 연결 제한시간 |
| `BIZINFO_SOURCE_DOCUMENT_READ_TIMEOUT` | `10s` | 공식 원문 응답 제한시간 |
| `BIZINFO_SYNC_ENABLED` | `true` | 기업마당 수집·색인 준비·DB 공개 작업 실행 여부 |
| `BIZINFO_SYNC_INITIAL_DELAY` | `PT0S` | 첫 수집 작업까지의 지연 |
| `BIZINFO_SYNC_FIXED_DELAY` | `PT6H` | 이전 수집 작업 종료 후 다음 실행까지의 지연 |
| `AI_SERVICE_BASE_URL` | `http://127.0.0.1:8000` | 내부 AI Service 주소 |
| `AI_SERVICE_CONNECT_TIMEOUT` | `1s` | AI Service 연결 제한시간 |
| `AI_SERVICE_READ_TIMEOUT` | `35s` | AI Health·대화 조건 해석·원문 근거 답변 응답 제한시간 |
| `AI_RANKING_READ_TIMEOUT` | `55s` | 지원사업 최종 점수화 전용 응답 제한시간 |
| `AI_SEMANTIC_SEARCH_READ_TIMEOUT` | `30s` | 의미 검색·색인 응답 제한시간 |
| `SUPPORT_PROGRAM_INDEX_ENABLED` | `true` | 이미 공개된 공고의 누락 벡터 자동 복구 여부 |
| `SUPPORT_PROGRAM_INDEX_INITIAL_DELAY` | `PT0S` | 첫 누락 벡터 복구까지의 지연 |
| `SUPPORT_PROGRAM_INDEX_FIXED_DELAY` | `PT1M` | 이전 복구 작업 종료 후 다음 실행까지의 지연 |
| `APP_CORS_ALLOWED_ORIGIN` | `http://localhost:5173` | 허용할 Web origin |

추천 점수화만 AI 모델 `45s`·Agent 실행 `50s`·Core 읽기 `55s`로 제한합니다.
`AiServiceClientProperties.rankingReadTimeout`과 전용 `aiRankingRestClient`를 사용하며
`HttpAiSupportProgramRankingClient`에만 주입합니다. 공유 `aiServiceRestClient`의 `35s`와
의미 검색·색인의 `aiSemanticSearchRestClient` `30s`는 유지합니다. 대화 조건 해석·원문 근거 답변의
AI 모델·Agent 제한도 기존 `25s`·`30s`이며 Health와 함께 공유 Core 제한을 사용합니다.
검색은 의미 검색과 점수화를 순서대로 호출하므로 브라우저 검색 제한은 두 Core 읽기 제한
`30s + 55s`에 여유를 둔 `90s`입니다. 이 값은 요청 제한이며 응답시간 보장은 아닙니다.
AI의 HTTP 504나 Core 읽기 시간 초과는 기존대로 `TIMEOUT → 504 AI_SERVICE_TIMEOUT`으로 전달하며
빈 결과·일반 장애로 바꾸지 않습니다. 후보 수·모델·프롬프트·원문 자격 판단 계약은 바꾸지 않습니다.

`SUPPORT_PROGRAM_INDEX_ENABLED=false`는 별도 복구 스케줄러만 끕니다. 새 카탈로그 공개 전의 필수
색인은 계속 실행됩니다. 두 스케줄러는 각각 별도의 단일 스레드에서 실행되며 실패를 기록한 뒤
다음 주기에 다시 시도합니다. 현재 공개된 수동 동기화 HTTP API는 없습니다.

기업마당 키는 Encoding·Decoding 형식 모두 받을 수 있으며 Client가 요청 전에 정규화합니다.
목록 DTO는 검색·저장에 사용하는 원본 필드만 디코딩합니다. 사용하지 않는 신청 방법
(`reqstMthPapersCn`) 필드는 무시하며, 실제 사용하는 필드와 페이지 완전성 검증은 유지합니다.
실제 인증키·운영 DB 비밀번호는 환경변수로 주입하고 Git에 기록하지 않습니다. OpenAI 키는
Core API가 아닌 AI Service에만 설정합니다.

## 코드 구조

기본 패키지는 `ai.govbiz.core`, Gradle 프로젝트명은 `govbiz-core-api`입니다.

```text
supportprogram/
├── controller            # 공개 HTTP 진입점
│   └── dto               # 공개 요청·응답 계약
├── service/
│   ├── search             # DB 조회 → 의미·키워드 순위 결합 → AI 점수화
│   ├── conversation       # 대화 변경 인용 검증·초안 병합·확정 조건 대비 변경 계산
│   ├── detail             # 현재 공고 상세 조회
│   ├── readiness          # 제공처별 준비 상태와 전체 검색 범위 집계
│   ├── evidence           # 공식 원문 캐시·청킹 → 근거 검색·답변
│   ├── sync               # 수집·색인 준비·DB 공개와 별도 벡터 복구
│   ├── evaluation         # 비웹 fixture 내보내기·검색 품질 평가 캡처 프로필
│   └── dto                # 검증된 내부 실행 결과
├── facade                 # 기업마당 수집·공식 원문·AI 응답 검증·도메인 변환
├── client/
│   ├── bizinfo            # 기업마당 HTTP·목록/공식 HTML 검증·외부 DTO 정규화
│   ├── kstartup           # K-Startup 페이지 검증·공식 상세 URL·원문 대상·전용 분류 정규화
│   └── ai                 # AI 내부 HTTP 계약·조건 해석·공고/원문 청크 색인과 답변
├── repository            # 도메인↔DB 행 변환·트랜잭션·저장·조회
│   └── mapper            # MyBatis Mapper, DbRow
├── domain                 # 업무 모델·서울 날짜 기준 접수 상태 규칙
└── helper                 # 지원사업 하위 흐름이 함께 쓰는 보조 작업
account/
├── controller            # 로그인·로그아웃·내 계정, 개발용 로그인, 기업 등록·수정 HTTP 진입점
│   └── dto               # 공개 요청·응답 계약
├── service               # 회원가입, 로그인 검증·시도 제한, JWT 세션, 비밀번호 변경·계정 삭제, 기업 등록(사업자등록번호 조회)
├── client/bizno          # Bizno 사업자등록번호 조회 HTTP·응답 검증·오류 변환
├── repository            # 계정·세션·기업 저장과 조회, DbRow 변환
│   └── mapper            # MyBatis Mapper, DbRow
├── domain                # 계정·역할·세션·기업 업무 모델
├── client/bizno          # Bizno 사업자등록번호 조회 HTTP·응답 검증·오류 변환
├── helper                # HS256 JWT 발급·검증·해시, 세션 쿠키 발급·읽기, 이메일 정규화
├── web                   # Account 파라미터 resolver, Origin 검사 interceptor와 MVC 등록
└── config                # BCrypt, 세션·개발 로그인 설정
partner/
├── controller            # 파트너 모집글 목록·상세·작성, 제안 보내기·수락·거절·철회, 제안함 HTTP 진입점
│   └── dto               # 공개 요청·응답 계약
├── service               # 작성·제안 조건(기업 등록·공고 접수 중·마감일·공고당 하나·당사자) 확인과 조회
├── repository            # 모집글·제안 저장, 기업·계정·공고 조인 조회, 검색·필터·정렬·페이지, 제안 수
│   └── mapper            # MyBatis Mapper, DbRow
└── domain                # 모집글·제안·역할 업무 모델, 조회 시점 모집·제안 상태 계산
_health                    # Core API Health
_health_ai_service         # AI Service Health의 Controller → Service → Client
_sampleitem                # 학습 예제
_common                    # 실제 공유하는 HTTP·AI 설정·JSON·CORS·서울 기준 시계·오류 처리
```

외부 호출의 기본 흐름은 `Controller → Service → Facade → Client`입니다. Facade는 하위 호출·검증·변환을
묶을 때 사용하며, DB 접근은 `Service → Repository → MyBatis Mapper → Mapper XML → MySQL`입니다.
Facade와 Domain은 MyBatis Mapper를 직접 호출하지 않습니다.

| 타입 | 소유 위치·역할 |
|---|---|
| `Request`, `Response` | 공개 HTTP 계약은 해당 기능의 `controller/dto` |
| 외부 `Request`, `Payload` | 상대 시스템별 `client/dto` |
| `Result` | 검증된 실행 결과는 `service/dto` |
| 업무 모델 | 프레임워크에 의존하지 않는 `domain` |
| `DbRow` | `repository/mapper`의 DB 행 타입. Repository 밖으로 노출하지 않음 |
| `Helper` | 실제 반복 보조 작업. 특정 기능의 하위 흐름이 함께 쓰면 해당 기능의 `helper`, 둘 이상의 기능이 함께 쓰면 `_common/helper` |

SQL은 [`SupportProgramMapper.xml`](src/main/resources/mybatis/supportprogram/repository/SupportProgramMapper.xml)에
명시하며 JPA·JdbcClient·annotation SQL을 혼용하지 않습니다. Repository가 JSON 배열과 DbRow를
변환하고 `SupportProgramStatusResolver`를 호출합니다. 상세 규칙은 [AGENTS.md](../../AGENTS.md)를 따릅니다.

## 데이터 관리와 오류 처리

- Flyway [V1](src/main/resources/db/migration/V1__create_support_program.sql)은 공고 테이블,
  [V2](src/main/resources/db/migration/V2__add_support_program_sync_generation.sql)는 최신 수집 시작 세대,
  [V3](src/main/resources/db/migration/V3__create_support_program_source_document.sql)는 공고별 공식 원문
  테이블, [V4](src/main/resources/db/migration/V4__create_support_program_sync_status.sql)는 공개 스냅샷의
  세대·지문·공고 수·색인 준비와 최근 동기화 결과,
  [V5](src/main/resources/db/migration/V5__create_account.sql)는 계정과 세션 테이블,
  [V6](src/main/resources/db/migration/V6__create_company.sql)는 계정당 하나인 기업 테이블(사업자번호 UNIQUE),
  [V7](src/main/resources/db/migration/V7__add_support_program_startup_details.sql)은 K-Startup 전용 분류 JSON과 형식·제공처 제약,
  [V8](src/main/resources/db/migration/V8__create_partner_recruitment.sql)은 계정·기업·공고에 묶인 파트너 모집글 테이블,
  [V9](src/main/resources/db/migration/V9__create_partner_proposal.sql)은 모집글과 제안 계정·기업에 묶인 파트너 제안 테이블,
  [V10](src/main/resources/db/migration/V10__create_combination_review.sql)은 중복 검토 건과 선택 사업,
  [V11](src/main/resources/db/migration/V11__create_combination_review_run.sql)은 실행 스냅샷과 원본 파일 테이블,
  [V12](src/main/resources/db/migration/V12__create_daily_report.sql)는 일일 리포트 구독·발송 테이블,
  [V13](src/main/resources/db/migration/V13__create_company_partner_profile.sql)은 기업당 하나인 협업·파트너 설정 테이블을 만듭니다.
  적용된 migration은 수정하지 않고 새 버전을 추가합니다.
- 전체 수집·검증·색인이 끝난 뒤 최신 시작 세대만 공개합니다. 해당 제공처 행 미노출 처리와 UPSERT를
  하나의 짧은 DB transaction으로 묶고, 같은 transaction에서 스냅샷 지문·공고 수·`indexReady=true`·성공
  시각을 기록합니다. 외부 HTTP 호출은 transaction 밖에서 수행합니다.
- 수집 또는 공개 전 필수 색인이 실패하면 현재 세대일 때만 실패 시각을 기록합니다. 이때 이전 공개 스냅샷의
  공고·색인 준비 상태는 바꾸지 않습니다. 별도 복구가 실패하면 자신이 읽어 색인한 세대·지문·공고 수와 상태 행이
  여전히 일치할 때만 `indexReady=false`로 바꿉니다. 복구 성공도 같은 조건에서만 준비 완료를 기록합니다.
- 복구는 제공처별로 벡터를 확인하고 상태를 갱신합니다. 한 제공처의 실패 이후에도 다른 제공처를 처리하고,
  전체 처리가 끝나면 실패를 오류로 전달합니다. 최신 수집 실패와 기존 공개 스냅샷 복구 실패는 서로 다른 상태입니다.
- Repository의 `findSearchablePresent()`는 공개 스냅샷과 색인 준비를 모두 요구하고, 최신 목록용
  `findPublishedPresent()`는 공개 스냅샷만 요구합니다. Mapper의 `findPublishedPresent(requireIndexReady)`가
  같은 SQL에서 색인 조건만 선택하며, 색인 복구용 전체 조회 `findPresent()`와 혼용하지 않습니다.
- V4 적용 전부터 있던 공고에는 공개 세대·검색 문서 지문·과거 색인 성공 여부가 없습니다. 다만 현재 공개된
  제공처별 공고가 1건 이상이고 해당 제공처 전체 색인 복구가 성공하면, 그때 읽어 색인한 지문·공고 수를 sentinel 세대 `0`과
  함께 한 번만 채택합니다. 빈 초기 DB는 `PREPARING`, 복구 전 legacy 공고는 `UNAVAILABLE`이며, 실제 새 스냅샷의 지문은 이
  bootstrap이 덮어쓰지 않습니다.
- 원본 ID 표기의 대소문자만 바뀌어도 UPSERT가 최신 표기를 저장하여 DB ID와 벡터 ID를 맞춥니다.
  DB 고유키의 비교는 `utf8mb4_0900_ai_ci` collation을 따릅니다.
- 접수 상태를 DB에 고정 저장하지 않습니다. 조회 시 날짜를 우선 적용하고, 날짜만으로 판단할 수
  없는 경우 원문 표현을 확인합니다. 명시적 종료 표현은 상시 접수 표현보다 우선합니다.
- 검색·색인 흐름은 [아키텍처](../../docs/architecture.md)에, 20,000건 상한·자동 벡터 삭제 미연결 등
  현재 제약은 [구현 현황](../../docs/implementation-status.md)에 정리합니다.

AI 경계의 실패는 `application/problem+json`으로 변환합니다. 내부 URL·라이브러리 예외는 공개하지 않습니다.

| AI 호출에서 관측한 상황 | 공개 HTTP | `code` |
|---|---:|---|
| 내부 503 또는 연결 불가 | 503 | `AI_SERVICE_UNAVAILABLE` |
| 조건 해석·점수화·색인 API의 내부 408·504 또는 연결·읽기 시간 초과 | 504 | `AI_SERVICE_TIMEOUT` |
| 예상하지 않은 HTTP 상태 | 502 | `AI_SERVICE_UPSTREAM_ERROR` |
| 잘못된 JSON·빈 body·응답 계약 위반 | 502 | `AI_SERVICE_INVALID_RESPONSE` |
| 공식 원문 제공처 수집·HTML 검증 실패 | 503 | `SUPPORT_PROGRAM_EVIDENCE_UNAVAILABLE` |
| 현재 공고 제공처가 원문 근거 질문을 지원하지 않음 | 422 | `SUPPORT_PROGRAM_EVIDENCE_NOT_SUPPORTED` |

AI Service는 시간 초과 외 LLM 실행 실패와 색인 미준비·Qdrant 실패를 내부 503으로 반환하므로 일반적으로 공개 503이
됩니다. 조건 해석·점수화의 모델·HTTP·Agent 시간 초과는 내부 504 → 공개 `504 AI_SERVICE_TIMEOUT`으로
구분합니다. Health API의 내부 408·504는 점수화 API와 달리 `UPSTREAM_ERROR`로 분류합니다.
기업마당 수집 오류는 검색 요청에서 발생하는 오류가 아니라 백그라운드 작업의 실패로 기록됩니다.

## 검증

`backend/core-api` 디렉터리에서 JDK 21 환경으로 실행합니다. Repository 통합 테스트가 실제
`mysql:8.4` Testcontainers를 실행하므로 Docker가 필요합니다.

```bash
./gradlew clean test --no-daemon
```

테스트는 Controller 계약·Client/Facade 응답 검증·상태 계산·동기화 순서·공식 원문 HTML 검증·근거 청크/인용 계약과
MySQL의 JSON, 복합 식별자, UPSERT, rollback, 시작 세대에 따른 공개 제어, 공개 스냅샷 준비 상태 전이를 검증합니다. 전체 서비스 연결 검증은
[인프라 README](../../infrastructure/README.md)의 Compose 검증 절차를 참고하세요.

C02 회귀는 공개 HTTP의 nullable 필수 키·엄격한 타입·문자/날짜 경계, 내부 AI JSON의 누락 필드·오류 변환,
현재 발화 인용·미변경 조건 보존·직전 초안 병합·CLEAR 기본값·변경 목록 순서·기존 검색과의 공유 요청 제한을
검증합니다. `SupportProgramConversationServiceTest`, `SupportProgramConversationControllerTest`,
`AiSupportProgramConversationClientTest`는 외부 모델과 DB를 사용하지 않습니다.

2026-09-07 C02 검증은 Temurin JDK 21.0.12와 실제 MySQL 8.4.11(Testcontainers `mysql:8.4`)에서 전체
`clean test`를 실행해 47개 스위트·508개 테스트가 통과했습니다(실패·오류·건너뜀 0). 신규 C02 75개와
기존 433개를 모두 포함하며, 테스트 필터나 대체 DB·유료 모델 호출은 사용하지 않았습니다.

같은 날 랭킹 전용 timeout을 분리한 뒤에도 JDK 21.0.12·MySQL 8.4.11에서 필터 없이 전체
`./gradlew clean test --no-daemon`을 다시 실행해 47개 스위트·512개 테스트가 통과했습니다
(실패·오류·건너뜀 0). 설정 바인딩·전용 bean 주입·실제 HTTP 읽기 제한·공개 timeout 오류 매핑과
기존 MySQL 통합 43개를 포함하며, 개발 서비스 변경이나 유료 모델 호출 없이 검증했습니다.

`SupportProgramEvidenceIntegrationTest`는 실제 Core HTTP·MySQL과 고정한 공식 HTML 2건으로 RAG 경계를
통합 검증합니다. 기본 실행에서 원문/AI 외부 HTTP는 스텁이며 API 키를 사용하지 않습니다.
명시적으로 별도 로컬 AI 주소와 새 캡처 경로를 지정한 경우에만 실제 AI 경로를 호출할 수 있습니다.
실행 조건·범위·기록은 [RAG 평가 안내](../../evaluation/support-program-evidence/README.md)를 참고하세요.
