# 계정·인증 HTTP 계약

이메일 로그인과 세션 확인·로그아웃, 개발용 시드 로그인의 공개 API를 정리합니다. 회원가입·이메일 인증·기업 등록은
다음 단계에서 추가합니다. 구현 범위는 [구현 현황](implementation-status.md)을 참고하세요.

```text
Browser
  → POST /api/v1/auth/login · /logout, GET /api/v1/auth/me   (세션은 HttpOnly 쿠키 govbiz_session)
      → AccountAuthController → AccountLoginService · AccountSessionService
          → AccountRepository → MySQL (account, account_session)
  → POST /api/v1/auth/dev-login   (app.account.dev-login.enabled=true 일 때만 등록)
      → AccountDevLoginController → AccountDevLoginService
```

| Method·Path | 인증 | 성공 |
|---|---|---|
| `POST /api/v1/auth/login` | 없음 | 200 세션 응답 + `Set-Cookie` |
| `POST /api/v1/auth/dev-login` | 없음 | 200 세션 응답 + `Set-Cookie` (개발 환경 전용) |
| `GET /api/v1/auth/me` | 세션 쿠키 | 200 계정 |
| `POST /api/v1/auth/logout` | 세션 쿠키 | 204 + 쿠키 만료 |
| `PUT /api/v1/me/password` | 세션 쿠키 + Origin | 204. 다른 기기 세션 종료 |
| `GET /api/v1/me/deletion-preview` | 세션 쿠키 | 200 삭제 시 함께 닫히는 것의 수 |
| `DELETE /api/v1/me` | 세션 쿠키 + Origin | 204 + 쿠키 만료 |

## 권한 단계

화면 권한은 역할 이름이 아니라 계정이 통과한 확인 단계 `tier`로 정합니다. 서버가 계정 상태로 계산해 모든 세션
응답에 내려 주고, 프런트의 `RequireAuth`는 이 값으로만 `/app` 아래 라우트를 나눕니다. `GuestOnly`(로그인·회원가입)와
`PublicOnly`(공개 화면)는 세션 유무만 보고 로그인한 사용자를 각각 복귀 경로와 같은 내용의 `/app` 화면으로 보냅니다.
서버는 프런트 판단을 믿지 않고 쓰기 API마다 같은 단계를 다시 검사합니다.

| `tier` | 조건 | 열리는 화면 |
|---|---|---|
| (익명) | 세션 없음 | 공개 화면: 검색·상세·원문 질문·요금제·파트너 모집 읽기(`/`, `/pricing`, `/partners`), 로그인·회원가입 |
| `MEMBER` | 로그인 | 사이드바 작업 화면(`/app/chat` `/app/pricing` `/app/partners` `/app/profile` …) |
| `COMPANY` | 사업자등록번호 조회(Bizno)로 확인한 기업 등록 | 파트너 모집글 작성. 이메일 인증 조건은 인증 기능이 생길 때 더함 |
| `ADMIN` | `account.role = ADMIN` | 위 전부 + `/app/admin/*` |

## 세션 쿠키

세션 JWT는 응답 본문이 아니라 쿠키로만 전달합니다. 브라우저 스크립트는 토큰을 읽을 수 없고, 브라우저가
같은 호스트로 보내는 요청에 자동으로 붙입니다. 프런트는 `fetch`에 `credentials: 'include'`만 둡니다.

```http
Set-Cookie: govbiz_session=<JWT>; Path=/; Max-Age=2592000; HttpOnly; Secure; SameSite=Lax
```

| 속성 | 값 |
|---|---|
| `HttpOnly` | 항상. XSS로 토큰을 읽지 못하게 함 |
| `Secure` | `ACCOUNT_COOKIE_SECURE`(기본 `true`). HTTPS가 없는 로컬 개발(Compose)만 `false` |
| `SameSite=Lax` | 다른 사이트에서 시작한 POST에는 붙지 않음. 링크로 들어오는 GET에는 붙어 로그인 상태가 유지됨 |
| `Max-Age` | `rememberMe=true`일 때만 `ACCOUNT_SESSION_TTL`(기본 30일). `false`면 속성이 없어 브라우저를 닫으면 사라짐 |
| `Domain` | 없음. 발급한 호스트에만 묶임 |

JWT는 HS256이며 `sub`=계정 ID, `iat`·`exp`=초 단위 epoch, `jti`=무작위입니다. Core는 토큰 원문을 저장하지
않고 SHA-256 해시와 절대 만료·마지막 사용 시각을 `account_session`에 둡니다.

### 만료

