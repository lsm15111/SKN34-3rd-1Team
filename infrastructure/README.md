# GovBiz Docker Compose

Docker Compose는 React 개발 서버, Core API, AI Service, 원본 카탈로그용 MySQL과 의미 검색용
Qdrant를 함께 실행하는 로컬 개발 구성입니다. 회원 세션은 동작하지만 개발용 시드 로그인이 켜져 있고 쿠키 `Secure`가
꺼져 있으므로 운영 배포·TLS·운영 인증 구성으로 쓰지 않습니다.
전체 기술 선택과 데이터 흐름은 [프로젝트 기술 문서](../docs/technology.md)를 참고하세요.

기업 맞춤 리포트의 SMTP·발신자·정기 발송 설정은 [일일 리포트 안내](../docs/daily-reports.md#발송-환경-설정)를 참고하세요.
Compose는 관련 환경변수를 Core API에 전달하지만, 메일·자동 발송은 기본 비활성화이며 실제 SMTP 전달은 별도 확인해야 합니다.

```text
Browser (127.0.0.1:5173)
  → Vite web container
      → /api proxy
          → core-api:8080
              ├→ mysql:3306 (사용자 검색 카탈로그)
              ├→ https://apis.data.go.kr (백그라운드 동기화)
              └→ ai-service:8000
                    ├→ qdrant:6333 (현재 공고의 벡터 색인)
                    └→ https://api.openai.com (텍스트 임베딩·후보 점수화)
```

## 주소 규칙

| 호출 주체 | 사용하는 주소 | 이유 |
|---|---|---|
| 브라우저의 React | `/api/...` | Vite 프록시가 같은 Origin 요청을 Core API로 중계 |
| web 컨테이너 | `http://core-api:8080` | Compose 내부 DNS |
| Core API 컨테이너 | `http://ai-service:8000` | Compose 내부 DNS |
| Core API 컨테이너 | `jdbc:mysql://mysql:3306/govbiz` | 사용자 검색용 지원사업 카탈로그 MySQL |
| Core API 컨테이너 | `https://apis.data.go.kr` | 백그라운드 동기화 전용 실제 기업마당 공고 upstream |
| AI Service 컨테이너 | `https://api.openai.com/v1` | 공고·질의 임베딩 및 후보 점수화 |
| AI Service 컨테이너 | `http://qdrant:6333` | 공고 임베딩 저장·의미 검색 |
| Host 터미널 | `http://127.0.0.1:8080` | Host에 공개된 Core API 포트 |
| Host의 DB 도구 | `127.0.0.1:3306` | loopback으로만 공개한 MySQL 포트 |
| Host 터미널 | `http://127.0.0.1:6333` | loopback으로만 공개한 개발용 Qdrant API |

`core-api`, `ai-service`, `mysql`, `qdrant`는 컨테이너 네트워크 안에서만 해석되는 이름입니다. 브라우저
JavaScript가 `http://core-api:8080`을 직접 호출하면 실패합니다.

## 실행

Docker Engine과 Compose v2가 필요합니다. 저장소 루트에서 `.env.example`을 `.env`로 복사하고,
공공데이터포털에서 발급한 일반 인증키와 필수 OpenAI 키를 넣습니다.
Encoding 또는 Decoding 키를 사용할 수 있으며 Core API가 호출 전에 정규화합니다. `.env`는 Git에서
제외되며 각 키는 필요한 컨테이너에만 전달됩니다.

```dotenv
DATA_GO_KR_SERVICE_KEY=발급받은_인증키
OPENAI_API_KEY=발급받은_OpenAI_API_키
```

| 환경변수 | 기본값 | 용도 |
|---|---|---|
| `BIZINFO_API_BASE_URL` | `https://apis.data.go.kr` | 백그라운드 동기화가 사용하는 공고 API origin. 로컬 스텁 검증 외에는 변경하지 않음 |
| `BIZINFO_API_CONNECT_TIMEOUT` | `2s` | 동기화 외부 API 연결 제한시간 |
| `BIZINFO_API_READ_TIMEOUT` | `10s` | 동기화 외부 API 응답 제한시간 |
| `BIZINFO_SYNC_ENABLED` | `true` | `false`이면 기업마당 공고 자동 동기화를 실행하지 않음 |
| `BIZINFO_SYNC_INITIAL_DELAY` | `PT0S` | 앱 시작 시 스케줄러의 첫 동기화까지의 ISO-8601 기간. 기본값은 즉시 실행 |
| `BIZINFO_SYNC_FIXED_DELAY` | `PT6H` | 이전 동기화가 끝난 뒤 다음 동기화까지의 ISO-8601 기간 |
| `KSTARTUP_API_KEY` | 빈 값 | K-Startup 활용 승인을 받은 공공데이터포털 서비스키. Core에만 주입 |
| `KSTARTUP_API_BASE_URL` | `https://apis.data.go.kr` | K-Startup 수집 API origin |
| `KSTARTUP_API_CONNECT_TIMEOUT` / `KSTARTUP_API_READ_TIMEOUT` | `2s` / `10s` | 연결 / 응답 제한시간 |
| `KSTARTUP_SYNC_ENABLED` | `false` | 초기 임베딩 비용 확인 뒤 켜는 별도 수집기 |
| `KSTARTUP_SYNC_SCOPE` | `RECENT_YEAR` | API에 1년 전 날짜 조건 전달. `RECENT_THREE_MONTHS`는 3개월 전, `OPEN`은 모집 중 공고만. 실응답에는 장기 공고도 포함될 수 있음 |
| `KSTARTUP_SYNC_INITIAL_DELAY` / `KSTARTUP_SYNC_FIXED_DELAY` | `PT0S` / `PT6H` | 첫 수집 지연 / 완료 후 다음 실행까지 지연 |
| `MSIT_API_KEY` / `CNTRADE_NOTICE_API_KEY` | `DATA_GO_KR_SERVICE_KEY` 재사용 | 각 API 활용 승인이 필요하며 전용 키로 덮어쓸 수 있음 |
| `MSIT_API_BASE_URL` / `CNTRADE_NOTICE_API_BASE_URL` | `https://apis.data.go.kr` | 제공처별 API origin |
| `MSIT_API_CONNECT_TIMEOUT` / `CNTRADE_NOTICE_API_CONNECT_TIMEOUT` | `2s` | 연결 제한시간 |
| `MSIT_API_READ_TIMEOUT` / `CNTRADE_NOTICE_API_READ_TIMEOUT` | `20s` | 응답 제한시간 |
| `MSIT_SYNC_ENABLED` / `CNTRADE_NOTICE_SYNC_ENABLED` | `false` | 최초 임베딩 비용과 실 API 응답 확인 후 각각 활성화 |
| `MSIT_SYNC_INITIAL_DELAY` / `CNTRADE_NOTICE_SYNC_INITIAL_DELAY` | `PT0S` | 첫 수집 지연 |
| `MSIT_SYNC_FIXED_DELAY` / `CNTRADE_NOTICE_SYNC_FIXED_DELAY` | `PT6H` | 해당 수집 완료 후 다음 실행까지 지연 |
| `CORE_API_HOST_PORT` / `WEB_HOST_PORT` | `8080` / `5173` | loopback 공개 포트. 격리 검증에서는 `18080` / `15173` 사용 |
| `ACCOUNT_SESSION_TTL` | `P30D` | "로그인 상태 유지"를 켠 세션의 절대 만료 기간 |
| `ACCOUNT_SESSION_SHORT_TTL` | `PT12H` | "로그인 상태 유지"를 끈 세션의 절대 만료 기간 |
| `ACCOUNT_SESSION_IDLE_TTL` | `P7D` | 마지막 사용 뒤 세션을 끝내는 유휴 기간 |
| `ACCOUNT_JWT_SECRET` | 로컬 개발용 문자열 | 세션 JWT 서명 비밀키(32자 이상). Core API 코드에는 기본값이 없으며 운영 환경에서는 반드시 교체 |
| `ACCOUNT_COOKIE_SECURE` | `false` | 세션 쿠키 `Secure` 속성. Compose는 http라 끄고, HTTPS 운영에서는 `true` |
| `ACCOUNT_DEV_LOGIN_ENABLED` | `true` | Compose 개발 환경에서는 `POST /api/v1/auth/dev-login`으로 관리자(`admin@govbiz.local`)·회원(`member@govbiz.local`)·기업 회원(`company@govbiz.local`) 시드 세션을 바로 발급. 운영에서는 `false` |
| `ACCOUNT_DEV_LOGIN_EMAIL` | `admin@govbiz.local` | 개발용 관리자 시드 계정 이메일 |
| `ACCOUNT_DEV_LOGIN_MEMBER_EMAIL` | `member@govbiz.local` | 개발용 회원 시드 계정 이메일 |
| `ACCOUNT_DEV_LOGIN_COMPANY_EMAIL` | `company@govbiz.local` | 개발용 기업 회원 시드 계정 이메일. 예시 기업(그루브데이터)을 함께 등록 |
| `ACCOUNT_DEV_LOGIN_PASSWORD` | `govbiz-admin1` | 시드 계정을 만들 때 저장하는 비밀번호. 로그인 폼으로도 쓸 수 있으므로 공유 환경에서는 교체 |
| `BIZNO_API_KEY` | 빈 값 | 기업 등록 시 사업자등록번호를 확인하는 Bizno API 키. 비어 있으면 프로필의 기업 조회·등록이 503 |
| `BIZNO_URL` | `https://bizno.net/api/fapi` | Bizno 조회 endpoint |
| `OPENAI_API_KEY` | 없음(필수) | AI Service만 사용하는 OpenAI 인증키 |
| `OPENAI_MODEL` | `gpt-5.6-luna` | 대화·원문 답변의 모델, 랭킹 전용 모델 미설정 시 상속 |
| `OPENAI_RANKING_MODEL` | 미설정 | 랭킹 전용 모델. `.env.example`은 정확도 우선 `gpt-5.6-sol` 설정 |
| `OPENAI_RANKING_REASONING_EFFORT` | `none` | 랭킹 추론 수준(`none` 또는 `low`). `.env.example`은 `low`; 비용·지연 증가 가능 |
| `LLM_MODEL_TIMEOUT_SECONDS` | `25.0` | 조건 해석·원문 근거 답변의 OpenAI 호출 제한시간(초) |
| `LLM_RUN_TIMEOUT_SECONDS` | `30.0` | 조건 해석·원문 근거 답변의 Agent 실행 제한시간(초) |
| `LLM_RANKING_MODEL_TIMEOUT_SECONDS` | `45.0` | 후보 점수화 전용 OpenAI 호출 제한시간(초) |
| `LLM_RANKING_RUN_TIMEOUT_SECONDS` | `50.0` | 후보 점수화 전용 Agent 실행 제한시간(초) |
| `AI_SERVICE_READ_TIMEOUT` | `35s` | Core API의 AI Health·조건 해석·원문 근거 답변 읽기 제한시간 |
| `AI_RANKING_READ_TIMEOUT` | `55s` | Core API의 후보 점수화 전용 읽기 제한시간 |
| `SUPPORT_PROGRAM_REQUEST_PER_CLIENT_PER_MINUTE` | `6` | 검색·원문 질문이 공유하는 접속 주소별 최근 60초 허용 요청 수 |
| `SUPPORT_PROGRAM_REQUEST_GLOBAL_PER_MINUTE` | `60` | Core 프로세스 전체의 최근 60초 허용 요청 수 |
| `SUPPORT_PROGRAM_REQUEST_MAX_CONCURRENT` | `4` | 두 API의 최대 동시 처리 수. 초과 시 대기 없이 거절 |
| `AI_SEMANTIC_SEARCH_READ_TIMEOUT` | `30s` | Core API의 색인·의미 검색 요청 제한시간 |
| `OPENAI_EMBEDDING_MODEL` | `text-embedding-3-small` | 공고·질의 임베딩 모델 |
| `OPENAI_EMBEDDING_DIMENSIONS` | `1536` | 임베딩 차원 수. 모델·차원이 바뀌면 별도 컬렉션을 사용 |
| `EMBEDDING_TIMEOUT_SECONDS` | `15` | OpenAI 임베딩 호출 제한시간(초) |
| `QDRANT_TIMEOUT_SECONDS` | `5` | Qdrant 요청 제한시간(초) |
| `QDRANT_HOST_PORT` | `6333` | Host loopback에 연결할 Qdrant 포트 |
| `SUPPORT_PROGRAM_INDEX_ENABLED` | `true` | MySQL 현재 공고의 누락 벡터 정기 복구 여부. 기업마당 동기화의 사전 색인은 중지하지 않음 |
| `SUPPORT_PROGRAM_INDEX_INITIAL_DELAY` | `PT0S` | 앱 시작 시 스케줄러의 첫 벡터 복구까지의 기간 |
| `SUPPORT_PROGRAM_INDEX_FIXED_DELAY` | `PT1M` | 이전 복구 완료 뒤 다음 실행까지의 기간 |
| `APP_CORS_ALLOWED_ORIGIN` | `http://127.0.0.1:5173` | Compose에서 Core API가 허용할 브라우저 origin |
| `MYSQL_DATABASE` | `govbiz` | MySQL 초기 데이터베이스 이름 |
| `MYSQL_USER` | `govbiz` | Core API의 MySQL 사용자 |
| `MYSQL_PASSWORD` | `govbiz-local` | Core API의 MySQL 비밀번호. 공유 환경에서는 secret으로 교체 |
| `MYSQL_ROOT_PASSWORD` | `govbiz-root-local` | MySQL 초기 root 비밀번호. 공유 환경에서는 secret으로 교체 |
| `MYSQL_HOST_PORT` | `3306` | Host loopback에 연결할 MySQL 포트 |

OpenAI는 공고 임베딩과 후보 점수화의 필수 의존성입니다. 키가 없으면 Compose 설정과 AI Service
시작이 실패하고, 실행 중 AI 호출이 실패하면 Core API가 오류 종류에 따라 502·503·504로
전달합니다. 후보 점수화는 모델 `45s` → Agent 실행 `50s` → Core 전용 읽기 `55s` 순서입니다.
조건 해석·원문 근거 답변은 기존 모델 `25s` → Agent 실행 `30s` → Core 읽기 `35s`를 유지합니다.
검색 화면은 순차적인 의미 검색과 점수화의 Core 읽기 제한 `30s + 55s`에 여유를 둔 `90s` 후
요청을 취소하고 수동 재시도를 허용합니다. 시간 초과는 성공이나 빈 결과로 바꾸지 않고 명시적인 오류로
반환합니다. 이 값은 대기 상한이지 응답속도 목표가 아니며 자동 재시도는 하지 않습니다.
기존 `.env`나 서버 환경변수에 예전 제한시간을 지정했다면 기본값보다 우선하므로 직접 갱신해야 합니다.
랭킹 시간 예산은 별도 변수로 조정하며, 변경할 때는 모델 < Agent < Core 읽기 및
의미 검색 + 점수화 < 화면 상한의 관계도 함께 유지해야 합니다.

공고 색인에도 OpenAI 임베딩 비용이 발생합니다. 신규·변경된 검색용 텍스트만 임베딩하며, 동일 내용은
Qdrant에 저장된 벡터를 재사용합니다. 의미 검색·색인 API의 전체 제한시간은 최대 25초이며 Core의
전용 읽기 제한시간 `30s`보다 짧게 유지합니다. 개발용 Qdrant는 인증 없이 loopback에만 공개됩니다.
외부에 배포할 때는 네트워크 접근 제한과 인증을 별도로 구성해야 합니다.

현재 Compose의 Qdrant 이미지는 `1.17.1`로 고정되어 있습니다. 버전 변경 시에는 데이터가 있는
상태로 중지·재시작한 뒤 검색이 복구되는지도 확인합니다.

Core API는 기본 설정에서 앱 시작 시 초기 지연 `PT0S`로 기업마당 공고 동기화를 실행하고, 동기화 완료 시점부터
6시간 뒤에 다시 실행합니다. 전체 페이지 수집·검증과 새 공고의 벡터 색인이 성공한 뒤 MySQL
카탈로그를 한 transaction으로 갱신합니다. 외부 호출·색인 실패 시 이전 MySQL 카탈로그를 유지하고,
더 최신 동기화가 시작되었다면 오래된 실행 결과는 공개하지 않습니다. 사용자 검색은 MySQL을 읽고
기업마당 API를 직접 호출하지 않습니다. 로컬에서 자동 동기화를 끄려면 `.env`에 `BIZINFO_SYNC_ENABLED=false`를
설정합니다. 이 경우 기존 카탈로그는 검색할 수 있지만 새 공고는 갱신되지 않습니다.

별도 벡터 복구 스케줄러는 MySQL의 현재 공고를 기본 1분 주기로 확인하고 누락된 벡터를 채웁니다.
새 동기화 데이터는 벡터 준비 후 MySQL에 공개되므로, 통상적인 공고 변경 때문에 미완성 색인이
검색에 노출되지는 않습니다. Qdrant 데이터 유실이나 기존 DB의 색인 미완료로 검색 대상 공고의 벡터가
부족하면 자연어 검색은 503을 반환합니다. 최초 카탈로그가 아직 비어 있으면 검색 결과도 빈 목록입니다.
빈 검색어의 최신 목록과 상세 조회는 Qdrant·OpenAI 없이 MySQL에서 반환합니다.

현재 작업은 오래된 벡터를 자동 삭제하지 않습니다. 검색할 때 현재 공고 식별자·내용 해시와 일치하는
벡터만 선택하며, 삭제 없는 복구로 겹치는 동기화 실행이 서로의 벡터를 지우는 일을 방지합니다.
오래된 벡터의 안전한 정리는 후속 과제입니다. `SUPPORT_PROGRAM_INDEX_ENABLED=false`는 정기 복구만
중지하며 기업마당 동기화의 사전 색인이나 자연어 검색의 Qdrant 의존성을 없애지 않습니다.

저장소 루트에서 실행합니다.

```bash
docker compose --env-file .env --file infrastructure/compose.yaml up --build
```

| 주소 | 용도 |
|---|---|
| `http://127.0.0.1:5173` | React Vite 개발 서버 |
| `http://127.0.0.1:5173/api/v1/health` | Vite 프록시를 거친 Core API Health |
| `http://127.0.0.1:5173/api/v1/support-programs/search?query=%EC%88%98%EC%B6%9C&acceptingOnly=true` | Vite 프록시를 거친 실제 공고 검색 |
| `http://127.0.0.1:5173/api/v1/sample-items/prepare` | Vite 프록시를 거친 SampleItem 준비 API (`POST`, JSON 본문 필요) |
| `http://127.0.0.1:5173/api/v1/health/ai-service` | Core API를 거친 AI Service Health |

AI Service의 `/internal/v1/support-program-rankings/rank`와 `/internal/v1/support-program-index/*`는
Compose 네트워크 내부에서 Core API만 호출합니다. Host나 브라우저에 AI Service 포트를 공개하지 않습니다.

### 백엔드 변경 반영과 검색 405 오류

Web은 소스 디렉터리를 bind mount하여 Vite가 변경을 바로 반영하지만, Core·AI는 이미지 안의 JAR/Python 코드를
실행합니다. 소스 수정이나 `docker compose restart`만으로 백엔드 코드가 갱신되지는 않습니다.
C01처럼 공개 POST 검색과 내부 기업 조건 계약을 함께 변경했다면 **Core와 AI를 함께 재빌드**해야 합니다.
화면의 POST 검색에 `405 Method Not Allowed`가 나오고 `OPTIONS /api/v1/support-programs/search`의 `Allow`에
GET만 있다면 실행 중인 Core가 구버전인지 확인합니다. Core만 갱신하고 AI를 그대로 두는 것도 계약 불일치를 만듭니다.

기존 `govbiz` 프로젝트의 환경 파일과 설정을 유지하며 백엔드만 교체하는 예시입니다. 다른 프로젝트 이름으로
실행했다면 먼저 실제 프로젝트를 확인합니다. MySQL·Qdrant·Web을 재생성하거나 volume을 삭제하지 않습니다.
Core 시작 시 기존 설정에 따라 자동 수집·색인이 동작할 수 있고 OpenAI 임베딩 비용이 발생할 수 있습니다.

```bash
docker compose --project-name govbiz --env-file .env --file infrastructure/compose.yaml build core-api ai-service
docker compose --project-name govbiz --env-file .env --file infrastructure/compose.yaml up --detach --no-deps --no-build ai-service
# AI가 준비된 뒤 Core를 교체합니다.
docker compose --project-name govbiz --env-file .env --file infrastructure/compose.yaml up --detach --no-deps --no-build core-api
```

교체 후 Web 프록시 경유 `/api/v1/health`, `/api/v1/health/ai-service`를 확인합니다. 검색 경로에 빈 JSON `{}`를
POST하면 새 서버는 입력 오류 400을 반환해야 하며, 이 검증 요청은 실제 검색·모델 호출을 시작하지 않습니다.
AI의 내부 `/openapi.json`에서는 랭킹 요청의 `companyConditions`와 내부 검색문 최대 길이 1,000을 확인할 수 있습니다.
Health·계약 검증은 실제 모델의 검색 품질 검증과 구분합니다.

### 중지와 데이터 초기화

일반 중지는 named volume의 MySQL·Qdrant 데이터를 유지합니다.

```bash
docker compose --env-file .env --file infrastructure/compose.yaml down --remove-orphans
```

로컬 데이터를 의도적으로 초기화할 때만 다음 명령을 사용합니다. `mysql-data`, `qdrant-data`,
`web-node-modules` volume을 삭제하므로 필요한 데이터는 먼저 백업해야 합니다. 삭제한 카탈로그와
색인은 다시 수집·구축해야 하며, 실제 OpenAI를 쓰는 색인 재구축에는 비용이 발생합니다.

```bash
docker compose --env-file .env --file infrastructure/compose.yaml down --volumes --remove-orphans
```

## 통합 smoke

Docker Engine·Compose v2·Bash·curl이 필요합니다. 다음 스크립트는 별도 Compose 프로젝트
`govbiz-verify`를 사용해 이미지를 빌드하고 다음을 확인합니다. 첫 실행의 이미지·의존 패키지
다운로드에는 네트워크가 필요합니다.

Windows에서는 WSL 등 Bash 환경에서 실행합니다. 루트 `.gitattributes`가 shell script를 LF로
유지하여 `core.autocrlf=true` 체크아웃에서도 Bash의 CRLF 구문 오류를 방지합니다.

```bash
./infrastructure/scripts/verify-compose.sh
```

검증 스크립트는 `verification` profile의 `bizinfo-stub`·`kstartup-stub`·`public-notices-stub`·`openai-stub`을 사용합니다. MySQL·Qdrant는
실제 서버이고, 외부 공고·임베딩·점수화 응답만 고정된 가상 자료입니다. 기업마당 공고 27개와 K-Startup 공고 2개를
수집하고, MSIT 11개(10+1 페이지)와 CNTRADE_NOTICE 2개(1+1 페이지)도 검증합니다.
네 출처의 관련 공고가 함께 검색되는지, K-Startup 전용 3개 필터가 저장된 분류를 사용하는지,
두 새 출처의 접수 기간 미확인이 OPEN으로 오인되지 않는지 확인합니다. CN fixture는 공식 명세 기반이며
실 API 성공을 뜻하지 않습니다. 이는 서비스 연결과 후보 누락 수정의 검증이지,
실제 OpenAI 모델의 검색 품질 측정이 아닙니다.

스텁 주소와 더미 인증키를 강제하므로 개인 키를 외부로 전송하거나 실제 OpenAI 비용을 발생시키지 않습니다.
네 제공처 스텁은 디코딩된 키도 확인합니다. 제공처 동기화와 색인은 `PT2S` 주기로 실행합니다.
장애·복구 확인을 위해 같은 API를 반복 호출하므로 스크립트는 요청량을 주소별·전체 각각 1,000건,
동시 처리 4건으로 설정합니다. 이는 서비스 연결 검증이며 기본 6건·60건의 한도 도달이나 적정 처리량을
검증하는 부하 테스트는 아닙니다. 낮은 한도·혼잡·오류 화면은 Core·Frontend 회귀 테스트에서 검증합니다.
설정 범위와 프록시/NAT 공유 등 운영 제약은 [요청 제한 안내](../docs/support-program-request-limits.md)를 참고하세요.
검증용 MySQL·Qdrant는 기본 Host 포트 `13306`·`16333`을 사용하며 각각
`VERIFY_COMPOSE_MYSQL_HOST_PORT`·`VERIFY_COMPOSE_QDRANT_HOST_PORT`로 변경할 수 있습니다.

1. Vite Web 응답이 200인지 확인합니다.
2. Vite 프록시를 거친 Core API Health가 200인지 확인합니다.
3. 동기화된 공고 행이 MySQL에 존재하는지 확인한 뒤 로컬 스텁을 중지하고, 빈 검색어 GET이
   Web → Core API → MySQL 카탈로그를 거쳐 이를 반환하는지 확인합니다. 이 검색 요청은 로컬 스텁을
   직접 호출하지 않으며, 더미 OpenAI 키도 외부로 보내지 않습니다.
4. 자연어 검색이 Web → Core → MySQL → AI Service → Qdrant → 점수화를 거쳐 오래된 관련 공고를 반환하는지 확인합니다.
5. Qdrant를 중지하고 정기 복구가 `UNAVAILABLE/indexReady=false`를 기록한 뒤에도 자연어 검색은 503,
   빈 검색어 목록은 기존 공개 공고를 포함한 200인지 확인합니다. 이후 재시작 뒤 검색 복구를 확인합니다.
6. SampleItem 준비 POST가 200과 `READY_FOR_PROCESSING`을 반환하는지 확인합니다.
7. Core API를 통한 AI Service Health가 200인지 확인합니다.
8. AI Service를 중지했을 때 Core Health는 200, AI Health와 자연어 검색은 503(연결 불가) 또는 504(시간 초과)인지 확인합니다.
9. AI Service 재시작 후 Core API 재시작 없이 Health와 자연어 검색이 복구되는지 확인합니다.

Web/Core는 검증 전용 `15173`/`18080` 포트를 사용해 기존 개발 서비스를 중지하지 않고 실행할 수 있습니다.
`VERIFY_COMPOSE_WEB_HOST_PORT`/`VERIFY_COMPOSE_CORE_API_HOST_PORT`로 바꿀 수 있으며 CORS·요청 Origin도 같은 Web 주소를 사용합니다.
스크립트는 종료 시 검증용 컨테이너와 volume을 삭제합니다. 조사 목적으로 유지하려면
`VERIFY_COMPOSE_KEEP_RUNNING=true`로 실행합니다. `VERIFY_COMPOSE_PROJECT_NAME`을 변경할 경우 기존
개발·운영 프로젝트 이름을 사용하지 마세요. 실행 전 해당 이름의 컨테이너·네트워크·volume이 하나라도
존재하면 정리 작업 없이 중단합니다. 설정 검증이나 Docker 자원 확인에 실패해도 기존 자원을 정리하지
않으며, 새 검증 스택의 시작을 시도한 뒤부터만 자동 정리를 적용합니다. 같은 프로젝트 이름으로 검증을
동시에 실행하지 마세요. `KEEP_RUNNING`으로 남긴 스택이 있다면 새 이름을 지정하거나 해당 검증 자원을
직접 확인한 후 정리해야 합니다.

안전장치 회귀 테스트는 실제 Docker를 호출하지 않고 실행할 수 있습니다.

```bash
python3 -B -m unittest discover -s infrastructure/scripts -p 'test_*.py'
```

## 개발용 목데이터

파트너 모집 화면을 채우는 예시 공고·기업·모집글·제안은 [dev-seed.sql](dev-seed.sql)로 넣습니다. 스택이 떠 있는 상태에서
저장소 루트에서 실행합니다.

```bash
docker exec -i govbiz-mysql-1 sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < infrastructure/dev-seed.sql
```

몇 번을 다시 실행해도 됩니다. 시드 계정과 딸린 행을 지우고 오늘 기준 날짜로 다시 만들기 때문에 충돌이 없고, 며칠 지나
대기 제안이 만료로 바뀌면 다시 실행해 처음 모양으로 되돌립니다. 모든 시드 계정(`company@govbiz.local`, `coop@<기업>.example`)은
`ACCOUNT_DEV_LOGIN_PASSWORD`로 로그인됩니다. 자세한 구성은
[계정·인증 계약](../docs/account-auth-contract.md#개발용-목데이터)을 보세요.
