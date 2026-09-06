# 파트너 모집 HTTP 계약

공식 공고 하나에 묶인 파트너 모집글과 참여 제안의 공개 API입니다. 설계와 단계 계획은
[파트너 모집 계획](partner-recruitment-plan.md), 계정·세션 계약은 [계정·인증 계약](account-auth-contract.md)을 참고하세요.

```text
Browser
  → GET /api/v1/recruitment-posts, GET /api/v1/recruitment-posts/{id}      (로그인 선택)
  → POST/PUT /api/v1/recruitment-posts, POST …/{id}/close, GET …/mine      (Bearer 필수)
      → RecruitmentPostController → RecruitmentPostService
          → RecruitmentPostRepository → MySQL recruitment_post (+ company JOIN)
          → SupportProgramRepository.findPresentBySourceAndProgramId (연결 공고 읽기)
          → RecruitmentPostStatusResolver (숨김 > 종료 > 모집 중, 저장하지 않음)
```

## 공통 규칙

- 모집글은 현재 공개된 공고 `(sourceCode, sourceProgramId)` 하나에 묶이며 등록 뒤 공고는 바꾸지 않습니다.
- 표시 상태 `status`는 읽을 때 계산합니다. `HIDDEN`(운영자 숨김) → `CLOSED`(작성자 조기 마감, 모집 마감일 경과,
  연결 공고 미공개 또는 접수 `CLOSED`) → 그 외 `OPEN`. 목록은 `OPEN`만, 작성 기업은 `/mine`에서 모든 상태를 봅니다.
- 제목·본문에 이메일 주소나 한국 전화번호 형태가 있으면 `422 CONTACT_IN_TEXT`로 거부합니다. 담당자 연락처는 제안이
  수락된 뒤 시스템이 공개합니다.
- `Authorization: Bearer` 헤더가 있으면 조회 API도 세션을 검증합니다(만료 토큰은 401). 헤더가 없으면 비로그인 조회입니다.

## 모집글 목록

```http
GET /api/v1/recruitment-posts?sourceCode=BIZINFO&sourceProgramId=PBLN_000000091203&page=0&size=20
```

| Query parameter | 필수 | 설명 |
|---|---|---|
| `sourceCode`, `sourceProgramId` | 아니요(둘 다 함께) | 특정 공고의 모집글만 |
| `page` | 아니요 | 0부터. 기본 0 |
| `size` | 아니요 | 1~50. 기본 20 |

`OPEN` 글만 모집 마감일이 가까운 순으로 돌려줍니다. 응답은 `{ items: [RecruitmentPost], page, size, totalCount }`.

## 모집글 상세

```http
GET /api/v1/recruitment-posts/{id}
```

```json
{
  "id": 12,
  "status": "OPEN",
  "title": "AI 실증 과제 데이터 구축·라벨링 참여기관 구합니다",
  "body": "학습용 민원 문서 정제와 라벨링을 맡아 주실 참여기관을 찾습니다.",
  "ourRole": "LEAD",
  "wantedRole": "PARTICIPANT",
  "wantedCompanyCount": 1,
  "wantedRegion": "서울·경기·인천",
  "requiredCapabilities": ["데이터 구축", "라벨링 운영"],
  "closesOn": "2026-09-20",
  "closedEarlyAt": null,
  "createdAt": "2026-09-06T12:00:00+09:00",
  "updatedAt": "2026-09-06T12:00:00+09:00",
  "company": { "businessNumber": "1248100998", "companyName": "데이터브릿지 주식회사", "businessStatus": "계속사업자" },
  "program": {
    "sourceCode": "BIZINFO", "sourceProgramId": "PBLN_000000091203",
    "title": "서울 AI 스타트업 실증 지원사업", "organization": "서울경제진흥원", "status": "OPEN",
    "applicationPeriod": "2026. 9. 1. ~ 2026. 9. 30. 18:00", "applicationEndDate": "2026-09-30",
    "targetDescription": "서울 소재 창업 7년 이내 AI 기업",
    "sourceUrl": "https://www.bizinfo.go.kr/…"
  },
  "proposalCount": 0,
  "viewer": { "isOwner": false }
}
```