| 종류 | 값 | 설명 |
|---|---|---|
| 절대 만료(로그인 상태 유지) | `ACCOUNT_SESSION_TTL` = 30일 | JWT `exp`·세션 행 `expires_at`·쿠키 `Max-Age`가 같은 시각 |
| 절대 만료(유지 안 함) | `ACCOUNT_SESSION_SHORT_TTL` = 12시간 | 쿠키는 브라우저 세션 쿠키이고 서버 행이 12시간 뒤 만료 |
| 유휴 만료 | `ACCOUNT_SESSION_IDLE_TTL` = 7일 | 마지막 사용 뒤 7일이 지나면 절대 만료 전이라도 401 |

`GET /me`와 `Account` 파라미터를 받는 모든 API는 서명·만료를 먼저 검사한 뒤 해시로 세션 행을 찾고, 절대·유휴
만료를 확인한 다음 계정을 읽습니다. 마지막 사용 시각은 5분에 한 번만 갱신해 UPDATE를 줄입니다. 로그아웃(행 삭제)된
토큰은 JWT가 유효해도 401이고, 정지된 계정은 403입니다. 로그인할 때마다 새 세션을 만들고 같은 계정의 만료
세션을 정리합니다.

### CSRF

세션 쿠키가 붙은 상태 변경 요청(POST·PUT·PATCH·DELETE)은 두 겹으로 막습니다.

1. `SameSite=Lax`라 다른 사이트의 폼·스크립트가 보내는 POST에는 쿠키가 붙지 않습니다.
2. `Origin` 헤더가 `APP_CORS_ALLOWED_ORIGIN`(쉼표로 여러 개 가능) 중 하나여야 합니다. `Origin`이 없으면 `Referer`의
   origin으로 판단하고, 둘 다 없으면 브라우저 요청으로 볼 수 없어 403 `SESSION_ORIGIN_REJECTED`입니다. 허용되지 않은
   origin은 CORS 처리기가 먼저 403으로 거절하고 `/api/**`에 등록된 `SessionOriginInterceptor`가 한 번 더 막습니다.
   curl로 세션 쿠키를 흉내낼 때는 허용 origin을 함께 보냅니다. Compose 기본값은
   `-H "Origin: http://127.0.0.1:5173"`입니다. 세션 쿠키가 없는 요청은 검사하지 않습니다.

## 회원가입

```http
POST /api/v1/auth/signup
Content-Type: application/json

{ "email": "manager@company.co.kr", "password": "password1" }
```

| 필드 | 규칙 |
|---|---|
| `email` | 이메일 형식, 320자 이하. Core가 앞뒤 공백 제거·소문자로 정규화해 저장하며 같은 이메일은 409. 탈퇴한 계정의 이메일은 익명화되므로 다시 가입할 수 있음 |
| `password` | 8~72자. 길이만 검사하고 문자 종류는 강제하지 않음. BCrypt 해시만 저장 |

성공하면 201과 함께 아래 로그인과 같은 세션 응답을 돌려주고 브라우저 세션 쿠키(`rememberMe=false`와 같음)를
발급합니다. 계정은 `role=USER`, `tier=MEMBER`, `emailVerified=false`로 만들어지고 약관 동의 시각은 요청 시각으로
기록합니다. 이메일 인증은 별도 단계입니다. 가입 시도는 로그인과 같은 접속 주소 한도(분당 20회)를 함께 씁니다.

## 로그인

```http
POST /api/v1/auth/login
Content-Type: application/json

{ "email": "manager@company.co.kr", "password": "password1", "rememberMe": true }
```

| 필드 | 규칙 |
|---|---|
| `email` | 이메일 형식, 320자 이하. Core가 앞뒤 공백 제거·소문자로 정규화해 조회 |
| `password` | 1~72자. 규칙 검사는 가입 시에만 하며 로그인은 일치 여부만 확인 |
| `rememberMe` | 선택, 기본 `false`. `true`면 30일 영구 쿠키, `false`면 브라우저 세션 쿠키 + 12시간 |

세션 응답은 로그인과 개발용 로그인이 같습니다.

```json
{
  "expiresAt": "2026-10-06T12:00:00+09:00",
  "account": { "email": "manager@company.co.kr", "role": "USER", "tier": "MEMBER", "emailVerified": false }
}
```

| 필드 | 설명 |
|---|---|
| `expiresAt` | 세션 절대 만료 시각(서울 offset). `rememberMe=true`면 쿠키의 Max-Age와 같음 |
| `account.role` | `USER` 또는 `ADMIN`. 가입 시에는 항상 `USER` |
| `account.tier` | 권한 단계 `MEMBER`·`COMPANY`·`ADMIN` |
| `account.emailVerified` | 이메일 인증 완료 여부. 가입 직후에는 `false`이고 시드 계정만 `true` |

