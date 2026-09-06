# 회원가입·로그인 설계와 단계별 작업 프롬프트

기준일: 2026-09-06. 기준 브랜치: `origin/main` (`2a5b205`). 참고 브랜치: `feature/auth-bizno`
(Bizno 조회 Client·인증 화면 시안·비로그인 3회 제한, main 대비 3 commit, 테스트·문서 없음).

이 문서는 두 부분입니다. 앞은 **설계**(무엇을 어떤 구조로 만들지), 뒤는 **단계별 프롬프트**(각 단계를
독립 브랜치·PR로 진행할 때 그대로 붙여 쓰는 지시문)입니다.

---

## 1. 목표와 범위

- 사업자등록번호 하나로 Bizno에서 기업을 확인하고, **이메일 + 비밀번호 + 사업자등록번호**만으로 가입한다.
  담당자 이름·소재지·업종·설립연도·기업 단계는 받지 않는다(가입 후 프로필 기능에서 다룬다).
- 이메일 + 비밀번호로 로그인하고, 새로고침 후에도 로그인 상태가 유지되며, 로그아웃할 수 있다.
- 로그인하지 않아도 검색은 그대로 된다. 비로그인 무료 검색 3회 제한은 마지막 단계에서 별도 기능으로 얹는다.
- 이메일 인증 메일, 비밀번호 재설정, 소셜 로그인, 관리자 권한, 로그인 시도 제한은 이번 범위 밖이다.