| 필드 | 설명 |
|---|---|
| `ourRole` | 작성 기업의 역할. `LEAD`(주관기관) / `PARTICIPANT`(참여기관) |
| `wantedRole` | 찾는 역할. `LEAD` / `PARTICIPANT` / `DEMAND`(수요처) |
| `program` | 연결 공고 요약. 공고가 더 이상 공개되지 않으면 `null`이며 그때 `status`는 `CLOSED` |
| `proposalCount` | 받은 제안 수. 제안 기능(P4) 전까지 항상 0 |
| `viewer.isOwner` | 요청 세션의 기업이 작성 기업인지 |

숨김·종료된 글은 작성 기업에게만 보이고 다른 사용자에게는 `404 RECRUITMENT_POST_NOT_FOUND`입니다.

## 모집글 등록·수정·조기 마감·내 글

```http
POST /api/v1/recruitment-posts
Authorization: Bearer <sessionToken>
Content-Type: application/json

{
  "sourceCode": "BIZINFO",
  "sourceProgramId": "PBLN_000000091203",
  "post": {
    "title": "…", "body": "…",
    "ourRole": "LEAD", "wantedRole": "PARTICIPANT", "wantedCompanyCount": 1,
    "wantedRegion": "서울·경기·인천", "requiredCapabilities": ["데이터 구축"],
    "closesOn": "2026-09-20"
  }
}
```

| 필드 | 규칙 |
|---|---|
| `title` | 1~80자, 앞뒤 공백·제어 문자 없음 |
| `body` | 1~2,000자 |
| `ourRole` | `LEAD` / `PARTICIPANT` (`DEMAND` 불가) |
| `wantedRole` | `LEAD` / `PARTICIPANT` / `DEMAND` |
| `wantedCompanyCount` | 1~10 |
| `wantedRegion` | 0~60자 |
| `requiredCapabilities` | 0~10개, 각 1~30자, 중복 없음 |
| `closesOn` | 오늘 이후. 공고 `applicationEndDate`가 있으면 그 이하 |

- `PUT /api/v1/recruitment-posts/{id}` — body는 `{ "post": {…} }`. 작성 기업이 `OPEN` 상태에서만.
- `POST /api/v1/recruitment-posts/{id}/close` — 조기 마감. 작성 기업이 `OPEN` 상태에서만.
- `GET /api/v1/recruitment-posts/mine` — 내 기업이 쓴 모집글 전체(`{ items: [...] }`), 최근 작성순.

성공 시 등록은 `201`, 나머지는 `200`으로 모집글 상세와 같은 JSON을 돌려줍니다.

## 오류

| 상황 | HTTP | `code` |
|---|---:|---|
| 필드 검증 실패 | 400 | `REQUEST_VALIDATION_FAILED` |
| 세션 없음·만료(쓰기·내 글) | 401 | `AUTHENTICATION_REQUIRED` |
| 작성 기업이 아님 | 403 | `NOT_POST_OWNER` |
| 모집글 없음 또는 타인의 숨김·종료 글 | 404 | `RECRUITMENT_POST_NOT_FOUND` |
| 모집 중이 아닌 글 수정·마감 | 409 | `RECRUITMENT_POST_NOT_OPEN` |
| 연결 공고가 미공개·접수 종료 | 422 | `SUPPORT_PROGRAM_NOT_OPEN` |
| 모집 마감일이 오늘 이전이거나 공고 마감 이후 | 422 | `RECRUITMENT_CLOSES_ON_INVALID` |
| 제목·본문에 연락처 | 422 | `CONTACT_IN_TEXT` |

## 참여 제안 (P4 예정)

제안 보내기·받은/보낸 제안·수락·거절·철회는 [파트너 모집 계획](partner-recruitment-plan.md) §4.2의 계약대로
다음 단계에서 추가합니다.
