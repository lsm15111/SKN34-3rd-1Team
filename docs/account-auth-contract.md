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
```

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

## 회원가입·로그인 (예정)

가입·로그인·로그아웃·내 계정 조회 endpoint는 [계정·인증 계획](account-auth-plan.md) §3.4의 계약대로
다음 단계에서 추가합니다. 이 문서는 해당 endpoint가 구현될 때 함께 갱신합니다.