### 로그인 시도 제한

한 Core 프로세스의 메모리에서 계정과 접속 주소 기준으로 제한합니다. 넘으면 429 `LOGIN_RATE_LIMITED`이며
`Retry-After` 헤더와 본문 `retryAfterSeconds`에 다시 시도할 수 있는 초를 담습니다.

| 기준 | 규칙 |
|---|---|
| 계정(정규화한 이메일) | 연속 실패 5회부터 잠금. 30초에서 시작해 실패가 이어질 때마다 두 배, 최대 15분. 성공하면 초기화 |
| 접속 주소 | 최근 60초 20회 |

## 개발용 시드 로그인

`ACCOUNT_DEV_LOGIN_ENABLED=true`(Compose 기본값)일 때만 `POST /api/v1/auth/dev-login`이 등록됩니다.
본문의 `tier`로 시드 계정을 고릅니다. 본문이 없으면 `ACCOUNT_DEV_LOGIN_EMAIL`(기본 `admin@govbiz.local`)의 관리자,
`{ "tier": "MEMBER" }`는 `ACCOUNT_DEV_LOGIN_MEMBER_EMAIL`(기본 `member@govbiz.local`)의 기업 없는 회원,
`{ "tier": "COMPANY" }`는 `ACCOUNT_DEV_LOGIN_COMPANY_EMAIL`(기본 `company@govbiz.local`)의 기업 회원으로 30일 세션
쿠키를 발급합니다. 예전 계약의 `{ "role": "USER" }`도 회원으로 받습니다. 계정이 없으면 이메일 인증이 끝난 상태로 만들고
`ACCOUNT_DEV_LOGIN_PASSWORD`(기본 `govbiz-admin1`)를 비밀번호로 저장하므로 일반 로그인 화면에서도 같은 값으로
로그인됩니다. 기업 회원은 사업자등록번호 조회 없이 예시 기업 `그루브데이터 주식회사`(가상 사업자등록번호
`2208800042`)를 함께 등록합니다. 운영 환경에서는 반드시 `false`로 두고 꺼져 있으면 404입니다. 프런트의
`개발 로그인 · 관리자`·`회원`·`기업` 버튼은 개발 빌드(`import.meta.env.DEV`)에서만 렌더링됩니다.

## 개발용 목데이터

[`infrastructure/dev-seed.sql`](../infrastructure/dev-seed.sql)을 MySQL에 흘려 넣으면 파트너 모집 화면을 채우는 예시 데이터가
들어갑니다. Core API 코드는 관여하지 않고, 화면은 이 행들을 보통 회원이 만든 것과 똑같이 읽어 모집 상태·제안 상태·제안 수·
연락처 공개를 계산합니다.

```bash
docker exec -i govbiz-mysql-1 sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < infrastructure/dev-seed.sql
```

| 항목 | 내용 |
|---|---|
| 예시 공고 7건 | 제공처 코드 `DEMO`로 upsert. 제공처 동기화는 출처별 스냅샷이라 지우지 않고, 검색 카탈로그는 색인 준비 제공처만 보여 주므로 모집글에서만 보임 |
| 파트너 기업 9곳 | `coop@<기업>.example` 이메일의 기업 회원 계정(개발 로그인 비밀번호로 로그인 가능). 사업자등록번호·도메인은 가상 값, 협업·파트너 설정까지 채움 |
| 내 기업 | 기업 회원 개발 로그인 계정(`company@govbiz.local`, 그루브데이터). 협업·파트너 설정도 채움 |
| 모집글 10건 | 모집 중 8건, 모집 마감일 경과 1건, 공고 접수 종료 1건. 그중 1건이 내 글 |
| 제안 22건 | 내 글로 받은 5건(대기 2·수락 1·거절 1·만료 1), 내가 보낸 5건(대기·수락·철회·거절·모집 마감 만료), 나머지는 다른 기업끼리 |

날짜는 실행하는 날(`CURDATE()`) 기준 상대값입니다. 같은 파일을 몇 번 실행해도 됩니다. 시드 계정과 FK CASCADE로 딸린
기업·협업 설정·모집글·제안을 먼저 지우고 다시 만들기 때문에 UNIQUE 충돌이 없고, 며칠 지나 대기 제안이 7일 응답 기한으로
만료되거나 모집 마감일이 지나면 다시 실행해 처음 모양으로 되돌립니다. 시드 계정이 아닌 회원의 행은 건드리지 않지만, 그
회원이 시드 모집글에 보낸 제안은 모집글과 함께 지워집니다.

