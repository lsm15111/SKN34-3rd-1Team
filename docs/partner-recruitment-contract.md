# 파트너 모집 HTTP 계약

공식 공고 하나에 묶인 파트너 모집글과 참여 제안의 공개 API입니다. 설계와 단계 계획은
[파트너 모집 계획](partner-recruitment-plan.md), 계정·세션 계약은 [계정·인증 계약](account-auth-contract.md)을 참고하세요.

```text
Browser
  → GET /api/v1/recruitment-posts, GET /api/v1/recruitment-posts/{id}      (로그인 선택)
  → POST/PUT /api/v1/recruitment-posts, POST …/{id}/close, GET …/mine      (Bearer 필수)
      → RecruitmentPostController → RecruitmentPostService
          → RecruitmentPostRepository → MySQL recruitment_post (+ company JOIN)
          → RecruitmentProposalRepository (받은 제안 수, 조회 기업의 제안 상태)
          → SupportProgramRepository.findPresentBySourceAndProgramId (연결 공고 읽기)
          → RecruitmentPostStatusResolver (숨김 > 종료 > 모집 중, 저장하지 않음)
  → POST/GET /api/v1/recruitment-posts/{id}/proposals, POST /api/v1/recruitment-proposals/{id}/accept|decline|withdraw,
    GET /api/v1/recruitment-proposals/sent                                   (Bearer 필수)
      → RecruitmentProposalController → RecruitmentProposalService
          → RecruitmentProposalRepository → MySQL recruitment_proposal (+ company·account JOIN)
          → ProposalStatusResolver (결정값 > 7일 만료 > 모집글 종료 > 대기, 저장하지 않음)
```

## 공통 규칙

- 모집글은 현재 공개된 공고 `(sourceCode, sourceProgramId)` 하나에 묶이며 등록 뒤 공고는 바꾸지 않습니다.
- 표시 상태 `status`는 읽을 때 계산합니다. `HIDDEN`(운영자 숨김) → `CLOSED`(작성자 조기 마감, 모집 마감일 경과,
  연결 공고 미공개 또는 접수 `CLOSED`) → 그 외 `OPEN`. 목록은 `OPEN`만, 작성 기업은 `/mine`에서 모든 상태를 봅니다.
