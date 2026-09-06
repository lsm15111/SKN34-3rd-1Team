# 계정·인증 HTTP 계약

회원가입 전 기업 확인과 계정 관련 공개 API를 정리합니다. 브라우저는 Core API만 호출하며 Bizno 키는
Core API에만 있습니다. 전체 구성은 [기술 문서](technology.md), 설계와 단계 계획은
[계정·인증 계획](account-auth-plan.md), 구현 범위는 [구현 현황](implementation-status.md)을 참고하세요.

```text
Browser
  → GET /api/v1/auth/businesses/lookup?businessNumber=
      → Core API
          → BiznoBusinessController → BiznoBusinessService → BiznoClient
              → Bizno GET /api/fapi?key=…&gb=1&q=<숫자 10자리>&type=json
          → 국세청 등록 사업자(bsttcd 있음)만 골라 반환. 저장하지 않음
  → POST /api/v1/auth/signup · /login · /logout, GET /api/v1/auth/me
      → AccountAuthController → AccountSignupService · AccountLoginService · AccountSessionService
          → AccountRepository → MySQL (company, account, account_session)
```

| Method·Path | 인증 | 성공 |
|---|---|---|
| `GET /api/v1/auth/businesses/lookup` | 없음 | 200 기업 목록 |
| `POST /api/v1/auth/signup` | 없음 | 201 세션 응답 |
| `POST /api/v1/auth/login` | 없음 | 200 세션 응답 |
| `GET /api/v1/auth/me` | Bearer | 200 계정 |
| `POST /api/v1/auth/logout` | Bearer | 204 |

## 사업자등록번호 기업 확인

```http
GET /api/v1/auth/businesses/lookup?businessNumber=124-81-00998
Accept: application/json
```

| Query parameter | 필수 | 설명 |
|---|---|---|
| `businessNumber` | 예 | 사업자등록번호. `[0-9]{3}-?[0-9]{2}-?[0-9]{5}` 형식이며 Core가 하이픈을 제거해 숫자 10자리로 조회 |

```json
{
  "businesses": [
    {
      "businessNumber": "1248100998",
      "companyName": "삼성전자(주)",
      "businessStatus": "계속사업자"
    }
  ]
}
```

| 필드 | 설명 |
|---|---|
| `businesses` | 국세청 등록 사업자로 확인된 항목. 없으면 빈 배열. 같은 번호가 요청과 다르거나 사업자 상태 코드가 없는 항목은 제외 |
| `businessNumber` | 하이픈 없는 숫자 10자리 |
| `companyName` | Bizno의 상호 원문 |
| `businessStatus` | Bizno의 사업자 상태 원문(`계속사업자` 등). 비어 있을 수 있음 |

Bizno는 미등록 번호에도 `resultCode: 0`과 임의 상호가 담긴 항목을 돌려주고, 결과가 없으면 `items` 키 자체를
생략합니다. Core는 사업자 상태 코드(`bsttcd`)가 있는 항목만 등록 사업자로 인정하며, `businessStatus`로
가입 가능 여부를 판정하지 않습니다.

## 오류

모든 오류는 `application/problem+json`이며 `code` 속성으로 구분합니다. Bizno의 요청 URL·API 키·원본
예외 메시지는 응답에 포함하지 않습니다.

| 상황 | HTTP | `code` |
|---|---:|---|
| `businessNumber` 누락 또는 형식 오류 | 400 | `REQUEST_VALIDATION_FAILED` (`errors[].field = businessNumber`) |
| `BIZNO_API_KEY` 미설정 | 503 | `BIZNO_NOT_CONFIGURED` |
| Bizno 연결 불가 | 503 | `BIZNO_UNAVAILABLE` |
| Bizno 연결·읽기 시간 초과 | 504 | `BIZNO_TIMEOUT` |
| 200이 아닌 HTTP 상태 또는 `resultCode`가 0이 아님(키 미등록 등) | 502 | `BIZNO_UPSTREAM_ERROR` |
| 잘못된 JSON·빈 body·계약 위반 항목 | 502 | `BIZNO_INVALID_RESPONSE` |

```json
{
  "type": "urn:govbiz:problem:bizno-not-configured",
  "title": "Bizno Not Configured",
  "status": 503,
  "detail": "Business registration lookup is not configured on this server.",
  "instance": "/api/v1/auth/businesses/lookup",
  "code": "BIZNO_NOT_CONFIGURED"
}
```

## 회원가입

```http
POST /api/v1/auth/signup
Content-Type: application/json

{ "email": "Manager@Company.co.kr", "password": "password1", "businessNumber": "124-81-00998" }
```

| 필드 | 규칙 |
|---|---|
| `email` | 이메일 형식, 최대 320자. Core가 앞뒤 공백 제거·소문자로 정규화해 저장 |
| `password` | 8~72자, 영문과 숫자 각 1자 이상, 제어 문자 없음. BCrypt 해시만 저장 |
| `businessNumber` | 사업자등록번호. 형식은 기업 확인과 같으며 Core가 Bizno로 다시 확인 |

처리 순서는 이메일 중복 확인 → Bizno 기업 확인(DB transaction 밖) → 비밀번호 해시 → 기업 UPSERT·계정·세션
저장(짧은 transaction 하나)입니다. 담당자 이름·소재지·업종은 받지 않습니다. 약관 동의 시각은 요청 시각으로 기록합니다.
Bizno가 확인한 기업 중 첫 항목을 저장하며, 사업자 상태(휴업·폐업)는 저장·표시만 하고 가입을 막지 않습니다.