## 사업자등록번호 확인

기업 등록 전에 회원이 입력한 사업자등록번호를 국세청 조회(Bizno API)로 확인합니다. 조회 결과에서는 상호와 사업자 상태만
쓰고 법인번호·과세유형은 쓰지 않습니다. 세션 쿠키가 필요하며 국세청 조회 자체는 서버만 호출합니다.

```http
GET /api/v1/me/company/lookup?businessNumber=124-81-00998
Cookie: govbiz_session=<JWT>
```

```json
{ "businessNumber": "1248100998", "companyName": "삼성전자(주)", "businessStatus": "계속사업자", "isActive": true }
```

| 필드 | 설명 |
|---|---|
| `businessNumber` | 요청은 하이픈 선택, 응답은 숫자 10자리 |
| `companyName` `businessStatus` | 국세청 원문. 상태는 계속사업자·휴업자·폐업자 |
| `isActive` | 계속사업자(상태 코드 `01`)만 `true`. 기업 등록은 이 값이 `true`일 때만 허용할 예정 |

등록되지 않은 번호는 404 `BUSINESS_NOT_FOUND`이고, 국세청 조회가 안 되는 경우는 `BIZNO_*` 코드로 구분합니다.
## 기업 등록·프로필

기업은 계정당 하나이며 사업자등록번호는 Bizno 조회 API로 확인합니다. 조회 결과에서는 상호와 사업자 상태만 쓰고
소재지·업종·설립연도와 홈페이지(선택)는 담당자가 입력합니다. 모든 요청은 세션 쿠키가 필요합니다.

| 메서드·경로 | 용도 | 성공 |
|---|---|---|
| `GET /api/v1/me/company/lookup?businessNumber=` | 등록 전 미리보기. 하이픈 선택 | 200 `businessNumber`(10자리) `companyName` `businessStatus` `isActive` |
| `GET /api/v1/me/company` | 내 기업 | 200 기업 응답, 없으면 404 `COMPANY_NOT_REGISTERED` |
| `POST /api/v1/me/company` | 등록. 서버가 다시 조회해 계속사업자만 허용 | 201 기업 응답. 이후 `/auth/me`의 `tier`가 `COMPANY` |
| `PUT /api/v1/me/company` | 담당자 입력 항목 수정 | 200 기업 응답 |

```http
POST /api/v1/me/company
Content-Type: application/json
Cookie: govbiz_session=<JWT>

{ "businessNumber": "124-81-00998", "region": "서울특별시", "industry": "정보통신업", "foundedYear": 2020,
  "homepageUrl": "https://example.co.kr" }
```

| 필드 | 규칙 |
|---|---|
| `businessNumber` | 등록 때만. 숫자 10자리, 하이픈 선택. 상호·상태는 서버가 조회 결과로 채우므로 받지 않음 |
| `region` `industry` | 1~40자 / 1~80자. 프런트는 17개 시·도와 표준산업분류 대분류 목록에서 고름 |
| `foundedYear` | 1900~올해. 올해를 넘으면 400 `REQUEST_VALIDATION_FAILED`에 `errors[].field=foundedYear` |
| `homepageUrl` | 선택. `http(s)://`로 시작하고 공백이 없는 500자 이하 주소. 앞뒤 공백은 다듬고 빈 문자열은 비운 것으로 저장. 다른 스킴은 400 `errors[].field=homepageUrl` |

기업 응답은 요청 필드(`businessNumber`·`region`·`industry`·`foundedYear`·`homepageUrl`)에 `companyName` `businessStatus`
`businessVerifiedAt` `updatedAt`을 더한 것입니다. 세션·내 계정 응답의 `account.company`에는 `companyName`·`businessNumber` 요약이 실리고 기업이 없으면 `null`입니다.

### 협업·파트너 설정

기업이 파트너 모집에서 어떤 역할과 분야로 협업할지 밝히는 설정입니다. 모집글 상세와 기업 프로필 보기에서 다른 기업에게
보이며 담당자 연락처는 담지 않습니다. 기업이 없으면 404 `COMPANY_NOT_REGISTERED`입니다.

```http
GET /api/v1/me/company/partner-profile
PUT /api/v1/me/company/partner-profile
Cookie: govbiz_session=<JWT>

{ "roles": ["LEAD", "PARTICIPANT"], "interestAreas": ["기술", "사업화"],
  "introduction": "AI 문서 분류 SaaS를 만드는 팀입니다.", "capabilities": ["문서 분류 AI", "공공 레퍼런스"] }
```