화면 참고: 캔버스 `GovBiz 계정 화면`의 **로그인**, **회원가입** 아트보드
(https://claude.ai/code/artifact/a14c6ab2-e4e5-4512-b430-1bea91262570). 회원가입 아트보드의 3단계
구성(계정 정보 → 기업 정보 → 이메일 인증)은 채택하지 않고, 왼쪽 소개 패널 + 오른쪽 단일 폼으로 줄인다.
`feature/auth-bizno`의 `SignupPage.tsx`·`LoginPage.tsx`·`AuthPage.styles.ts`가 이미 이 형태이므로 그대로 이어간다.

## 2. 기존 브랜치 평가 (`feature/auth-bizno`)

| 항목 | 상태 | 이번 계획에서 |
|---|---|---|
| `account/client/bizno/*` (Client·Config·Properties·DTO·Exception) | 구조는 규칙에 맞음. 테스트 없음, `timeout()`은 만들어 두고 쓰지 않음, 조회 실패가 ProblemDetail로 안 바뀜 | 1단계에서 가져오고 테스트·예외 매핑 추가 |
| `BiznoBusinessController` `GET /api/v1/auth/businesses/lookup` | 응답이 배열 그대로(`List<BiznoBusiness>`), Client DTO를 공개 계약으로 노출 | `controller/dto` Response로 감싸고 객체 응답으로 변경 |
| `BiznoBusinessService` | `require`로 10자리 검증 → 500으로 떨어짐 | Bean Validation `@Pattern`으로 400 처리 |
| `SignupPage`·`LoginPage`·`AuthPage.styles` | 화면 완성도 높음. 제출은 안내 문구만 표시. API 호출이 View 안에 직접 있음 | 4단계에서 ViewModel·UseCase·Repository로 분리 |
| `authApi.ts` (`lookupBiznoBusiness`) | Zod 검증 있음. DTO 파일 분리 안 됨 | 4단계에서 `data/models`·`data/api` 규칙대로 재배치 |
| `usageSlice` + `AuthGateModal` + 채팅 헤더 변경 | 동작함. 새로고침 시 초기화, 로그인 여부 무관 | 5단계에서 로그인 시 해제, localStorage 보존 추가 |
| `application.properties`·`compose.yaml` Bizno 항목 | 그대로 사용 가능 | 1단계에서 가져오고 `.env.example`·infra README 추가 |

브랜치는 삭제하지 않고 참고용으로 둔다. 새 작업은 모두 `origin/main`에서 새 브랜치를 판다.

## 3. Core API 설계

### 3.1 기능 디렉터리

```
ai.govbiz.core.account
├── controller/            AccountAuthController, BiznoBusinessController
│   └── dto/               *Request, *Response
├── service/               BiznoBusinessService, AccountSignupService, AccountLoginService, AccountSessionService
│   ├── dto/               AccountSessionResult, AuthenticatedAccountResult
│   └── exception/         EmailAlreadyRegisteredException, InvalidCredentialsException,
│                          AuthenticationRequiredException, BusinessNotFoundException
├── domain/                Account, Company, AccountSession, PasswordHash 규칙
├── repository/            AccountRepository
│   └── mapper/            AccountMapper, AccountDbRow, CompanyDbRow, AccountSessionDbRow
└── client/bizno/          BiznoClient, config/, dto/BiznoBusiness, exception/BiznoClientException
```

Facade는 두지 않는다. Bizno 호출은 단일 Client 호출이므로 Service가 Client를 직접 쓴다(AGENTS 규칙).
SQL은 `src/main/resources/mybatis/account/repository/AccountMapper.xml`에 두고
`mybatis.mapper-locations`에 경로를 추가한다.

### 3.2 스키마 (Flyway `V5__create_account.sql`)

```sql
CREATE TABLE company (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    business_number CHAR(10) NOT NULL,          -- 숫자 10자리, 하이픈 제거
    company_name VARCHAR(255) NOT NULL,
    business_status VARCHAR(64) NOT NULL,       -- Bizno bstt 원문(계속사업자/휴업자/폐업자 등), 없으면 ''
    verified_source VARCHAR(32) NOT NULL,       -- 'BIZNO'
    verified_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_company_business_number UNIQUE (business_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE account (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email VARCHAR(320) NOT NULL,                -- 소문자 정규화 후 저장
    password_hash VARCHAR(100) NOT NULL,
    company_id BIGINT UNSIGNED NOT NULL,
    terms_agreed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_account_email UNIQUE (email),
    CONSTRAINT fk_account_company FOREIGN KEY (company_id) REFERENCES company (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE account_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    token_hash CHAR(64) NOT NULL,               -- SHA-256(hex) of the opaque token
    account_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_account_session_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_account_session_account FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE,
    INDEX idx_account_session_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

- 한 기업에 여러 계정을 허용한다(담당자가 여럿일 수 있음). 기업 정보는 가입 시점 Bizno 응답으로 UPSERT한다.
- `company.business_status`는 저장·표시만 하고 가입을 막지 않는다(폐업자 판정은 이번 범위 밖).
- V5 번호는 다른 브랜치(예: `applicationMethod` 추가)와 충돌할 수 있으니 PR 직전에 `origin/main`의
  migration 목록을 다시 확인한다.

### 3.3 인증 방식

- **비밀번호**: BCrypt(cost 10). `org.springframework.security:spring-security-crypto` 한 개만 추가한다
  (Security filter chain·자동 설정 없음). **새 의존성이므로 3단계 시작 전에 사용자 승인**을 받는다.
  승인하지 않으면 JDK 내장 `PBKDF2WithHmacSHA256`(210,000회, 16바이트 salt)으로 대체한다.
- **세션**: 로그인·가입 성공 시 `SecureRandom` 32바이트를 base64url로 만든 불투명 토큰을 발급한다.
  DB에는 SHA-256 해시만 저장하고 만료는 30일(`app.account.session-ttl=${ACCOUNT_SESSION_TTL:P30D}`).
  브라우저는 `Authorization: Bearer <token>` 헤더로 보낸다. 쿠키·CSRF·CORS credentials 변경이 없고
  MyBatis·Flyway 규칙과 맞아서 이 방식을 택했다. 만료 세션 정리는 로그인 시 해당 계정의 만료 행 삭제로 대신한다.
- **인증 확인**: `AccountSessionService.requireAccount(authorizationHeader: String?)`가 헤더 파싱 →
  해시 조회 → 만료 확인 → `AuthenticatedAccountResult`를 돌려준다. 보호 endpoint가 둘(`/me`, `/logout`)뿐이라
  Interceptor·ArgumentResolver는 두지 않는다. 보호 endpoint가 셋 이상 되면 그때 ArgumentResolver로 옮긴다.

### 3.4 공개 HTTP 계약 (`docs/account-auth-contract.md`로 정리)

| Method·Path | 요청 | 성공 | 실패 코드 |
|---|---|---|---|
| `GET /api/v1/auth/businesses/lookup?businessNumber=` | 숫자 10자리(하이픈 허용, 서버에서 제거) | 200 `{ "businesses": [ { "businessNumber", "companyName", "businessStatus" } ] }` (없으면 빈 배열) | 400 `REQUEST_VALIDATION_FAILED`, 503 `BIZNO_NOT_CONFIGURED`/`BIZNO_UNAVAILABLE`, 502 `BIZNO_UPSTREAM_ERROR`/`BIZNO_INVALID_RESPONSE`, 504 `BIZNO_TIMEOUT` |
| `POST /api/v1/auth/signup` | `{ "email", "password", "businessNumber" }` | 201 `AuthSessionResponse` | 400 검증, 409 `EMAIL_ALREADY_REGISTERED`, 422 `BUSINESS_NOT_FOUND`, Bizno 5xx 동일 |
| `POST /api/v1/auth/login` | `{ "email", "password" }` | 200 `AuthSessionResponse` | 401 `INVALID_CREDENTIALS`(이메일·비밀번호 구분 없음) |
| `POST /api/v1/auth/logout` | Bearer | 204 | 401 `AUTHENTICATION_REQUIRED` |
| `GET /api/v1/auth/me` | Bearer | 200 `{ "account": AccountResponse }` | 401 `AUTHENTICATION_REQUIRED` |

```json
// AuthSessionResponse
{
  "sessionToken": "…",
  "expiresAt": "2026-10-06T09:00:00+09:00",
  "account": {
    "email": "manager@company.co.kr",
    "company": { "businessNumber": "1234567890", "companyName": "예시 소프트웨어 주식회사", "businessStatus": "계속사업자" }
  }
}
```

검증 규칙: 이메일은 `@Email` + 최대 320자, 소문자 정규화. 비밀번호는 8~72자(BCrypt 한계), 영문·숫자 각 1자 이상.
사업자등록번호는 `[0-9-]{10,12}` → 숫자만 남겨 10자리. 오류 응답은 기존 `ApiExceptionHandler`의
ProblemDetail(`code` 속성) 형식을 그대로 따른다.

### 3.5 호출 흐름

```
POST /api/v1/auth/signup
  → AccountAuthController
    → AccountSignupService
        1. 이메일 정규화·중복 조회 (AccountRepository)
        2. BiznoClient.findByBusinessNumber (DB transaction 밖)  → 없으면 BusinessNotFoundException
        3. 비밀번호 해시
        4. @Transactional: company UPSERT → account INSERT → session INSERT (AccountRepository 공개 메서드 하나)
    → AuthSessionResponse
```

Bizno 호출은 transaction 안에서 하지 않는다(AGENTS). 이메일 중복은 1번의 선조회와 DB UNIQUE 제약을
둘 다 사용하고, INSERT 시 `DuplicateKeyException`도 409로 변환한다.

## 4. Frontend 설계

```
domain/entities/Account.ts                 Account { email, company: Company }, Company { businessNumber, companyName, businessStatus }
domain/entities/AuthSession.ts             AuthSession { sessionToken, expiresAt, account }
domain/repositories/AccountRepository.ts   lookupBusiness, signUp, logIn, logOut, getCurrentAccount  (+ AccountSignUp/AccountLogIn 명령 타입, 결과 union)
domain/usecases/                           LookupBusinessUseCase, SignUpUseCase, LogInUseCase, LogOutUseCase, GetCurrentAccountUseCase
data/models/BiznoBusinessDto.ts, AccountDto.ts, AuthSessionDto.ts   Zod 스키마 + toDomain
data/api/accountApi.ts                     fetch 경계. AccountApiError { status, code } (ProblemDetail의 code 파싱)
data/repositories/AccountRepositoryImpl.ts DTO→Domain, 409/422/401을 결과 union으로 변환
data/storage/sessionTokenStorage.ts        localStorage 'govbiz.sessionToken' 읽기·쓰기·삭제 (try/catch)
app/di/*                                   accountRepository, 5개 UseCase, sessionTokenStorage 등록
presentation/features/auth/
  state/authSlice.ts                       { status: 'unknown'|'anonymous'|'authenticated', account }
  validation/signupFormSchema.ts, loginFormSchema.ts   Zod (react-hook-form + zodResolver, sample-item 예제 방식)
  viewmodel/useSignupViewModel.ts, useLoginViewModel.ts, useAuthSessionViewModel.ts
  view/SignupPage.tsx, LoginPage.tsx, AuthPage.styles.ts
```

- 세션 복원: `App` 진입 시 `useAuthSessionViewModel`이 저장된 토큰으로 `GET /me`를 한 번 호출해
  `authSlice`를 채운다. 401이면 토큰을 지우고 `anonymous`.
- 가입·로그인 성공 → 토큰 저장 → `authSlice.signedIn` → `/`로 이동. 로그아웃 → API 호출 → 토큰 삭제 → `signedOut`.
- 채팅 헤더: 비로그인이면 `로그인` 링크, 로그인이면 회사명 + `로그아웃` 버튼.
- 오류 문구: 로그인 실패는 "이메일 또는 비밀번호를 확인해 주세요." 하나로 통일. 가입 409는 "이미 가입된 이메일입니다.",
  422는 "사업자등록번호로 기업을 찾지 못했습니다.", Bizno 5xx는 "기업 정보를 지금 확인할 수 없습니다. 잠시 후 다시 시도해 주세요."
- 토큰을 localStorage에 두는 선택은 XSS에 취약하다. 현재 앱은 외부 스크립트가 없고 CSP·쿠키 인증은 다음 과제로 남긴다(문서에 명시).

## 5. 단계와 브랜치

| 단계 | 브랜치 (origin/main 기준) | 내용 | 완료 기준 |
|---|---|---|---|
| 0 | (브랜치 없음) | 기준 확인·환경 점검·Bizno 실제 응답 확정 | 체크리스트 통과 |
| 1 | `feature/account-bizno-lookup` | Bizno Client + 조회 API + 예외 매핑 + 테스트 + 문서 | PR #A |
| 2 | `feature/account-schema` | V5 migration + Domain + Repository + MySQL 통합 테스트 | PR #B (A 병합 후) |
| 3 | `feature/account-auth-api` | 가입·로그인·로그아웃·me API + 세션 + 계약 문서 | PR #C (B 병합 후) |
| 4 | `feature/account-auth-web` | Frontend 전 계층 + 화면 연결 + 세션 복원 + 헤더 | PR #D (C 병합 후) |
| 5 | `feature/anonymous-search-limit` | 비로그인 3회 제한 + 가입 유도 모달 | PR #E (D 병합 후) |

각 PR은 main으로 보낸다. 다음 단계는 이전 PR이 병합된 main에서 다시 브랜치를 판다(병합 전이면 이전
브랜치 위에서 시작하고 PR base를 이전 브랜치로 잡는다).

공통 규칙(모든 단계):
- 커밋 메시지는 최근 커밋처럼 한국어 한 줄("Bizno 기업 정보 조회 API 연결"), 끝에 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- Core API 변경 후 `cd backend/core-api && ./gradlew clean test --no-daemon` (JDK 21). Repository·XML·migration을
  건드린 단계는 Testcontainers 통합 테스트가 실제로 돌아야 하므로 **Docker Desktop이 켜져 있어야 한다**
  (2026-09-06 점검 시 `docker version`이 응답하지 않았다).
- Frontend 변경 후 `cd frontend && pnpm lint && pnpm test && pnpm build`.
- 구조·이름을 바꾸면 `backend/core-api/README.md`, `docs/architecture.md`, `docs/implementation-status.md`를 같이 고친다.
- 요청 범위 밖 리팩터링·패턴 통일은 하지 않는다. 새 의존성은 추가 전에 사용자에게 알린다.

---

## 6. 단계별 프롬프트

각 프롬프트는 그 단계만 알고 있어도 실행할 수 있게 썼다. 실행 순서대로 붙여 넣는다.

### 프롬프트 0 — 기준 확인과 환경 점검

```
GovBiz 저장소(C:\Workspace\SKN\SKN34-3rd-1Team)에서 회원가입·로그인 기능 구현을 시작하기 전 점검을 한다.
설계는 docs/account-auth-plan.md §1~§5를 따른다. 코드는 아직 바꾸지 않는다.

1. git fetch --all --prune 후 origin/main의 최신 커밋과 backend/core-api/src/main/resources/db/migration 목록을 확인해
   다음 Flyway 번호가 V5인지 적는다.
2. feature/auth-bizno 브랜치가 origin/main보다 앞선 커밋 3개(98c2c5c, cf3193d, f8ffaa9)를 가진 것을 확인하고,
   1단계에서 가져올 파일 목록을 적는다: account/client/bizno/*, BiznoBusinessController, BiznoBusinessService,
   application.properties의 app.bizno.*, compose.yaml의 BIZNO_* 4줄.
3. 환경: java -version이 21인지, docker version이 응답하는지(Testcontainers), pnpm -v, node -v를 확인한다.
   Docker가 꺼져 있으면 2단계 전에 켜야 한다고 보고한다.
4. .env에 BIZNO_API_KEY와 BIZNO_URL이 있는지 키 이름만 확인한다(값은 출력하지 않는다).
5. Bizno 실제 응답 형식 확정: .env의 키로 curl "$BIZNO_URL?key=$BIZNO_API_KEY&gb=1&q=<테스트용 사업자번호>&type=json"을
   한 번 호출해 resultCode, resultMsg, items[]의 필드명(company, bno, bstt 등)을 적는다. 키 값은 출력에 남기지 않는다.
   BiznoClient가 가정한 필드(resultCode==0, items[].company/bno/bstt)와 다르면 1단계 프롬프트에 반영할 차이를 적는다.
6. 결과를 docs/account-auth-plan.md 맨 아래 "0단계 점검 결과" 절로 추가한다(날짜, 다음 Flyway 번호, 도구 버전,
   Bizno 응답 필드 목록). 커밋하지 않는다.
```

### 프롬프트 1 — Bizno 기업 조회 API (`feature/account-bizno-lookup`)

```
GovBiz Core API에 사업자등록번호로 Bizno 기업 정보를 조회하는 공개 API를 기능 단위 브랜치로 만든다.
AGENTS.md의 Core API 규칙과 docs/account-auth-plan.md §3.1·§3.4(lookup 행)·§5 공통 규칙을 따른다.

브랜치: git switch -c feature/account-bizno-lookup origin/main

가져올 코드(feature/auth-bizno에서 git checkout feature/auth-bizno -- <경로>로 가져온 뒤 수정):
- backend/core-api/src/main/kotlin/ai/govbiz/core/account/client/bizno/** (BiznoClient, config/BiznoClientConfig,
  config/BiznoClientProperties, dto/BiznoBusiness, exception/BiznoClientException)
- account/controller/BiznoBusinessController.kt, account/service/BiznoBusinessService.kt
- application.properties의 app.bizno.* 5줄, infrastructure/compose.yaml core-api 환경변수 BIZNO_* 4줄

수정 사항:
1. BiznoClient: 0단계에서 확정한 실제 응답 필드에 맞춘다. 읽기 timeout은 다른 Client처럼 SocketTimeoutException/
   HttpTimeoutException을 BiznoClientException.timeout()으로 구분한다(현재 unavailable로 뭉개짐).
   API 키가 로그·예외 메시지·URL 문자열에 남지 않게 한다.
2. BiznoBusinessService: require 대신 숫자만 남긴 뒤 길이 10 검증은 Controller의 Bean Validation
   (@Pattern("[0-9-]{10,12}"))으로 옮기고, Service는 정규화(숫자만)와 Client 호출만 한다.
3. Controller 응답을 controller/dto/BiznoBusinessLookupResponse(businesses: List<BiznoBusinessResponse>)로 감싼다.
   Client DTO를 공개 계약으로 노출하지 않는다.
4. _common/exception/ApiExceptionHandler에 BiznoClientException 처리 추가: NOT_CONFIGURED·UNAVAILABLE → 503,
   UPSTREAM_ERROR·INVALID_RESPONSE → 502, TIMEOUT → 504. type은 urn:govbiz:problem:bizno-*, code는 BIZNO_NOT_CONFIGURED 등.
   기존 AiServiceFailure 처리 방식과 같은 모양으로 쓴다.

테스트(production 패키지 구조를 따른다):
- account/client/bizno/BiznoClientTest: MockRestServiceServer로 성공(2건), resultCode≠0, items 누락, HTTP 500, 연결 실패,
  timeout, 빈 키(notConfigured, 요청 없음). BizInfoClientTest 스타일.
- account/client/bizno/config/BiznoClientPropertiesTest: endpoint 경로·timeout 검증.
- account/controller/BiznoBusinessControllerTest: MockMvc standalone + ApiExceptionHandler. 하이픈 포함 입력 정규화,
  9자리 400 REQUEST_VALIDATION_FAILED, 빈 결과 200 {"businesses":[]}, 503/502/504 ProblemDetail code 확인.

문서·설정:
- .env.example에 BIZNO_API_KEY=, BIZNO_URL=https://bizno.net/api/fapi 항목과 한 줄 설명 추가.
- infrastructure/README.md 환경변수 표에 BIZNO_* 추가.
- docs/account-auth-contract.md 신규: lookup 요청·응답·오류 표(§3.4 형식). README.md 상세 문서 표에 링크 추가.
- backend/core-api/README.md와 docs/architecture.md에 account 기능 디렉터리와 호출 흐름
  (GET lookup → BiznoBusinessController → BiznoBusinessService → BiznoClient → Bizno) 추가.
- docs/implementation-status.md 사용자 기능 표에 "사업자등록번호 기업 조회 | 구현됨" 추가.

검증: cd backend/core-api && ./gradlew clean test --no-daemon 통과, git diff --check 통과,
rg로 옛 응답 형식(List<BiznoBusiness> 반환) 잔재 없음 확인.

마무리: 한국어 한 줄 커밋(예: "사업자등록번호 Bizno 기업 조회 API 추가")에 Co-Authored-By 트레일러.
push 후 gh pr create --base main. PR 본문에 호출 흐름과 오류 코드 표, 끝에
"🤖 Generated with [Claude Code](https://claude.com/claude-code)".
하지 말 것: 회원가입 코드 선작성, 다른 기능 파일 정리, 새 의존성 추가.
```

### 프롬프트 2 — 계정 스키마와 Repository (`feature/account-schema`)

```
GovBiz Core API에 계정·기업·세션 저장소를 만든다. 공개 API는 아직 추가하지 않는다.
AGENTS.md의 "MySQL 및 MyBatis 영속성 규칙"·"스키마와 데이터 동기화 규칙"·"데이터베이스 검증"과
docs/account-auth-plan.md §3.2·§3.3을 따른다. Docker Desktop이 실행 중이어야 한다.

브랜치: feature/account-bizno-lookup이 main에 병합됐으면 git switch -c feature/account-schema origin/main,
아니면 feature/account-bizno-lookup 위에서 만들고 PR base를 그 브랜치로 둔다.

구현:
1. src/main/resources/db/migration/V5__create_account.sql: §3.2의 company, account, account_session 세 테이블.
   번호는 origin/main의 migration 목록을 다시 확인해 정한다.
2. account/domain: Account(id, email, company), Company(id, businessNumber, companyName, businessStatus),
   AccountSession(accountId, tokenHash, expiresAt). MyBatis annotation·컬럼명은 넣지 않는다.
3. account/repository/mapper: AccountMapper(@Mapper interface), AccountDbRow, CompanyDbRow, AccountSessionDbRow(var·기본값 허용).
   SQL은 src/main/resources/mybatis/account/repository/AccountMapper.xml(namespace = Mapper 전체 이름, resultMap 명시, SELECT * 금지, #{} 바인딩).
   application.properties의 mybatis.mapper-locations를 두 XML 모두 읽도록 확장한다.
4. account/repository/AccountRepository(@Repository) 공개 메서드:
   - findByEmail(email): Account? (password_hash 포함한 내부용 결과가 필요하면 repository 밖으로 DbRow를 내보내지 말고
     Domain에 AccountCredential(accountId, passwordHash) 같은 별도 값을 둔다)
   - createAccountWithCompany(email, passwordHash, termsAgreedAt, company: VerifiedCompany, session: NewSession): Account
     @Transactional 하나로 company UPSERT(business_number 기준으로 이름·상태·verified_at 갱신) → account INSERT → session INSERT.
     이메일 UNIQUE 위반은 DuplicateKeyException을 그대로 던지고 Service가 변환한다.
   - createSession(accountId, tokenHash, expiresAt), findAccountBySessionTokenHash(tokenHash, now): Account?
     (만료 전 행만), deleteSessionByTokenHash(tokenHash): Int, deleteExpiredSessions(accountId, now): Int.
5. Clock은 기존 "seoulClock" Qualifier를 주입받아 사용한다.

테스트:
- account/repository/AccountRepositoryIntegrationTest: @SpringBootTest + @Import(MySqlTestContainerConfig) +
  기존 통합 테스트와 같은 properties(AI·동기화·색인 비활성). 검증 항목: 한글 회사명·특수문자 저장/조회,
  같은 사업자번호로 두 계정 생성 시 company 행 1개·이름 갱신, 이메일 중복 시 DuplicateKeyException과 rollback
  (account·session 행이 남지 않음), 세션 만료 경계(expires_at == now 는 조회 안 됨), 삭제 후 조회 null,
  ON DELETE CASCADE 확인.
- @BeforeEach에서 account_session → account → company 순으로 DELETE.
- 기존 SupportProgramRepositoryIntegrationTest가 새 테이블 때문에 깨지지 않는지 확인.

문서: backend/core-api/README.md·docs/architecture.md "데이터" 절에 세 테이블 설명,
docs/implementation-status.md "스키마 관리" 행에 V5 추가.

검증: ./gradlew clean test --no-daemon (Testcontainers 실제 실행 확인), git diff --check.
마무리: 커밋 "계정·기업·세션 MySQL 스키마와 Repository 추가" + 트레일러, PR 생성.
하지 말 것: JPA·JdbcClient 병용, Repository interface·BaseRepository 추가, 공개 API 추가.
```

### 프롬프트 3 — 가입·로그인·로그아웃·내 정보 API (`feature/account-auth-api`)

```
GovBiz Core API에 회원가입·로그인·로그아웃·내 계정 조회 API를 만든다.
docs/account-auth-plan.md §3.3~§3.5와 AGENTS.md를 따른다. 2단계 Repository와 1단계 BiznoClient를 사용한다.

시작 전 확인(사용자 승인 필요): 비밀번호 해시에 org.springframework.security:spring-security-crypto를 추가한다.
Spring Security starter·filter chain은 넣지 않는다. 승인 전에는 build.gradle을 바꾸지 말고 먼저 알린다.
거절되면 JDK PBKDF2WithHmacSHA256(210,000회, 16바이트 salt, 형식 "pbkdf2$<iter>$<salt>$<hash>")로 구현한다.

브랜치: feature/account-schema 병합 후 git switch -c feature/account-auth-api origin/main.

구현:
1. account/domain: PasswordPolicy(8~72자, 영문·숫자 포함) 검증 함수, SessionToken 생성(SecureRandom 32바이트 base64url)과
   SHA-256 hex 해시 함수. 이 둘은 domain 또는 account/helper에 두되 이름은 역할대로(SessionTokenHelper 등).
2. account/service:
   - AccountSignupService.signUp(email, password, businessNumber): AccountSessionResult
     이메일 소문자 정규화 → findByEmail 있으면 EmailAlreadyRegisteredException →
     BiznoClient 조회(transaction 밖) 결과 중 bno 정규화값이 같은 첫 항목, 없으면 BusinessNotFoundException →
     해시 → 토큰 발급 → repository.createAccountWithCompany → 결과. DuplicateKeyException은 409 예외로 변환.
   - AccountLoginService.logIn(email, password): AccountSessionResult
     계정 없음/비밀번호 불일치 모두 InvalidCredentialsException(같은 응답, 계정 없을 때도 더미 해시 비교로 시간 차 최소화).
     성공 시 만료 세션 정리 후 새 세션 발급.
   - AccountSessionService.requireAccount(authorization: String?): AuthenticatedAccountResult
     "Bearer " 접두사·빈 토큰·미존재·만료 → AuthenticationRequiredException.
     logOut(authorization): 해시로 세션 삭제(없어도 204).
   - service/dto: AccountSessionResult(sessionToken, expiresAt: OffsetDateTime(서울), account), AuthenticatedAccountResult.
   - session TTL: app.account.session-ttl=${ACCOUNT_SESSION_TTL:P30D} (AccountSessionProperties, Duration 양수 검증).
3. account/controller/AccountAuthController(@RequestMapping("/api/v1/auth")):
   POST /signup(201), POST /login(200), POST /logout(204, @RequestHeader("Authorization") required=false),
   GET /me(200). controller/dto: SignupRequest(@Email @Size(max=320), @Size(8..72) password, @Pattern 사업자번호),
   LoginRequest, AuthSessionResponse, AccountResponse, CompanyResponse. 요청 password는 toString·로그에 남기지 않는다.
4. ApiExceptionHandler: EMAIL_ALREADY_REGISTERED 409, BUSINESS_NOT_FOUND 422, INVALID_CREDENTIALS 401,
   AUTHENTICATION_REQUIRED 401(WWW-Authenticate: Bearer 헤더 포함). DuplicateKeyException은 Service에서 변환하므로 여기서 잡지 않는다.
5. WebCorsConfig: allowedHeaders("*")로 Authorization이 이미 허용되는지 확인만 한다. 변경 없으면 그대로 둔다.

테스트:
- service 단위 테스트(Mockito): 가입 성공 흐름 순서(중복 확인 → Bizno → 저장), Bizno 결과 없음 422 예외, 중복 409,
  로그인 실패 두 경우 동일 예외, 세션 만료·형식 오류 401, TTL 계산.
- AccountAuthControllerTest(MockMvc standalone + ApiExceptionHandler): 201 응답 계약(sessionToken·expiresAt 오프셋 +09:00·account),
  400 필드 오류 목록, 409/422/401 code, /me·/logout의 Bearer 누락 401, /logout 204.
- 통합 테스트 1개(AccountAuthFlowIntegrationTest, Testcontainers, BiznoClient는 MockRestServiceServer 또는 @MockitoBean):
  signup → me → logout → me 401, 같은 이메일 재가입 409, login 성공.

문서: docs/account-auth-contract.md에 네 endpoint와 오류 표·예시 JSON, docs/architecture.md에 §3.5 흐름,
docs/implementation-status.md "회원·기업 프로필·북마크·알림 | 미구현" 행을 "회원가입·로그인 | 구현됨 …"과
"기업 프로필·북마크·알림 | 미구현"으로 나눈다. backend/core-api/README.md 실행 절에 ACCOUNT_SESSION_TTL·BIZNO_API_KEY 설명.
docs/technology.md 기술 스택 표에 spring-security-crypto(BCrypt) 한 줄. .env.example·compose.yaml에 ACCOUNT_SESSION_TTL.

검증: ./gradlew clean test --no-daemon, git diff --check, rg "password" 로 로그·toString 노출 여부 점검.
마무리: 커밋 "이메일 회원가입·로그인·세션 API 추가" + 트레일러, PR 생성.
하지 말 것: Spring Security filter chain, JWT, 쿠키 세션, 이메일 인증·비밀번호 재설정 선구현.
```

### 프롬프트 4 — Frontend 가입·로그인 화면 연결 (`feature/account-auth-web`)

```
GovBiz Frontend에 회원가입·로그인·로그아웃과 세션 복원을 클린 아키텍처 규칙(View → ViewModel Hook → UseCase →
Repository → Zod DTO, Awilix appContainer)대로 구현한다. docs/account-auth-plan.md §4와 §3.4 계약,
frontend/README.md·docs/technology.md의 Frontend 규칙을 따른다. 3단계 API가 main에 있어야 한다.

브랜치: git switch -c feature/account-auth-web origin/main.
화면은 feature/auth-bizno의 SignupPage.tsx·LoginPage.tsx·AuthPage.styles.ts를 git checkout으로 가져와 시작한다
(디자인 유지, 로직만 ViewModel로 옮긴다). 캔버스 로그인·회원가입 아트보드의 왼쪽 패널 문구를 참고하되
"이메일 인증"·"비밀번호 재설정"·"로그인 상태 유지" 요소는 넣지 않는다.

구현 순서:
1. domain: entities/Account.ts(Account, Company), entities/AuthSession.ts, repositories/AccountRepository.ts
   (lookupBusiness(businessNumber, signal): Company[]; signUp(command, signal): SignUpResult; logIn(command, signal): LogInResult;
   logOut(signal): void; getCurrentAccount(signal): Account | null). 결과 union:
   SignUpResult = {outcome:'session', session} | {outcome:'email-taken'} | {outcome:'business-not-found'} | {outcome:'business-lookup-unavailable'}
   LogInResult = {outcome:'session', session} | {outcome:'invalid-credentials'}.
   usecases: LookupBusinessUseCase, SignUpUseCase, LogInUseCase, LogOutUseCase, GetCurrentAccountUseCase (+ 각 .test.ts).
2. data: models/BiznoBusinessDto.ts, models/AccountDto.ts, models/AuthSessionDto.ts(Zod, expiresAt datetime offset),
   api/accountApi.ts(5개 함수, AccountApiError{status, code} — 응답이 application/problem+json이면 code 파싱),
   storage/sessionTokenStorage.ts(localStorage 'govbiz.sessionToken', 모든 접근 try/catch),
   repositories/AccountRepositoryImpl.ts(토큰 저장소를 생성자로 받아 Bearer 헤더 부여·저장·삭제 담당).
   api/__tests__/accountApi.test.ts: 요청 URL·헤더·본문·Bearer, 409/422/401 → AccountApiError code.
3. app/di: types.ts AppCradle에 accountRepository·sessionTokenStorage·5개 UseCase 추가, registerExternalServices에
   sessionTokenStorage(asValue 또는 asFunction), registerRepositories·registerUseCases 등록. appContainer.test.ts 갱신.
4. presentation/features/auth:
   - state/authSlice.ts: status 'unknown'|'anonymous'|'authenticated', account. actions sessionRestored(account|null), signedIn(account), signedOut().
     selectors selectAuthStatus, selectCurrentAccount, selectIsAuthenticated. store.ts에 auth reducer 추가(기존 키 순서 테스트 갱신).
   - validation/signupFormSchema.ts(email, password 8~72 영문·숫자, passwordConfirm 일치, businessNumber [0-9-]{10,12}, termsAgreed literal true),
     loginFormSchema.ts. sample-item의 zod 메시지 스타일(한국어) 사용.
   - viewmodel/useSignupViewModel.ts: react-hook-form + zodResolver; lookupBusiness(AbortController, 최신 요청만 반영),
     선택된 기업 상태, submit → SignUpUseCase → outcome별 메시지 → signedIn → navigate('/').
     useLoginViewModel.ts: submit → LogInUseCase → invalid-credentials 메시지 → signedIn → navigate('/').
     useAuthSessionViewModel.ts: mount 시 토큰 있으면 GetCurrentAccountUseCase → sessionRestored; 없으면 anonymous. logOut().
   - view/SignupPage.tsx·LoginPage.tsx: ViewModel만 사용, API 직접 호출 금지. 라벨·placeholder는 기존 파일 유지.
     제출 중 버튼 비활성, 오류는 role="alert".
5. App.tsx: /login·/signup 라우트, 최상위에서 useAuthSessionViewModel 1회 호출(세션 복원). 로그인 상태에서 /login·/signup 접근 시 /로 이동.
   ChatPage 헤더: 비로그인 → "로그인" 링크(기업마당 공식 데이터 배지는 유지), 로그인 → 회사명 배지 + "로그아웃" 버튼.
   ChatPage.styles.ts에 필요한 클래스만 추가.

테스트: useSignupViewModel·useLoginViewModel·useAuthSessionViewModel의 renderHook 테스트(UseCase 대역 주입),
authSlice 테스트, App.test.tsx에 "회원가입 → 홈 이동 → 헤더에 회사명", "로그인 실패 문구", "저장된 토큰으로 세션 복원",
"로그아웃 후 로그인 링크" 시나리오 추가(fetch stub은 기존 jsonResponse 헬퍼 방식, 401은 problem+json Response).

문서: frontend/README.md 계층 표에 auth 기능 예시 추가, docs/implementation-status.md 사용자 기능 표 갱신
("로그인 상태 유지 | 구현됨 | localStorage 토큰으로 새로고침 후 복원, XSS 대비 CSP는 후속 과제"),
docs/account-auth-contract.md에 브라우저 저장 방식 한 줄.

검증: cd frontend && pnpm lint && pnpm test && pnpm build. 가능하면 Core API를 띄워 실제 가입→로그인→새로고침→로그아웃을 한 번 확인한다.
마무리: 커밋 "회원가입·로그인 화면과 세션 복원 연결" + 트레일러, PR 생성.
하지 말 것: 비로그인 검색 제한(5단계), 프로필 편집, 이메일 인증 UI, 새 npm 의존성.
```

### 프롬프트 5 — 비로그인 무료 검색 3회 제한 (`feature/anonymous-search-limit`)

```
GovBiz Frontend에서 로그인하지 않은 사용자의 검색을 3회로 제한하고, 초과 시 가입·로그인 유도 모달을 띄운다.
feature/auth-bizno의 usageSlice.ts·AuthGateModal.tsx·ChatPage/ViewModel 변경(커밋 f8ffaa9)을 기반으로 하되
4단계의 authSlice와 연결한다. 백엔드 변경은 없다.

브랜치: git switch -c feature/anonymous-search-limit origin/main (4단계 병합 후).

구현:
1. presentation/features/auth/state/usageSlice.ts를 가져오고 anonymousSearchCount를 localStorage('govbiz.anonymousSearchCount')에
   보존한다(초기 상태 로드·변경 시 저장, try/catch, 잘못된 값은 0). 저장 로직은 data/storage/anonymousUsageStorage.ts에 두고 slice는 순수하게 유지
   (store 구독 또는 ViewModel에서 저장).
2. useSupportProgramChatViewModel: selectIsAuthenticated가 true면 제한·카운트 증가를 건너뛴다. 제한 도달 시 isAuthGateOpen.
   로그인·가입 성공(signedIn) 시 usageReset.
3. AuthGateModal: 가져온 컴포넌트 유지. Escape·배경 클릭으로 닫힘, 열릴 때 첫 버튼에 focus, 닫힐 때 검색창 focus 복원
   (ChatPage의 사이드바 focus 처리 방식 참고).
4. ChatPage 헤더: 비로그인이면 "무료 검색 N회 남음" 배지, 로그인이면 표시하지 않음.

테스트: usageSlice 테스트(증가·리셋·상한), ViewModel 테스트(3회 후 UseCase 미호출·모달 열림, 로그인 상태면 무제한),
App.test.tsx 시나리오(3회 검색 후 모달 → "간편 회원가입" 링크로 /signup 이동), localStorage stub으로 새로고침 후 카운트 유지.
store 키 순서를 검사하는 기존 테스트 갱신.

문서: docs/implementation-status.md에 "비로그인 검색 제한 | 구현됨 | 브라우저 저장 기준 3회, 로그인 시 해제. 서버 측 제한은 없음".

검증: pnpm lint && pnpm test && pnpm build.
마무리: 커밋 "비로그인 무료 검색 3회 제한과 가입 유도 모달 추가" + 트레일러, PR 생성.
하지 말 것: 서버 측 rate limit, IP 기반 제한, 결제·플랜 개념.
```

## 7. 남는 결정 사항

1. **spring-security-crypto 추가 승인** (3단계). 대안은 JDK PBKDF2.
2. **토큰 저장 위치**: localStorage(계획) vs HttpOnly 쿠키. 쿠키로 바꾸면 CORS `allowCredentials`·CSRF 대응이 추가된다.
3. **폐업·휴업 상태 가입 허용 여부**: 계획은 저장·표시만 하고 허용.
4. **Flyway 번호**: 다른 브랜치가 V5를 먼저 쓰면 V6로 밀린다.

---

## 0단계 점검 결과 (2026-09-06)

- 기준: `origin/main` = `2a5b205`(PR #46). 로컬 main을 fast-forward했다. Flyway는 V1~V4이므로 다음 번호는 **V5**.
- 도구: JDK 21.0.12.1, Docker 27.3.1(실행 중), node 24.20.0, pnpm 12.3.1.
- 기준선 검증: Core API `./gradlew clean test --no-daemon` 287개 통과(MySQL Testcontainers 통합 26개 포함, 1분 1초).
  Frontend `pnpm lint`·`pnpm test`(114개)·`pnpm build` 통과.
- Bizno 실제 응답(`GET {BIZNO_URL}?key=…&gb=1&q=<번호>&type=json`, HTTP 200, `application/json; Charset=utf-8`):
  - 최상위: `resultCode`(0 정상, -1 키 오류 "미등록 사용자입니다."), `resultMsg`, `page`, `maxpage`, `pagecnt`, `totalCount`, `items`.
  - `items`는 **10칸 배열이며 빈 칸은 `null`**. `totalCount`가 0이면 `items` 키가 **없다**. 키 오류 시 `items`는 빈 문자열 `""`.
  - 항목 필드: `company`, `bno`(하이픈 포함 "124-81-00998"), `cno`, `bsttcd`, `bstt`, `TaxTypeCd`, `taxtype`, `EndDt`.
  - 국세청 등록 사업자: `bsttcd`="01", `bstt`="계속사업자"(휴업·폐업은 다른 코드로 추정, 실측 못 함).
  - **미등록 번호도 항목이 돌아온다**(`company`에 임의 상호, `bsttcd`·`bstt` 빈 문자열, `taxtype`에 "국세청에 등록되지 않은 사업자등록번호입니다." 또는 빈 문자열).
  - 따라서 **"확인된 기업" 판정은 `bsttcd`가 비어 있지 않은 항목**으로 한다. 회사명만으로 판정하면 미등록 번호가 통과한다.
  - `feature/auth-bizno`의 BiznoClient와 다른 점: `items` 누락을 오류로 던지던 것을 빈 결과로 바꾸고, `bno` 정규화(숫자만)와 `bsttcd` 필터를 추가한다.
