# 파트너 모집(컨소시엄) 설계와 단계별 작업 프롬프트

기준일: 2026-09-06. 전제: [계정·인증 계획](account-auth-plan.md)의 1~5단계(가입·로그인·세션·비로그인 제한)가
main에 병합된 상태. 화면 참고: 캔버스 `GovBiz 계정 화면`의 **모집 목록·모집글 작성·모집글 상세** 아트보드와
어드민 **회원·모집 운영** 아트보드(https://claude.ai/code/artifact/a14c6ab2-e4e5-4512-b430-1bea91262570).

이 문서는 세 부분입니다. §1~§5 설계(모델·규칙·계약·구조), §6 단계와 브랜치, §7 단계별 프롬프트.

---

## 1. 목표와 범위

- 로그인한 기업이 **공식 공고 하나에 묶인 모집글**을 올리고, 다른 기업이 **참여 제안**을 보내며, 수락되면 담당자
  연락처(이메일)가 서로 공개된다.
- 어드민은 **회원·기업 목록**과 **모집글 제어(숨김·강제 마감·사유 기록)**만 한다. 대시보드·수집 실행·품질 화면은
  이번 범위 밖이다.
- LLM 기능(공고 원문에서 컨소시엄 요건 발췌, 우리 기업과의 매칭 근거, 참여 요건 초안 작성)은 기본 흐름이 동작한
  뒤 별도 단계로 얹는다(§6 P7). 자동 매칭 확정·메시지 대리 발송은 하지 않는다.
- 이번에 하지 않는 것: 파트너 찾기(기업 프로필 검색), 프로필 완성도, 서류 상태 표시, 이메일 알림, 메시지함(수락
  후 대화), 신고 큐, 임시 저장·미리보기.

### 1.1 원칙 (시안 그대로)

1. 모집글은 반드시 현재 공개된 공고 `(sourceCode, sourceProgramId)` 하나에 묶인다. 공고가 마감되거나 사라지면
   모집도 종료로 본다.
2. 자격은 GovBiz가 보증하지 않는다. 공고 요건은 원문 발췌만 보여 준다.
3. 담당자 정보(이메일)는 제안이 수락된 뒤에만 상대에게 공개된다. 모집글·제안 본문에는 연락처를 적지 말라고
   안내하고, 이메일·전화번호 패턴은 저장 전에 거부한다.
4. 상태는 저장하지 않고 계산한다(AGENTS의 접수 상태 규칙과 같음). 모집 마감·공고 마감·제안 7일 만료는 읽을 때
   서울 기준 날짜로 판정하며 스케줄러를 두지 않는다.
5. 운영자 조치(숨김·강제 마감)는 사유가 남아야 하며, 모집글 내용을 운영자가 고쳐 쓰지 않는다.

## 2. 도메인 모델

```
Account(기존) ── 1:N ── RecruitmentPost ── 1:N ── RecruitmentProposal
   │                        │
Company(기존)          SupportProgram(기존, source_code + source_program_id)
```

### 2.1 RecruitmentPost (모집글)

| 필드 | 규칙 |
|---|---|
| `id` | BIGINT |
| `companyId`, `authorAccountId` | 작성 기업·담당자. 화면에는 기업명만 표시 |
| `sourceCode`, `sourceProgramId` | 연결 공고. 등록 시 현재 공개 공고이며 접수 상태가 `CLOSED`가 아니어야 함 |
| `title` | 1~80자 |
| `body` | 1~2,000자. 이메일·전화번호 패턴 거부 |
| `ourRole` | `LEAD`(주관기관) / `PARTICIPANT`(참여기관) |
| `wantedRole` | `LEAD` / `PARTICIPANT` / `DEMAND`(수요처) |
| `wantedCompanyCount` | 1~10 |
| `wantedRegion` | 0~60자 자유 텍스트(예: "서울·경기") |
| `requiredCapabilities` | 문자열 0~10개, 각 1~30자 (JSON) |
| `closesOn` | 모집 마감일. 오늘 이후이며 공고 `applicationEndDate`가 있으면 그 이하 |
| `closedEarlyAt` | 작성자가 조기 마감한 시각(null 가능) |
| `hiddenAt`, `hiddenReason` | 운영자 숨김(null 가능). 숨기면 작성자에게만 보임 |
| `createdAt`, `updatedAt` | |

**표시 상태(계산)**: `HIDDEN`(hiddenAt 있음) → `CLOSED`(closedEarlyAt 있음, 또는 오늘 > closesOn, 또는 연결 공고가
없거나 접수 상태 `CLOSED`) → 그 외 `OPEN`. 목록은 기본 `OPEN`만, 작성자는 자기 글의 모든 상태를 본다.

### 2.2 RecruitmentProposal (참여 제안)

| 필드 | 규칙 |
|---|---|
| `id`, `postId` | |
| `companyId`, `proposerAccountId` | 제안 기업·담당자 |
| `message` | 1~500자. 연락처 패턴 거부 |
| `decision` | `PENDING` / `ACCEPTED` / `DECLINED` / `WITHDRAWN` (저장값) |
| `decidedAt` | 수락·거절·철회 시각 |
| `createdAt` | |

**표시 상태(계산)**: 저장값이 `PENDING`이고 `createdAt + 7일 < 지금`이면 `EXPIRED`, 모집글이 `OPEN`이 아니면
`CLOSED`, 그 외 저장값 그대로. 한 기업은 한 모집글에 열린 제안(PENDING·ACCEPTED) 하나만 가진다(DB UNIQUE
+ 애플리케이션 검사). 자기 모집글에는 제안할 수 없다. `ACCEPTED`이면 양쪽 담당자 이메일이 서로에게 보인다.

### 2.3 Account.role (어드민)

`account.role` = `USER` / `ADMIN`. 관리자 지정은 SQL(`UPDATE account SET role='ADMIN' WHERE email=…`)로 하고
관리자 관리 화면은 두지 않는다. `GET /auth/me` 응답에 `role`을 추가해 화면이 운영 메뉴를 보여 줄지 정한다.

## 3. 스키마 (Flyway)

```sql
-- V6__add_account_role.sql
ALTER TABLE account
    ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER' AFTER password_hash,
    ADD CONSTRAINT chk_account_role CHECK (role IN ('USER', 'ADMIN'));

-- V7__create_recruitment_post.sql
CREATE TABLE recruitment_post (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id BIGINT UNSIGNED NOT NULL,
    author_account_id BIGINT UNSIGNED NOT NULL,
    source_code VARCHAR(64) NOT NULL,
    source_program_id VARCHAR(255) NOT NULL,
    title VARCHAR(80) NOT NULL,
    body TEXT NOT NULL,
    our_role VARCHAR(16) NOT NULL,
    wanted_role VARCHAR(16) NOT NULL,
    wanted_company_count TINYINT UNSIGNED NOT NULL,
    wanted_region VARCHAR(60) NOT NULL DEFAULT '',
    required_capabilities JSON NOT NULL,
    closes_on DATE NOT NULL,
    closed_early_at DATETIME(6) NULL,
    hidden_at DATETIME(6) NULL,
    hidden_reason VARCHAR(200) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_recruitment_post_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_recruitment_post_author FOREIGN KEY (author_account_id) REFERENCES account (id),
    CONSTRAINT fk_recruitment_post_program FOREIGN KEY (source_code, source_program_id)
        REFERENCES support_program (source_code, source_program_id),
    CONSTRAINT chk_recruitment_post_our_role CHECK (our_role IN ('LEAD', 'PARTICIPANT')),
    CONSTRAINT chk_recruitment_post_wanted_role CHECK (wanted_role IN ('LEAD', 'PARTICIPANT', 'DEMAND')),
    INDEX idx_recruitment_post_program (source_code, source_program_id),
    INDEX idx_recruitment_post_company (company_id),
    INDEX idx_recruitment_post_closes_on (closes_on)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- V8__create_recruitment_proposal.sql
CREATE TABLE recruitment_proposal (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    post_id BIGINT UNSIGNED NOT NULL,
    company_id BIGINT UNSIGNED NOT NULL,
    proposer_account_id BIGINT UNSIGNED NOT NULL,
    message VARCHAR(500) NOT NULL,
    decision VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    decided_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_recruitment_proposal_post FOREIGN KEY (post_id) REFERENCES recruitment_post (id) ON DELETE CASCADE,
    CONSTRAINT fk_recruitment_proposal_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_recruitment_proposal_proposer FOREIGN KEY (proposer_account_id) REFERENCES account (id),
    CONSTRAINT chk_recruitment_proposal_decision CHECK (decision IN ('PENDING', 'ACCEPTED', 'DECLINED', 'WITHDRAWN')),
    INDEX idx_recruitment_proposal_post (post_id),
    INDEX idx_recruitment_proposal_company (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

`recruitment_proposal`의 "기업당 열린 제안 하나" 규칙은 `(post_id, company_id)` UNIQUE로 강제하고, 철회·거절 뒤
다시 제안하려면 같은 행을 `PENDING`으로 되돌리는 대신 새 행을 허용할지는 P4에서 결정한다(초안: 재제안 불가,
UNIQUE 유지).

주의: `support_program`의 `(source_code, source_program_id)`를 FK로 참조하므로 동기화가 사라진 공고를 삭제하지
않고 `is_source_present=false`로만 바꾸는 현재 규칙이 전제다.

## 4. 공개 HTTP 계약 (`docs/partner-recruitment-contract.md`로 정리)

모든 쓰기 API는 `Authorization: Bearer` 필수(401 `AUTHENTICATION_REQUIRED`). 목록·상세 조회는 로그인 없이도
허용하되 제안 현황·연락처는 로그인·권한에 따라 가린다. 오류는 기존 ProblemDetail 형식.

### 4.1 모집글

| Method·Path | 인증 | 설명 | 실패 |
|---|---|---|---|
| `GET /api/v1/recruitment-posts?status=OPEN&sourceCode=&sourceProgramId=&page=&size=` | 선택 | 목록. 기본 `OPEN`, 마감 임박순. 공고 식별자로 필터 가능 | 400 |
| `GET /api/v1/recruitment-posts/{id}` | 선택 | 상세 + 연결 공고 요약(제목·기관·접수 상태·마감·원문 URL) + 제안 수. 작성자면 자기 글 상태 무관하게 조회 | 404 `RECRUITMENT_POST_NOT_FOUND` |
| `POST /api/v1/recruitment-posts` | 필수 | 등록. 공고 존재·접수 상태·`closesOn` 검증 | 400, 422 `SUPPORT_PROGRAM_NOT_OPEN`, 422 `CONTACT_IN_TEXT` |
| `PUT /api/v1/recruitment-posts/{id}` | 작성 기업 | 수정(OPEN일 때만) | 403 `NOT_POST_OWNER`, 409 `RECRUITMENT_POST_NOT_OPEN` |
| `POST /api/v1/recruitment-posts/{id}/close` | 작성 기업 | 조기 마감 | 403, 409 |
| `GET /api/v1/recruitment-posts/mine` | 필수 | 내 기업이 쓴 모집글(모든 상태) + 받은 제안 수 | |

### 4.2 제안

| Method·Path | 인증 | 설명 | 실패 |
|---|---|---|---|
| `POST /api/v1/recruitment-posts/{id}/proposals` | 필수 | 제안 보내기(`message`). 자기 글·중복·마감 글 거부 | 409 `PROPOSAL_ALREADY_EXISTS`, 409 `RECRUITMENT_POST_NOT_OPEN`, 422 `CONTACT_IN_TEXT`, 403 `OWN_POST` |
| `GET /api/v1/recruitment-posts/{id}/proposals` | 작성 기업 | 받은 제안 목록(기업명·메시지·상태·수락 시 이메일) | 403 |
| `POST /api/v1/recruitment-proposals/{id}/accept` · `/decline` | 작성 기업 | 수락·거절(PENDING만) | 403, 409 `PROPOSAL_NOT_PENDING` |
| `POST /api/v1/recruitment-proposals/{id}/withdraw` | 제안 기업 | 철회(PENDING만) | 403, 409 |
| `GET /api/v1/recruitment-proposals/sent` | 필수 | 내 기업이 보낸 제안 + 상태 + 수락 시 상대 이메일 | |

응답 예 (`GET /recruitment-posts/{id}`):

```json
{
  "id": 12,
  "status": "OPEN",
  "title": "AI 실증 과제 데이터 구축·라벨링 참여기관 구합니다",
  "body": "…",
  "ourRole": "LEAD", "wantedRole": "PARTICIPANT", "wantedCompanyCount": 1,
  "wantedRegion": "서울·경기·인천", "requiredCapabilities": ["데이터 구축", "라벨링 운영"],
  "closesOn": "2026-09-20", "createdAt": "2026-09-06T12:00:00+09:00",
  "company": { "businessNumber": "1248100998", "companyName": "데이터브릿지 주식회사", "businessStatus": "계속사업자" },
  "program": { "sourceCode": "BIZINFO", "sourceProgramId": "PBLN_000000091203", "title": "…", "organization": "…",
               "status": "OPEN", "applicationEndDate": "2026-09-30", "applicationPeriod": "…", "sourceUrl": "…" },
  "proposalCount": 3,
  "viewer": { "isOwner": false, "myProposalStatus": null }
}
```

### 4.3 어드민

| Method·Path | 인증 | 설명 |
|---|---|---|
| `GET /api/v1/admin/accounts?email=&page=&size=` | ADMIN | 회원 목록: 이메일, role, 기업명, 사업자번호, 사업자 상태, 가입일, 모집글 수 |
| `POST /api/v1/admin/accounts/{id}/sessions/revoke` | ADMIN | 해당 계정 세션 전부 삭제(즉시 로그아웃) |
| `GET /api/v1/admin/recruitment-posts?status=&page=&size=` | ADMIN | 모집글 목록(모든 상태), 작성 기업, 제안 수, 숨김 사유 |
| `POST /api/v1/admin/recruitment-posts/{id}/hide` · `/unhide` | ADMIN | 숨김·해제. `reason` 1~200자 필수(숨김) |
| `POST /api/v1/admin/recruitment-posts/{id}/close` | ADMIN | 강제 마감. `reason` 필수 |

관리자 조치는 `recruitment_post.hidden_reason`과 서버 로그(INFO, 관리자 계정 id·대상 id·사유)에 남긴다.
별도 감사 로그 테이블은 두지 않는다. 비관리자는 403 `ADMIN_REQUIRED`.

## 5. 코드 구조

### 5.1 Core API

```
ai.govbiz.core.recruitment
├── controller/            RecruitmentPostController, RecruitmentProposalController
│   └── dto/               *Request, *Response
├── service/               RecruitmentPostService, RecruitmentProposalService
│   ├── dto/               RecruitmentPostResult, RecruitmentProposalResult
│   └── exception/         RecruitmentPostNotFoundException, NotPostOwnerException, RecruitmentPostNotOpenException,
│                          SupportProgramNotOpenException, ProposalAlreadyExistsException, ProposalNotPendingException,
│                          ContactInTextException, OwnPostProposalException
├── domain/                RecruitmentPost, RecruitmentPostStatusResolver, RecruitmentProposal, ProposalStatusResolver,
│                          ContactPatternPolicy(이메일·전화 패턴)
└── repository/            RecruitmentPostRepository, RecruitmentProposalRepository
    └── mapper/            RecruitmentPostMapper(+DbRow), RecruitmentProposalMapper(+DbRow)

ai.govbiz.core.account (기존 확장)
├── service/               AccountSessionService.requireAdmin(authorization)  ← role 검사
├── admin/                 AdminAccountController, AdminRecruitmentPostController, AdminAccountService,
│                          AdminRecruitmentPostService   (어드민 공개 계약은 account 기능 아래 admin 하위 디렉터리)
```

- 보호 endpoint가 셋을 넘으므로 P1에서 Controller의 `Account` 파라미터를 채우는 `HandlerMethodArgumentResolver`를
  `account/web`에 두었다(아직 account 기능만 쓰므로 `_common`이 아님). `Account?`(nullable)이면 헤더가 없을 때 null을
  넣어 비로그인 조회를 허용한다. Resolver는 `AccountSessionService.requireAccount`를 호출할 뿐 새 추상화를 만들지 않는다.
- 공고 연결 검증은 `SupportProgramRepository.findPresentBySourceAndProgramId`를 그대로 사용한다(Service → 다른
  기능의 Repository 읽기 허용, 쓰기는 하지 않음).
- 상태 계산은 Domain의 `RecruitmentPostStatusResolver(post, program, today)`,
  `ProposalStatusResolver(proposal, postStatus, now)`가 맡고 DB에는 저장하지 않는다.
- SQL은 `mybatis/recruitment/repository/*.xml`. 목록 조회는 공고·기업 JOIN 한 번으로 DbRow에 평탄화한다.

### 5.2 Frontend

```
domain/entities/RecruitmentPost.ts, RecruitmentProposal.ts
domain/repositories/RecruitmentRepository.ts     (목록·상세·등록·수정·마감·제안 보내기·받은/보낸 제안·수락·거절·철회)
domain/repositories/AdminRepository.ts           (회원 목록·세션 종료·모집글 목록·숨김·해제·마감)
domain/usecases/                                 기능당 UseCase 1개(총 9 + 어드민 5)
data/models/RecruitmentDto.ts, AdminDto.ts       Zod
data/api/recruitmentApi.ts, adminApi.ts          Bearer 헤더는 sessionTokenStorage에서 읽음(AccountRepositoryImpl과 같은 방식)
presentation/features/recruitment/
  state/recruitmentSlice.ts (목록 필터·내 활동 요약만; 상세·폼은 로컬 state)
  validation/recruitmentPostFormSchema.ts, proposalFormSchema.ts
  viewmodel/useRecruitmentPostListViewModel, useRecruitmentPostDetailViewModel, useRecruitmentPostFormViewModel,
            useReceivedProposalsViewModel, useSentProposalsViewModel
  view/RecruitmentPostListPage, RecruitmentPostDetailPage, RecruitmentPostFormPage, MyRecruitmentPage, *.styles.ts
presentation/features/admin/
  viewmodel/useAdminAccountsViewModel, useAdminRecruitmentPostsViewModel
  view/AdminAccountsPage, AdminRecruitmentPostsPage, AdminShell(사이드바에 회원·모집글 2메뉴)
```

경로: `/partners`(목록), `/partners/new`, `/partners/:id`, `/partners/mine`, `/admin/accounts`, `/admin/recruitment-posts`.
채팅 사이드바에 "파트너 모집" 링크, 관리자면 "운영" 링크. 비로그인은 목록·상세까지만 보고 작성·제안은 로그인
화면으로 보낸다(돌아올 경로 유지).

## 6. 단계와 브랜치

| 단계 | 브랜치 | 내용 | 검증 |
|---|---|---|---|
| P1 | `feature/account-role-admin-members` | V6 role, `/me`에 role, 인증 ArgumentResolver, `requireAdmin`, 어드민 회원 목록·세션 종료 API + 어드민 셸·회원 화면 | Core 전체 테스트 + 통합, 프런트 테스트 |
| P2 | `feature/recruitment-post-api` | V7, Domain·상태 계산, Repository, 모집글 CRUD·조기 마감·내 글 API, 계약 문서 | 통합 테스트(공고 FK·상태 계산·권한) |
| P3 | `feature/recruitment-post-web` | 목록·상세·작성/수정·내 모집글 화면, 사이드바 링크 | 프런트 테스트, 브라우저 스모크 |
| P4 | `feature/recruitment-proposal-api` | V8, 제안 보내기·목록·수락·거절·철회, 연락처 공개 규칙, 7일 만료 계산 | 통합 테스트(UNIQUE·상태 전이·공개 규칙) |
| P5 | `feature/recruitment-proposal-web` | 상세의 제안 폼·상태, 받은 제안 관리, 보낸 제안, 내 활동 요약 | 프런트 테스트, 브라우저 스모크(두 계정) |
| P6 | `feature/admin-recruitment-control` | 어드민 모집글 목록·숨김·해제·강제 마감 API + 화면, 사유 기록 | 통합·프런트 테스트 |
| P7 | (후속) `feature/recruitment-llm-*` | 공고 원문 컨소시엄 요건 발췌(기능 85), 매칭 근거 설명, 참여 요건 초안 — AI Service 슬라이스 신설 | 별도 설계 |

공통 규칙: 커밋은 기능 단위 한 줄 한국어, push는 하지 않음, 각 브랜치는 이전 브랜치 위에서 시작, Core는
`./gradlew clean test --no-daemon`(Docker 필요), 프런트는 `pnpm lint && pnpm test && pnpm build`, 문서(계약·아키텍처·
구현 현황·README)를 같은 커밋에 갱신, 새 의존성 없음(예정).

## 7. 단계별 프롬프트

### 프롬프트 P1 — 계정 역할과 어드민 회원 목록 (`feature/account-role-admin-members`)

```
GovBiz에 관리자 역할과 어드민 회원·기업 목록을 만든다. docs/partner-recruitment-plan.md §2.3·§4.3·§5를 따르고
AGENTS.md와 기존 account 기능(Controller → Service → Repository → MyBatis XML)의 모양을 그대로 유지한다.

브랜치: 계정 5단계가 병합된 main(또는 feature/anonymous-search-limit) 위에서 git switch -c feature/account-role-admin-members.

Core API:
1. V6__add_account_role.sql(§3). AccountDbRow·Account domain에 role(enum AccountRole USER/ADMIN) 추가, AccountMapper.xml
   조회 컬럼에 role 포함. 가입은 항상 USER.
2. _common/web/AuthenticatedAccountArgumentResolver: Controller 메서드 파라미터 타입 AuthenticatedAccount(accountId, email,
   role, companyId)를 Authorization 헤더로 채운다. AccountSessionService.requireAccount를 호출하며 실패는 기존
   AuthenticationRequiredException. WebMvcConfigurer(기존 WebCorsConfig 옆 _common/config)에 등록. /me·/logout을 이 파라미터로 바꾼다.
3. AccountSessionService.requireAdmin(account): role이 ADMIN이 아니면 AdminRequiredException → 403 ADMIN_REQUIRED
   (ApiExceptionHandler 추가).
4. account/admin: AdminAccountController(GET /api/v1/admin/accounts?email=&page=&size=, POST /{id}/sessions/revoke),
   AdminAccountService, AccountRepository에 findPage(emailKeyword, offset, limit)·countByEmail·deleteSessionsByAccountId 추가.
   페이지 응답: { items:[{id,email,role,company{…},createdAt}], page, size, totalCount }. size 최대 100.
5. /me 응답 AccountResponse에 role 추가. docs/account-auth-contract.md·README·architecture·implementation-status 갱신.
6. 테스트: Resolver 단위, requireAdmin 단위, AdminAccountControllerTest(403·목록·revoke), AccountRepositoryIntegrationTest에
   role 저장·페이지 조회·세션 일괄 삭제 추가, 기존 AccountAuthFlowIntegrationTest에 role='USER' 확인.

Frontend:
1. AccountDto·Account에 role, authSlice selectIsAdmin.
2. features/admin: AdminShell(사이드바: 회원·기업 / 모집글(비활성, P6에서 활성)), AdminAccountsPage(이메일 검색, 표, 세션 종료
   버튼 + confirm), useAdminAccountsViewModel, AdminRepository·adminApi·AdminDto·UseCase 2개, DI 등록.
   /admin/* 라우트는 isAdmin이 아니면 /로 보낸다. 채팅 사이드바에 isAdmin일 때만 "운영" 링크.
3. 테스트: api·usecase·viewmodel·App 시나리오(관리자 로그인 → 운영 링크 → 회원 목록 → 세션 종료, 비관리자 접근 차단).
4. frontend/README 경로 표·구조, implementation-status 갱신.

검증·마무리: 전체 테스트 통과, 실제 MySQL로 관리자 계정을 SQL로 지정해 브라우저 확인, 커밋 "관리자 역할과 어드민 회원 목록 추가".
하지 말 것: 관리자 관리 UI, 감사 로그 테이블, 대시보드.
```

### 프롬프트 P2 — 모집글 API (`feature/recruitment-post-api`)

```
GovBiz Core API에 파트너 모집글 기능을 만든다. docs/partner-recruitment-plan.md §2.1·§3(V7)·§4.1·§5.1을 따른다.
P1의 AuthenticatedAccount 파라미터를 사용한다.

1. V7__create_recruitment_post.sql. recruitment/domain: RecruitmentPost, RecruitmentRole(LEAD/PARTICIPANT/DEMAND),
   RecruitmentPostStatus(OPEN/CLOSED/HIDDEN), RecruitmentPostStatusResolver(post, linkedProgram: SupportProgram?, today),
   ContactPatternPolicy.containsContact(text): 이메일(RFC 간이)·한국 전화번호(01x-xxxx-xxxx, 02-…, 0xx-…) 패턴.
2. repository: RecruitmentPostRepository(insert, update, closeEarly, findById(공고·기업 JOIN 평탄화), findOpenPage(sourceCode?,
   sourceProgramId?, today, offset, limit)+count, findByCompany(companyId)), mapper·XML. JSON 배열은 계정 Repository처럼 ObjectMapper로.
3. service: RecruitmentPostService.create(account, command) — 공고 존재(SupportProgramRepository.findPresentBySourceAndProgramId)
   ·접수 상태≠CLOSED·closesOn≤applicationEndDate(있을 때)·연락처 패턴 검증 → 저장. update/closeEarly는 소유 기업 확인·OPEN 확인.
   get(id, viewer?): HIDDEN·CLOSED 글은 작성 기업만 조회, 그 외 404. list, listMine. 결과는 service/dto Result에 계산된 status 포함.
4. controller: §4.1 5개 endpoint + /mine. Request 검증은 Bean Validation(제목 80, 본문 2000, 역량 10개·30자, count 1~10, region 60).
   Response에 program 요약(SupportProgramResponse 재사용 가능하면 재사용, 아니면 필요한 6필드만 담은 LinkedProgramResponse).
5. ApiExceptionHandler에 §4.1 오류 코드 추가. docs/partner-recruitment-contract.md 신규, architecture(흐름·상태 계산)·README·
   implementation-status 갱신, mybatis.mapper-locations는 글롭이라 변경 없음.
6. 테스트: StatusResolver 단위(공고 마감·조기 마감·숨김 우선순위·날짜 경계), ContactPatternPolicy 단위, Service 단위(Mockito,
   AccountTestHelper.anyValue 방식), Controller 계약(401/403/404/409/422), Repository 통합(FK로 없는 공고 거부, JSON 역량,
   목록 정렬 마감 임박순, 작성 기업 필터), 흐름 통합(가입 2계정 → 등록 → 타 계정 수정 403 → 조기 마감 → 목록에서 사라짐).
커밋 "파트너 모집글 API 추가". 하지 말 것: 제안·어드민·LLM.
```

### 프롬프트 P3 — 모집글 화면 (`feature/recruitment-post-web`)

```
GovBiz Frontend에 파트너 모집글 목록·상세·작성·내 모집글 화면을 만든다. docs/partner-recruitment-plan.md §5.2와 캔버스의
모집 목록·작성·상세 아트보드를 따르되, 이번 범위 밖 요소(파트너 찾기 탭, 프로필 일치 수, 추천 모집, 제안 폼·현황, 관심 공고함,
서류 상태, 임시 저장·미리보기, 숨기기·신고, LLM 초안 버튼)는 넣지 않는다.

1. domain/data/DI: RecruitmentRepository(목록·상세·등록·수정·조기 마감·내 글), Zod DTO, recruitmentApi(Bearer는 sessionTokenStorage에서),
   UseCase 6개.
2. 화면: /partners 목록(상태 OPEN, 카드: 연결 공고+D-day, 제목, 기업명, 찾는 역할·지역·역량, 페이지), /partners/:id 상세(조건 6칸,
   연결 공고 카드 + 원문 링크 + 상세 화면 링크, 본문, 작성 기업이면 수정·조기 마감 버튼), /partners/new·/partners/:id/edit 작성 폼
   (공고 검색: 기존 검색 API로 공고명 검색 후 선택 — 관심 공고함 없음, 역할·조건·마감일·제목·본문, Zod 검증은 서버 규칙과 동일,
   연락처 패턴 즉시 안내), /partners/mine(내 글·상태). 비로그인이 작성·수정에 들어오면 /login으로 보내고 로그인 후 복귀.
3. 채팅 사이드바에 "파트너 모집" 링크, 공고 상세 화면에 "이 공고의 파트너 모집 N건" 링크(목록의 공고 필터 사용).
4. 테스트: api·usecase·viewmodel·App 시나리오(목록 → 상세, 작성 → 등록 → 내 글, 비로그인 작성 진입 → 로그인 → 복귀, 422 안내).
5. 문서: frontend/README 경로·구조, implementation-status.
검증·브라우저 스모크 후 커밋 "파트너 모집글 화면 추가".
```

### 프롬프트 P4 — 참여 제안 API (`feature/recruitment-proposal-api`)

```
GovBiz Core API에 참여 제안 기능을 만든다. docs/partner-recruitment-plan.md §2.2·§3(V8)·§4.2를 따른다.

1. V8__create_recruitment_proposal.sql + UNIQUE (post_id, company_id). domain: RecruitmentProposal, ProposalDecision, ProposalStatus
   (PENDING/ACCEPTED/DECLINED/WITHDRAWN/EXPIRED/CLOSED), ProposalStatusResolver(proposal, postStatus, now, expiryDays=7).
2. repository: insert, findByPost(postId), findBySentCompany(companyId), findById, updateDecision(id, decision, decidedAt, expectedCurrent='PENDING')
   → 영향 행 0이면 PROPOSAL_NOT_PENDING.
3. service: send(account, postId, message): 글 OPEN, 자기 기업 글 금지(OWN_POST), 연락처 패턴, DuplicateKeyException → PROPOSAL_ALREADY_EXISTS.
   listReceived(account, postId): 소유 기업만. accept/decline: 소유 기업만, PENDING만. withdraw: 제안 기업만.
   listSent(account). 연락처 공개: 상태 ACCEPTED일 때만 상대 담당자 email을 Result에 채우고 그 외 null.
4. controller §4.2, ApiExceptionHandler 오류 코드, 계약 문서·architecture·README·implementation-status 갱신.
5. 테스트: Resolver 단위(7일 경계·글 마감 시 CLOSED), Service 단위, Controller 계약, Repository 통합(UNIQUE·조건부 updateDecision),
   흐름 통합(계정 A 글 → 계정 B 제안 → B 재제안 409 → A 수락 → 양쪽 목록에 이메일 노출 → B 철회 409(PENDING 아님)).
커밋 "파트너 모집 참여 제안 API 추가".
```

### 프롬프트 P5 — 참여 제안 화면 (`feature/recruitment-proposal-web`)

```
GovBiz Frontend에 참여 제안 흐름을 붙인다. §5.2를 따른다.
1. RecruitmentRepository에 제안 5개 메서드·UseCase 추가.
2. 상세 화면 오른쪽: 로그인·타 기업이면 제안 폼(500자·연락처 안내), 이미 보낸 제안이면 상태 표시·철회, 작성 기업이면 "받은 제안 N건" 링크.
   /partners/:id/proposals(작성 기업: 제안 카드 + 수락/거절 + 수락 시 이메일), /partners/mine에 보낸 제안 탭(상태·수락 시 상대 이메일).
   상태 문구: 대기 / 수락 / 거절 / 철회 / 7일 무응답 종료 / 모집 종료.
3. 테스트: viewmodel·App 시나리오(제안 → 대기 표시 → 상대 계정 수락 → 이메일 표시), 409 안내.
4. 문서 갱신. 브라우저 스모크(계정 2개). 커밋 "파트너 모집 참여 제안 화면 추가".
```

### 프롬프트 P6 — 어드민 모집글 제어 (`feature/admin-recruitment-control`)

```
어드민에 모집글 목록·숨김·해제·강제 마감을 만든다. §4.3의 recruitment-posts 3개 endpoint와 §5.
1. Core: account/admin/AdminRecruitmentPostController·Service, RecruitmentPostRepository에 findAllPage(status?, offset, limit)·hide(id, reason, at)
   ·unhide·closeByAdmin 추가(모두 조건부 갱신). 조치는 INFO 로그(관리자 id, 대상 id, 사유). 숨긴 글은 목록·상세에서 작성 기업 외 404.
2. Frontend: AdminRecruitmentPostsPage(상태 필터, 표: 제목·기업·공고·상태·제안 수·숨김 사유, 숨김/해제/마감 버튼 + 사유 입력 다이얼로그),
   AdminShell 메뉴 활성화, UseCase 4개.
3. 테스트: Controller 403·사유 필수 400, Repository 통합(조건부 갱신), App 시나리오(관리자 숨김 → 일반 사용자 목록에서 사라짐).
4. 문서 갱신. 커밋 "어드민 모집글 숨김·마감 제어 추가".
```

### P7(후속) — LLM 보조

기본 흐름이 돌아간 뒤 별도 설계한다. 후보: (a) 작성 화면의 "공고 원문에서 컨소시엄 요건 발췌"(기능 85, 기존
`support_program_evidence` 원문 청크 재사용, 새 Agent 1개), (b) 상세 화면의 "우리 기업과의 매칭 근거"(프로필 필드가 생긴
뒤), (c) 참여 요건 초안. 각각 원문 인용 필수·확정 어조 금지·자동 확정 없음 원칙을 유지한다.

## 8. 남는 결정 사항

1. 재제안 허용 여부(§3): 초안은 불가(UNIQUE).
2. 비로그인 목록·상세 공개 여부: 초안은 공개(기업명까지), 제안·연락처는 로그인 필요.
3. 모집글 삭제: 작성자 삭제는 두지 않고 조기 마감으로 대신(제안 이력 보존). 필요하면 P6에서 어드민 삭제 검토.
4. 알림(이메일): 범위 밖. 받은 제안은 내 모집글 화면에서만 확인.