| 필드 | 규칙 |
|---|---|
| `roles` | 모집글의 `PartnerRole`(`LEAD`·`PARTICIPANT`·`DEMAND`) 1개 이상, 중복 제거. 프런트는 주관기관·참여기관만 고름 |
| `interestAreas` | 최대 3개, 각 30자 이하. 지원사업 검색의 분야 이름을 씀. 빈 값은 버림 |
| `introduction` | 선택, 200자 이하. 앞뒤 공백을 다듬어 저장 |
| `capabilities` | 최대 5개, 각 30자 이하, 중복 불가. 모집글 역량 칩과 같은 형식 |

응답은 요청 필드에 `isSet`(저장한 적이 있는지)과 `updatedAt`을 더한 것입니다. 저장한 적이 없으면 `isSet=false`와
빈 목록·빈 문자열이고, `PUT`은 기업당 한 행을 만들거나 덮어씁니다.

## 파트너 모집글

모집글은 제공처에 현재 있는 공고 하나에 묶이며, 작성은 기업을 등록한 회원(`COMPANY`)만 할 수 있습니다. 읽기는 세션 없이도
가능하고 쿠키가 있으면 `isMine`으로 내 글을 표시합니다. 담당자 이름·연락처는 응답에 싣지 않습니다.

| 메서드·경로 | 용도 | 성공 |
|---|---|---|
| `GET /api/v1/partners/recruitments` | 목록. `keyword`(제목·공고·기관·기업명, 100자) `seekingRole`(LEAD·PARTICIPANT·DEMAND) `region`(시·도 또는 전국) `mine`(세션 필요) `sort`(DEADLINE·RECENT) `page` `pageSize`(1~50, 기본 20) | 200 `recruitments[]` `total` `page` `pageSize` `totalPages` |
| `GET /api/v1/partners/recruitments/{id}` | 상세 | 200 모집글 응답, 없으면 404 `RECRUITMENT_NOT_FOUND` |
| `POST /api/v1/partners/recruitments` | 작성. 서버가 공고 존재·접수 상태·마감일·중복을 확인 | 201 모집글 응답 |

```http
POST /api/v1/partners/recruitments
Content-Type: application/json
Cookie: govbiz_session=<JWT>

{ "sourceCode": "BIZINFO", "sourceProgramId": "PBLN_000000000112345", "title": "AI 실증 과제 참여기관 구합니다",
  "body": "라벨링 운영을 맡아 주실 참여기관을 찾습니다.", "ownRole": "LEAD", "seekingRole": "PARTICIPANT", "seekingCount": 1,
  "region": "서울", "minimumCompanyAgeYears": 3, "capabilities": ["데이터 구축", "라벨링"], "recruitmentDeadline": "2026-09-20" }
```

| 필드 | 규칙 |
|---|---|
| `sourceCode` `sourceProgramId` | 공고 검색·상세가 쓰는 제공처 식별자 조합. 제공처에서 사라진 공고는 404 |
| `title` `body` | 1~80자 / 1~2000자 |
| `ownRole` | `LEAD` 또는 `PARTICIPANT`. 수요처는 찾는 역할로만 씀 |
| `seekingRole` `seekingCount` | 찾는 역할과 기업 수(1~9곳) |
| `region` | 공고 분류와 같은 시·도 이름 또는 `전국`(20자 이하). 목록에서 지역을 고르면 전국 모집글도 함께 보임 |
| `minimumCompanyAgeYears` | 찾는 기업의 최소 업력(년), 1~50. 생략·null이면 무관 |
| `capabilities` | 30자 이하 문자열 최대 10개. 중복은 한 번만 저장 |
| `recruitmentDeadline` | 오늘 이후이면서 공고 접수 마감 전날까지. 접수 마감일이 없는 공고는 제한 없음 |

모집글 응답은 요청 필드에 `id` `status`(OPEN·CLOSED, 마감일·공고 접수 마감·수동 마감으로 조회 시점에 계산) `isMine`
`proposalCount`(철회하지 않은 제안 수) `myProposal`(로그인한 회원이 이 모집글에 보낸 제안의 `id` `status`, 없으면 null) `company`(`companyName` `region` `industry` `foundedYear` `isEmailVerified` `isBusinessVerified`)
`program`(`sourceCode` `sourceProgramId` `title` `organization` `summary` `targetDescription` `applicationPeriod` `applicationEndDate` `sourceUrl`)
`createdAt` `updatedAt`을 더한 것입니다. 목록 항목은 `body`·`ownRole`·`minimumCompanyAgeYears`·`myProposal`·`updatedAt`과 공고 원문 없이
`program`에 `title` `organization` `applicationEndDate`만 싣습니다. 목록은 마감일·공고 접수 마감일·수동 마감으로 모집 중인 글만 고르고
`mine=true`는 마감된 내 글도 포함합니다. 접수 마감일이 없는 공고의 접수 종료 문구는 SQL로 거르지 않으므로 항목의 `status`로 마감을 확인합니다.
한 계정은 같은 공고에 모집글 하나만 쓸 수 있습니다(`uq_partner_recruitment_account_program`).