성공 시 `201 Created`로 아래 세션 응답을 돌려줍니다.

```json
{
  "sessionToken": "Qm9v…(base64url 43자)",
  "expiresAt": "2026-10-06T12:00:00+09:00",
  "account": {
    "email": "manager@company.co.kr",
    "role": "USER",
    "company": {
      "businessNumber": "1248100998",
      "companyName": "삼성전자(주)",
      "businessStatus": "계속사업자"
    }
  }
}
```

## 로그인

```http
POST /api/v1/auth/login
Content-Type: application/json

{ "email": "manager@company.co.kr", "password": "password1" }
```

성공 시 `200 OK`로 회원가입과 같은 세션 응답을 돌려주고, 같은 계정의 만료된 세션을 정리합니다. 계정이 없거나
비밀번호가 틀린 경우 모두 `401 INVALID_CREDENTIALS` 하나로 답하며, 계정이 없을 때도 비밀번호 비교를 한 번
수행해 응답 시간으로 가입 여부가 드러나지 않게 합니다.

## 세션 토큰

- `sessionToken`은 256비트 무작위 값이며 서버는 SHA-256 해시만 저장합니다. 브라우저가 보관하고
  `Authorization: Bearer <sessionToken>` 헤더로 보냅니다.
- 유효 기간은 `ACCOUNT_SESSION_TTL`(기본 30일)이며 `expiresAt`은 서울 오프셋 ISO-8601 문자열입니다.
- 쿠키·CSRF 토큰·refresh 토큰은 없습니다. 만료·로그아웃된 토큰은 `401 AUTHENTICATION_REQUIRED`가 됩니다.
- GovBiz Web은 토큰을 `localStorage`의 `govbiz.sessionToken`에 저장하고, 앱 진입 시 `GET /api/v1/auth/me`로
  로그인 상태를 복원하며, 로그아웃과 `401` 응답에서 삭제합니다.

## 내 계정 조회

```http
GET /api/v1/auth/me
Authorization: Bearer <sessionToken>
```

```json
{ "account": { "email": "manager@company.co.kr", "role": "USER", "company": { "businessNumber": "1248100998", "companyName": "삼성전자(주)", "businessStatus": "계속사업자" } } }
```

## 로그아웃

```http
POST /api/v1/auth/logout
Authorization: Bearer <sessionToken>
```

세션 행을 삭제하고 `204 No Content`를 돌려줍니다. 이미 없거나 만료된 토큰도 204이며, 헤더가 없거나 Bearer 형식이
아니면 401입니다.

`account.role`은 `USER` 또는 `ADMIN`이며 가입 시 항상 `USER`입니다. 관리자는 SQL로만 지정합니다
(`UPDATE account SET role = 'ADMIN' WHERE email = '…'`). 세션 응답·내 계정 응답의 `account.role`로 화면이 운영 메뉴를 보여 줄지 정합니다.

## 어드민 회원·기업

관리자 세션(`role = ADMIN`)이 필요하며, 로그인은 했지만 관리자가 아니면 `403 ADMIN_REQUIRED`입니다.

```http
GET /api/v1/admin/accounts?email=manager&page=0&size=20
Authorization: Bearer <sessionToken>
```

| Query parameter | 필수 | 설명 |
|---|---|---|
| `email` | 아니요 | 이메일 부분 일치(대소문자 무시). 최대 320자 |
| `page` | 아니요 | 0부터. 기본 0 |
| `size` | 아니요 | 1~100. 기본 20 |

```json
{
  "items": [
    { "id": 12, "email": "manager@company.co.kr", "role": "USER",
      "company": { "businessNumber": "1248100998", "companyName": "삼성전자(주)", "businessStatus": "계속사업자" },
      "createdAt": "2026-09-06T12:00:00+09:00" }
  ],
  "page": 0, "size": 20, "totalCount": 1
}
```

최근 가입순입니다. 비밀번호 해시·세션 정보는 포함하지 않습니다.

```http
POST /api/v1/admin/accounts/{accountId}/sessions/revoke
Authorization: Bearer <sessionToken>
```

대상 계정의 세션을 모두 삭제해 즉시 로그아웃시키고 `204`를 돌려줍니다. 계정이 없으면 `404 ACCOUNT_NOT_FOUND`.
조치는 관리자 id·대상 id·삭제 수와 함께 서버 INFO 로그에 남깁니다.

## 계정 API 오류

| 상황 | HTTP | `code` |
|---|---:|---|
| 요청 필드 검증 실패 | 400 | `REQUEST_VALIDATION_FAILED` (`errors[].field`) |
| 이미 가입된 이메일 | 409 | `EMAIL_ALREADY_REGISTERED` |
| Bizno가 등록 사업자로 확인하지 못함 | 422 | `BUSINESS_NOT_FOUND` |
| 이메일 또는 비밀번호 불일치 | 401 | `INVALID_CREDENTIALS` |
| 세션 토큰 누락·형식 오류·만료·삭제 | 401 | `AUTHENTICATION_REQUIRED` (`WWW-Authenticate: Bearer`) |
| 관리자 전용 API를 일반 계정이 호출 | 403 | `ADMIN_REQUIRED` |
| 운영 대상 계정 없음 | 404 | `ACCOUNT_NOT_FOUND` |
| Bizno 미설정·장애 | 503/502/504 | 기업 확인과 같은 `BIZNO_*` |

응답에는 비밀번호·해시·토큰 해시를 포함하지 않으며 요청 로그에도 비밀번호를 남기지 않습니다.
