# GovBiz Web

지원사업 검색 결과를 채팅 형태로 보여 주고 공고의 상세 조건과 원문을 연결하는 웹 화면입니다.
React·TypeScript·Vite·Tailwind CSS를 사용합니다. 전체 기술 구성은
[프로젝트 기술 문서](../docs/technology.md), 기능별 완료·미구현 범위는
[구현 현황](../docs/implementation-status.md)을 참고하세요.

## 실행

### Docker Compose

저장소 루트의 `.env`와 API 키를 먼저 준비합니다. 상세 설정은
[Compose 실행 안내](../infrastructure/README.md#실행)에 있습니다.

```bash
docker compose --env-file .env --file infrastructure/compose.yaml up --build
```

브라우저에서 `http://127.0.0.1:5173`에 접속합니다. React는 `/api` 상대 주소로 요청하고,
Vite 개발 서버가 `http://core-api:8080`으로 중계합니다.

### 네이티브 개발

Node.js `24.x`, pnpm `11.22.x`가 필요합니다. Core API와 검색에 필요한 MySQL·AI Service·Qdrant는
[Core API 실행 안내](../backend/core-api/README.md)에 따라 먼저 실행합니다.

```bash
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

기본 API 주소는 `http://localhost:8080`입니다. 다른 주소를 사용하려면 `frontend/.env.example`을
`frontend/.env.local`로 복사하고 `VITE_CORE_API_BASE_URL`을 변경합니다. `VITE_` 값은 브라우저에
노출되므로 비밀값을 넣지 않습니다. API를 다른 origin으로 직접 호출할 때는 Core API의 CORS 설정도
프론트엔드 접속 주소와 맞춰야 합니다.

`VITE_CORE_API_BASE_URL=/`이면 Vite의 `/api` 프록시를 사용합니다. 프록시 목적지는
`VITE_DEV_PROXY_TARGET`이며 기본값은 `http://localhost:8080`입니다. 이 프록시는 개발 서버 설정으로,
정적 빌드 배포 시에는 별도의 `/api` 라우팅과 SPA 경로 처리가 필요합니다.
배포용 빌드에서는 `VITE_CORE_API_BASE_URL`을 반드시 지정합니다. 같은 origin에서 API를 제공한다면
`/`, 별도 서버라면 브라우저가 접근할 수 있는 공개 API 주소를 사용합니다.

## 화면과 현재 동작

| 경로 | 기능 |
|---|---|
| `/` | 자연어 검색, 결과 카드, 새 대화 시작 |
| `/support-programs/detail?sourceCode=...&sourceProgramId=...` | 식별자로 상세 API를 조회해 공고 조건·출처 표시 |
| `/signup` | 사업자등록번호 기업 확인 → 이메일·비밀번호로 간편 회원가입 |
| `/login` | 이메일·비밀번호 로그인 |
| `/partners` | 파트너 모집글 목록(모집 중, 마감 임박순). `?sourceCode=&sourceProgramId=`로 공고별 필터 |
| `/partners/:postId` | 모집글 상세, 연결 공고·작성 기업. 작성 기업이면 수정·조기 마감·받은 제안 링크, 다른 기업이면 참여 제안 폼·내 제안 상태·철회 |
| `/partners/:postId/proposals` | 로그인 필요. 작성 기업이 받은 제안을 보고 수락·거절(수락 시 상대 담당자 이메일) |
| `/partners/new`, `/partners/:postId/edit` | 로그인 필요. 공고 검색·선택 후 모집 조건·소개 작성, 수정 |
| `/partners/mine` | 로그인 필요. 내 기업의 모집글(종료·숨김 포함). `?tab=sent`는 보낸 제안(상태·철회·수락 시 상대 이메일) |
| `/admin/accounts` | 관리자 전용 운영 콘솔: 회원·기업 목록, 이메일 검색, 세션 강제 종료 |
| `/examples/sample-item/hook` | React Hook Form·로컬 요청 상태 예제 |
| `/examples/sample-item/redux` | Redux 상태 유지 예제 |

현재 채팅 화면은 `acceptingOnly=true`로 검색합니다. API가 지원하는 빈 검색어 최신 목록 조회와
접수 상태 전체 조회는 이 화면에 별도 조작 UI로 연결되어 있지 않습니다. 검색 제안은 입력창을
채우고, 사용자가 제출하면 요청을 보냅니다.

검색 결과에는 추천 점수·추천 이유·신청 기간·원문 링크가 표시됩니다. 상세 화면은 검색 결과의
메모리 상태에 의존하지 않고 URL의 `sourceCode`·`sourceProgramId`로 다시 조회하므로 새로고침과
직접 접속을 지원합니다. 상세 API는 검색별 점수·추천 이유를 제공하지 않으며, 공고 정보와 현재
접수 상태를 보여 줍니다. 잘못된 주소, 없는 공고, 조회 실패, 로딩 상태를 구분합니다.
원문 링크는 제공처 코드별 공식 도메인 allowlist와 `http(s)` 스킴을 함께 검증합니다. 현재
`BIZINFO`의 기업마당 도메인만 허용합니다. 실제 제공처를 추가할 때는 해당 제공처의 공식 도메인을
allowlist에 명시적으로 추가합니다. 테스트용 제공처는 production 허용 목록에 포함하지 않습니다.

회원가입은 사업자등록번호를 Core API의 Bizno 확인 endpoint로 검증한 뒤 이메일·비밀번호만 받습니다.
가입·로그인 응답의 세션 토큰은 `localStorage`(`govbiz.sessionToken`)에 저장하고 앱 진입 시 `GET /api/v1/auth/me`로
로그인 상태를 복원합니다. 채팅 헤더는 로그인 전에는 `로그인` 링크, 로그인 후에는 회사명과 `로그아웃`을
보여 줍니다. 계정의 `role`이 `ADMIN`이면 사이드바에 운영 콘솔 링크가 나타나며, 운영 화면은 관리자가 아니면
홈으로 보냅니다(실제 권한은 서버가 다시 확인). 검색은 로그인 없이도 동작하지만, 비로그인 검색은 브라우저에 기록한 횟수 기준 3회까지이며
그 뒤에는 가입·로그인 안내 모달을 띄웁니다. 이 제한은 안내 목적이며 서버는 검색을 제한하지 않습니다.

파트너 모집글은 공식 공고 하나에 묶어 작성합니다. 작성 화면은 기존 검색 API로 공고를 찾아 선택하고(공고 상세에서
들어오면 미리 선택), 모집 조건·소개를 Zod로 검사한 뒤 등록합니다. 목록·상세는 로그인 없이 볼 수 있고 작성·수정·
조기 마감·내 글은 로그인이 필요합니다. 로그인이 필요한 화면은 로그인 뒤 원래 경로로 돌아옵니다. 제목·소개의
이메일·전화번호는 서버와 같은 규칙으로 즉시 거부합니다.

참여 제안은 모집글 상세의 오른쪽 영역에서 보냅니다(500자, 연락처 금지, 기업당 한 번). 이미 보낸 제안이 있으면 보낸 제안
목록에서 찾아 상태(대기 중·수락됨·거절됨·철회함·7일 무응답 종료·모집 종료)와 대기 중 철회를 보여 주고, 보내거나 철회한
뒤에는 상세를 조용히 다시 읽어 제안 수·내 제안 상태를 맞춥니다. 작성 기업은 받은 제안 화면에서 수락·거절하며, 수락된
제안에서만 서로의 담당자 이메일이 보입니다. 서버의 409(중복·대기 아님)는 안내 문구로 바꿉니다.

채팅 형태의 화면이지만 각 검색 요청에는 현재 입력한 검색어만 전달합니다. 이전 대화를 이해하는
다중 턴 대화, 기업 프로필 편집, 북마크, 알림, 대화 이력의 서버 저장은 아직 구현하지 않았습니다.

## 구조와 상태 책임

```text
src/
├── app/                         # Redux Store, typed hook, Awilix 조립·등록
├── presentation/features/chat/ # 검색·상세 View, ViewModel, chat slice
├── presentation/features/auth/ # 회원가입·로그인 View, ViewModel, 폼 검증, auth slice
├── presentation/features/admin/ # 운영 콘솔 셸·회원 목록 View, ViewModel (관리자 role만 진입)
├── presentation/features/recruitment/ # 파트너 모집글 목록·상세·작성, 참여 제안 보내기·받은/보낸 제안 View, ViewModel, 폼 검증
├── presentation/features/sample-item/ # 상태관리 비교 예제
├── presentation/shared/        # Core API 상태 표시
├── domain/                      # Entity, Repository 계약, UseCase
└── data/                        # Fetch, Zod DTO 검증, Repository 구현, 세션 토큰 저장소, 테스트 fixture
```

검색은 `View → ViewModel → UseCase → Repository → Fetch → Core API` 순서입니다. ViewModel은
전역 `appContainer`에서 UseCase를 조회하고, ViewModel 내부 Thunk가 Redux의 요청·성공·실패 상태를
변경합니다. Awilix 등록은 `app/di`에 있으며 Domain은 컨테이너를 알지 못합니다. 상세 조회는 같은
UseCase·Repository 경계를 거치되 로딩·결과 상태를 ViewModel의 로컬 state에 둡니다.

| 소유자 | 현재 담당 상태 | 화면 이동·새로고침 동작 |
|---|---|---|
| React 로컬 상태 | 사이드바, 상세 조회, Health, Hook SampleItem, 가입·로그인 폼과 기업 확인 결과, 모집글 목록·상세·폼, 제안 폼·받은/보낸 제안 | 해당 화면이 unmount되면 초기화 |
| URL 검색 매개변수 | 모집글 목록의 공고 필터·페이지, 작성 화면의 미리 선택한 공고, 내 활동의 탭 | 새로고침·공유 후 유지 |
| Redux 메모리 | 채팅 메시지·입력·검색 상태, 로그인 상태·계정, Redux SampleItem | 앱 내 이동 시 유지, 새로고침 시 초기화(로그인 상태는 저장된 토큰으로 복원) |
| 브라우저 localStorage | 세션 토큰, 비로그인 검색 횟수 | 새로고침·재접속 후 유지, 로그아웃·만료 시 토큰 삭제, 로그인 시 횟수 초기화 |
| 서버 | MySQL 공고 카탈로그, 계정·기업·세션 | 브라우저 상태와 별개로 유지 |

Redux에는 직렬화 가능한 데이터만 저장하며 `AbortController`는 ViewModel의 `useRef`가 관리합니다.
새 대화 시작·화면 이탈 시 요청을 취소하고, `requestId`가 다른 과거 응답은 무시합니다. 새 대화
시작은 현재 메시지를 초기화하며 이전 대화 목록을 보관하지 않습니다.

입력과 요청에는 다음 처리가 적용됩니다.

- 검색 중 중복 제출 차단, 빈 입력 전송 차단
- 앞뒤 공백 제거 후 500자 초과 시 API를 호출하지 않고 입력값을 유지하며 안내
- 한글 IME 조합 중 Enter와 Safari `keyCode 229` Enter 제출 차단
- Enter 전송, Shift+Enter 줄바꿈
- 검색 실패 시 내부 예외 대신 안전한 오류 문구 표시
- 가입·로그인 폼은 Core API와 같은 규칙(이메일 형식, 비밀번호 8~72자 영문·숫자, 사업자등록번호 10자리)을
  Zod로 먼저 검사하고, 409·422·401은 사유별 안내 문구로 바꿈

스타일은 `src/index.css`의 Tailwind `@theme` 토큰과 View 옆 `*.styles.ts`를 사용합니다.
계층·DI의 상세 규칙은 [아키텍처 문서](../docs/architecture.md#frontend와-내부-계약), 예제 API는
[SampleItem 계약](../docs/sample-item-contract.md)을 참고하세요.

## 검증

```bash
pnpm test
pnpm lint
pnpm build
```

Vitest·Testing Library로 화면과 ViewModel, 입력 검증, 응답 변환, 요청 취소·늦은 응답 처리를
검증합니다. 개발 화면의 공고는 Core API에서 받으며 `data/fixtures`는 테스트용입니다.
전체 서비스 연결과 장애 복구 검증은 저장소 루트의 `./infrastructure/scripts/verify-compose.sh`를
사용합니다. 실행 조건과 검증 범위는 [통합 smoke 안내](../infrastructure/README.md#통합-smoke)를
참고하세요.