## 파트너 제안

제안은 기업을 등록한 회원이 모집 중인 남의 모집글에 한 번 보냅니다. 모든 요청은 세션이 필요하고 당사자(제안자·모집글 작성자)만
읽고 처리할 수 있습니다. 이메일 인증 조건은 인증 기능이 생길 때 더합니다.

| 메서드·경로 | 용도 | 성공 |
|---|---|---|
| `POST /api/v1/partners/recruitments/{id}/proposals` | 제안 보내기. `message`(1~500자) `shareProfile`(기본 true) | 201 제안 응답 |
| `GET /api/v1/partners/proposals/{id}` | 제안 하나. 당사자가 아니면 404 | 200 제안 응답 |
| `POST /api/v1/partners/proposals/{id}/accept` `.../decline` | 모집글 작성자의 수락·거절. 대기 중일 때만 | 200 제안 응답 |
| `POST /api/v1/partners/proposals/{id}/withdraw` | 제안자의 철회. 대기 중일 때만 | 200 제안 응답 |
| `GET /api/v1/me/proposals?box=received\|sent` | 제안함. 받은 제안은 내 모집글로 온 것, 보낸 제안은 내가 보낸 것. 다른 `box` 값은 400 `REQUEST_VALIDATION_FAILED` | 200 `box` `proposals[]` `pendingCount` |

제안 응답은 `id` `status` `message` `shareProfile` `isSent`(조회한 회원이 보낸 제안이면 true) `recruitment`(`id` `title` `status`
`recruitmentDeadline`) `counterpart` `createdAt` `expiresAt` `respondedAt`입니다. `status`는 저장하지 않고 조회 시점에 계산합니다.

| `status` | 조건 |
|---|---|
| `PENDING` | 응답·철회가 없고 보낸 지 7일 이내이며 모집글이 모집 중 |
| `ACCEPTED` / `DECLINED` | 작성자가 수락·거절 |
| `WITHDRAWN` | 제안자가 철회 |
| `EXPIRED` | 응답 없이 7일이 지났거나 모집글이 마감됨 |

`counterpart`는 조회한 회원의 상대 기업입니다. `companyName` `isEmailVerified` `isBusinessVerified`는 항상 있고,
`profile`(`region` `industry` `foundedYear` `homepageUrl`)은 제안자가 프로필 공유를 켰거나 제안이 수락됐을 때, `contact`(`email`
`businessNumber`)는 수락됐을 때만 양쪽에 실립니다. 모집글 상세 응답의 `proposalCount`는 철회하지 않은 제안 수이고,
로그인한 회원에게는 `myProposal`(`id` `status`)이 붙습니다. 같은 모집글에는 제안을 한 번만 보낼 수 있습니다
(`uq_partner_proposal_recruitment_proposer`). 거절·만료·철회된 뒤에도 다시 보낼 수 없습니다.

## 내 계정·로그아웃

```http
GET /api/v1/auth/me
Cookie: govbiz_session=<JWT>
```

```json
{ "account": { "email": "manager@company.co.kr", "role": "USER", "tier": "MEMBER", "emailVerified": false } }
```

`POST /api/v1/auth/logout`은 세션 행을 삭제하고 `Max-Age=0` 쿠키로 브라우저의 쿠키를 지운 뒤 204를
돌려줍니다. 이미 없거나 만료된 세션도 204입니다. 쿠키가 없으면 401입니다.

Controller는 `Account` 파라미터를 선언하면 `AuthenticatedAccountArgumentResolver`가 세션 쿠키로 채웁니다.
non-null 파라미터는 세션이 없을 때 401이고, `Account?`는 쿠키가 없으면 null을 넣어 비로그인 조회를 허용합니다.

프런트는 토큰을 다루지 않으므로 앱 시작 시 `/me`를 부를지만 localStorage의 힌트(`govbiz.hasSession`)로
정합니다. 힌트가 틀려도 서버의 401·403이 바로잡고 힌트를 지웁니다. 세션 복원이 끝나기 전에는 `RequireAuth`가
리다이렉트하지 않으며, 로그인 화면은 `?next=`의 앱 안 경로로 돌아갑니다.