- 제목·본문·제안 메시지에 이메일 주소나 한국 전화번호 형태가 있으면 `422 CONTACT_IN_TEXT`로 거부합니다. 담당자
  연락처는 제안이 수락된 뒤 시스템이 상대 기업에게만 공개합니다.
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
  "proposalCount": 3,
  "viewer": { "isOwner": false, "myProposalStatus": "PENDING" }
}
```

| 필드 | 설명 |
|---|---|
| `ourRole` | 작성 기업의 역할. `LEAD`(주관기관) / `PARTICIPANT`(참여기관) |
| `wantedRole` | 찾는 역할. `LEAD` / `PARTICIPANT` / `DEMAND`(수요처) |
| `program` | 연결 공고 요약. 공고가 더 이상 공개되지 않으면 `null`이며 그때 `status`는 `CLOSED` |
| `proposalCount` | 받은 제안 수(철회 제외) |
| `viewer.isOwner` | 요청 세션의 기업이 작성 기업인지 |
| `viewer.myProposalStatus` | 요청 세션의 기업이 이 글에 보낸 제안의 상태. 비로그인·미제안이면 `null` |

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

## 참여 제안

모든 제안 API는 `Authorization: Bearer` 세션이 필요합니다. 한 기업은 모집글 하나에 제안을 한 번만 보낼 수 있고,
철회·거절 뒤에도 다시 보낼 수 없습니다(`(post_id, company_id)` UNIQUE). 자기 기업의 글에는 보낼 수 없습니다.

| Method·Path | 누가 | 설명 |
|---|---|---|
| `POST /api/v1/recruitment-posts/{id}/proposals` | 다른 기업 | `{ "message": "1~500자" }`. `OPEN` 글에만. 성공 `201` |
| `GET /api/v1/recruitment-posts/{id}/proposals` | 작성 기업 | 받은 제안 전체(최근순). `{ items: [Proposal] }` |
| `POST /api/v1/recruitment-proposals/{id}/accept` · `/decline` | 작성 기업 | `PENDING` 제안만 수락·거절 |
| `POST /api/v1/recruitment-proposals/{id}/withdraw` | 제안 기업 | `PENDING` 제안만 철회 |
| `GET /api/v1/recruitment-proposals/sent` | 제안 기업 | 내 기업이 보낸 제안 전체(최근순). 대상 글이 종료돼도 남음 |

```json
{
  "id": 5,
  "postId": 12,
  "status": "ACCEPTED",
  "message": "공공 데이터 라벨링 운영 경험이 있는 참여기관입니다. 세부 비율은 협의하겠습니다.",
  "createdAt": "2026-09-06T12:00:00+09:00",
  "decidedAt": "2026-09-07T10:00:00+09:00",
  "company": { "businessNumber": "2208162517", "companyName": "비전솔루션", "businessStatus": "계속사업자" },
  "post": { "id": 12, "status": "OPEN", "title": "AI 실증 과제 데이터 구축·라벨링 참여기관 구합니다", "closesOn": "2026-09-20", "companyName": "데이터브릿지 주식회사" },
  "contactEmail": "partner@vision.co.kr"
}
```

| 필드 | 설명 |
|---|---|
| `status` | 저장값 `PENDING`/`ACCEPTED`/`DECLINED`/`WITHDRAWN`에 더해, 결정 없이 7일이 지난 제안은 `EXPIRED`, 모집글이 `OPEN`이 아니면 `CLOSED`로 계산 |
| `company` | 제안 기업 |
| `post` | 대상 모집글 요약. 전체는 모집글 상세 API |
| `contactEmail` | `ACCEPTED`일 때만 상대 담당자 이메일(작성 기업에게는 제안 담당자, 제안 기업에게는 작성 담당자). 그 외 `null` |

수락·거절·철회는 계산된 `status`가 `PENDING`일 때만 가능하며(만료·마감된 제안은 불가), 성공 시 바뀐 제안을 `200`으로 돌려줍니다.

### 제안 오류

| 상황 | HTTP | `code` |
|---|---:|---|
| `message` 검증 실패(빈 값, 500자 초과) | 400 | `REQUEST_VALIDATION_FAILED` |
| 자기 기업의 글에 제안 | 403 | `OWN_POST` |
| 작성 기업이 아닌데 받은 제안 조회·수락·거절 | 403 | `NOT_POST_OWNER` |
| 제안 기업이 아닌데 철회 | 403 | `NOT_PROPOSAL_OWNER` |
| 모집글 없음 | 404 | `RECRUITMENT_POST_NOT_FOUND` |
| 제안 없음 | 404 | `PROPOSAL_NOT_FOUND` |
| 같은 글에 이미 제안함 | 409 | `PROPOSAL_ALREADY_EXISTS` |
| 모집 중이 아닌 글에 제안 | 409 | `RECRUITMENT_POST_NOT_OPEN` |
| 이미 결정·만료·마감된 제안 | 409 | `PROPOSAL_NOT_PENDING` |
| 메시지에 연락처 | 422 | `CONTACT_IN_TEXT` |

## 어드민 모집글 제어

관리자 세션(`role = ADMIN`)이 필요하며, 로그인은 했지만 관리자가 아니면 `403 ADMIN_REQUIRED`입니다.

| Method·Path | 설명 |
|---|---|
| `GET /api/v1/admin/recruitment-posts?status=&page=&size=` | 모든 상태의 모집글, 최근 작성순. `status`는 `OPEN`/`CLOSED`/`HIDDEN`(생략 시 전체), `size` 1~100 |
| `POST /api/v1/admin/recruitment-posts/{id}/hide` | 숨김. body `{ "reason": "1~200자" }`. 사유는 모집글에 남음 |
| `POST /api/v1/admin/recruitment-posts/{id}/unhide` | 숨김 해제. body 없음 |
| `POST /api/v1/admin/recruitment-posts/{id}/close` | 강제 마감. body `{ "reason": "1~200자" }`. 사유는 서버 로그에만 |

```json
{
  "id": 12, "status": "HIDDEN",
  "title": "AI 실증 과제 데이터 구축·라벨링 참여기관 구합니다",
  "ourRole": "LEAD", "wantedRole": "PARTICIPANT", "closesOn": "2026-09-20",
  "closedEarlyAt": null, "hiddenAt": "2026-09-07T10:00:00+09:00", "hiddenReason": "연락처가 본문에 노출됨",
  "createdAt": "2026-09-06T12:00:00+09:00",
  "company": { "businessNumber": "1248100998", "companyName": "데이터브릿지 주식회사", "businessStatus": "계속사업자" },
  "program": { "sourceCode": "BIZINFO", "sourceProgramId": "PBLN_000000091203", "title": "…", "organization": "…", "status": "OPEN",
               "applicationPeriod": "…", "applicationEndDate": "2026-09-30", "targetDescription": "…", "sourceUrl": "…" },
  "proposalCount": 3
}
```

목록은 `{ items: [...], page, size, totalCount }`이고 조치는 바뀐 글 한 건을 `200`으로 돌려줍니다. 숨긴 글은 작성 기업 외에는
목록·상세에서 `404`이고 제안을 받지 않으며(`409 RECRUITMENT_POST_NOT_OPEN`), 작성 기업의 `/mine`에서는 `HIDDEN`으로 보입니다.
조치는 관리자 id·대상 id·사유와 함께 서버 INFO 로그에 남깁니다.

| 상황 | HTTP | `code` |
|---|---:|---|
| `reason` 검증 실패(빈 값, 200자 초과) | 400 | `REQUEST_VALIDATION_FAILED` |
| 관리자가 아님 | 403 | `ADMIN_REQUIRED` |
| 모집글 없음 | 404 | `RECRUITMENT_POST_NOT_FOUND` |
| 이미 숨긴 글을 다시 숨김 | 409 | `RECRUITMENT_POST_ALREADY_HIDDEN` |
| 숨기지 않은 글의 해제 | 409 | `RECRUITMENT_POST_NOT_HIDDEN` |
| 이미 종료·숨김된 글의 강제 마감 | 409 | `RECRUITMENT_POST_NOT_OPEN` |