## 비밀번호 변경·계정 삭제

둘 다 로그인한 상태에서 **현재 비밀번호를 다시 확인**합니다. 틀리면 422 `CURRENT_PASSWORD_MISMATCH`이며 세션은 그대로입니다
(401이 아니라 로그아웃되지 않습니다). 접속 주소 한도(분당 20회)는 로그인과 같이 씁니다.

```http
PUT /api/v1/me/password
Cookie: govbiz_session=<JWT>
Origin: http://127.0.0.1:5173

{ "currentPassword": "password1", "newPassword": "new-password-2" }
```

| 필드 | 규칙 |
|---|---|
| `currentPassword` | 1~72자 |
| `newPassword` | 8~72자(가입과 같음). 프런트는 현재 비밀번호와 같은 값을 보내지 않음 |

성공은 204입니다. 새 해시를 저장하고 **요청한 세션만 남긴 채 같은 계정의 다른 세션 행을 지워** 다른 기기는 401이 됩니다.

```http
GET /api/v1/me/deletion-preview
```

```json
{ "hasCompany": true, "openRecruitmentCount": 2, "receivedPendingProposalCount": 3, "sentPendingProposalCount": 1 }
```

삭제 확인 모달이 보여 주는 수이며 삭제하지 않습니다. `openRecruitmentCount`는 수동 마감하지 않은 내 모집글,
대기 제안 수는 조회 시점 상태로 셉니다.

```http
DELETE /api/v1/me
Cookie: govbiz_session=<JWT>
Origin: http://127.0.0.1:5173

{ "password": "password1" }
```

성공은 204와 `Max-Age=0` 쿠키입니다. 한 transaction에서 내가 보낸 대기 제안 철회, 내 모집글 수동 마감(받은 제안은
만료로 계산), 기업 행 삭제, 모든 세션 삭제, `deleted_at` 표시를 합니다. 계정 행은 모집글·제안이 참조하므로 남기되 이메일을
`deleted+<id>+<시각>@deleted.invalid`로 바꿉니다. 그래서 같은 이메일로 다시 가입하면 새 계정이 되고, 옛 계정으로는 로그인할 수 없습니다.

## 오류

모든 오류는 `application/problem+json`이며 `code` 속성으로 구분합니다. 비밀번호와 토큰 원문은 응답·로그에
포함하지 않습니다.

| 상황 | HTTP | `code` |
|---|---:|---|
| 이메일 형식·비밀번호 누락 등 요청 검증 실패 | 400 | `REQUEST_VALIDATION_FAILED` (`errors[].field`) |
| 이메일 없음 또는 비밀번호 불일치 | 401 | `INVALID_CREDENTIALS` |
| 이미 가입된 이메일로 회원가입 | 409 | `EMAIL_ALREADY_REGISTERED` |
| 비밀번호 변경·계정 삭제의 현재 비밀번호 불일치 | 422 | `CURRENT_PASSWORD_MISMATCH` |
| 기업을 등록하지 않은 계정의 기업 조회·수정 | 404 | `COMPANY_NOT_REGISTERED` |
| 등록되지 않은 사업자등록번호 | 404 | `BUSINESS_NOT_FOUND` |
| 휴업·폐업 사업자 등록 시도 | 422 | `BUSINESS_NOT_ACTIVE` (`businessStatus`) |
| 이미 기업을 등록한 계정의 재등록 | 409 | `COMPANY_ALREADY_REGISTERED` |
| 다른 계정이 등록한 사업자등록번호 | 409 | `BUSINESS_NUMBER_ALREADY_REGISTERED` |
| 기업을 등록하지 않은 회원의 모집글 작성·제안 보내기 | 403 | `COMPANY_REQUIRED` |
| 모집글에 묶을 공고가 없거나 제공처에서 사라짐 | 404 | `RECRUITMENT_PROGRAM_NOT_FOUND` |
| 접수가 끝난 공고에 모집글 작성 | 422 | `RECRUITMENT_PROGRAM_CLOSED` |
| 모집 마감일이 오늘 이전이거나 공고 접수 마감 전날을 넘김 | 422 | `RECRUITMENT_DEADLINE_NOT_ALLOWED` (`latestAllowedDeadline`) |
| 같은 공고에 이미 쓴 모집글이 있음 | 409 | `RECRUITMENT_ALREADY_EXISTS` |
| 모집글 없음 | 404 | `RECRUITMENT_NOT_FOUND` |
| 제안이 없거나 당사자가 아님 | 404 | `PROPOSAL_NOT_FOUND` |
| 자기 모집글에 제안 | 422 | `PROPOSAL_OWN_RECRUITMENT` |
| 마감된 모집글에 제안 | 422 | `RECRUITMENT_CLOSED` |
| 같은 모집글에 이미 보낸 제안이 있음 | 409 | `PROPOSAL_ALREADY_SENT` |
| 대기 중이 아닌 제안의 수락·거절·철회 | 409 | `PROPOSAL_NOT_PENDING` |
| 작성자가 아닌 수락·거절, 제안자가 아닌 철회 | 403 | `PROPOSAL_ACTION_FORBIDDEN` |
| Bizno 조회 키 미설정 / 연결 실패 / 시간 초과 / 응답 오류 | 503 / 503 / 504 / 502 | `BIZNO_NOT_CONFIGURED` `BIZNO_UNAVAILABLE` `BIZNO_TIMEOUT` `BIZNO_UPSTREAM_ERROR`·`BIZNO_INVALID_RESPONSE` |
| 세션 쿠키 없음·서명 오류·절대/유휴 만료·로그아웃된 세션·삭제된 계정 | 401 | `AUTHENTICATION_REQUIRED` (`WWW-Authenticate: Bearer`) |
| 정지된 계정의 로그인 또는 세션 사용 | 403 | `ACCOUNT_SUSPENDED` |
| 세션 쿠키가 붙은 상태 변경 요청의 Origin이 없거나 허용 목록에 없음 | 403 | `SESSION_ORIGIN_REJECTED` |
| 로그인·회원가입 시도 한도 초과 | 429 | `LOGIN_RATE_LIMITED` (`Retry-After`, `retryAfterSeconds`) |

```json
{
  "type": "urn:govbiz:problem:invalid-credentials",
  "title": "Invalid Credentials",
  "status": 401,
  "detail": "The email or password is incorrect.",
  "instance": "/api/v1/auth/login",
  "code": "INVALID_CREDENTIALS"
}
```

계정 없음과 비밀번호 불일치는 같은 응답이며, 계정이 없을 때도 해시 비교를 한 번 수행해 응답 시간으로
가입 여부가 드러나지 않게 합니다. 정지 여부는 비밀번호가 맞은 뒤에만 알립니다.

## 설정

| 환경변수 | 기본값 | 용도 |
|---|---|---|
| `ACCOUNT_SESSION_TTL` | `P30D` | "로그인 상태 유지" 세션의 절대 만료(ISO-8601). 쿠키 Max-Age와 같음 |
| `ACCOUNT_SESSION_SHORT_TTL` | `PT12H` | "로그인 상태 유지"를 끈 세션의 절대 만료. 쿠키는 브라우저 세션 쿠키 |
| `ACCOUNT_SESSION_IDLE_TTL` | `P7D` | 마지막 사용 뒤 세션을 끝내는 유휴 기간 |
| `ACCOUNT_JWT_SECRET` | 없음(필수. Compose·`.env.example`은 로컬 개발용 값) | HS256 서명 비밀키(32자 이상). 비어 있으면 기동 실패 |
| `ACCOUNT_COOKIE_SECURE` | `true` (Compose는 `false`) | 세션 쿠키의 `Secure` 속성. HTTPS 운영에서는 `true` |
| `ACCOUNT_DEV_LOGIN_ENABLED` | `false` (Compose는 `true`) | 개발용 시드 로그인 endpoint 등록 여부 |
| `ACCOUNT_DEV_LOGIN_EMAIL` | `admin@govbiz.local` | 관리자 시드 계정 이메일 |
| `ACCOUNT_DEV_LOGIN_MEMBER_EMAIL` | `member@govbiz.local` | 회원 시드 계정 이메일 |
| `ACCOUNT_DEV_LOGIN_COMPANY_EMAIL` | `company@govbiz.local` | 예시 기업을 등록한 기업 회원 시드 계정 이메일 |
| `ACCOUNT_DEV_LOGIN_PASSWORD` | `govbiz-admin1` | 시드 계정을 만들 때 저장하는 비밀번호(8~72자) |
| `BIZNO_API_KEY` | 빈 값 | 사업자등록번호 조회용 Bizno(bizno.net) API 키. 비어 있으면 기업 조회·등록이 503 |
| `BIZNO_URL` | `https://bizno.net/api/fapi` | Bizno 조회 endpoint. 경로는 `/api/fapi` 고정 |
