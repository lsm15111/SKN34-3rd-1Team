import { appPaths, isAppPath, publicPaths, toAppPath } from '../routes/appPaths'
import type { HelpAction, HelpEntry } from './helpTypes'

/**
 * 사용자가 실제로 막히는 지점에서 뽑은 도움말 항목입니다. 화면 22개를 균등하게 설명하는 대신
 * 막히는 곳만 답합니다. 제도 상식과 공고 내용은 근거가 없으므로 여기서 답하지 않습니다.
 */
export const helpEntries: readonly HelpEntry[] = [
  {
    id: 'conversation-proposal-confirm',
    question: '검색이 왜 바로 안 되나요?',
    title: '검색 전에 확인 카드가 뜨는 이유',
    summary: '검색어에서 읽어 낸 조건을 먼저 보여 주고 확인을 받습니다.',
    body: [
      '입력한 문장에서 지역·분야·접수 상태 같은 조건을 읽어 냅니다. 조건을 잘못 읽으면 결과 전체가 어긋나므로, 검색을 시작하기 전에 무엇으로 찾을지 카드로 보여 줍니다.',
      '카드의 조건이 맞으면 그대로 검색하고, 다르면 문장을 고쳐 다시 보내면 됩니다.',
    ],
    limitation: '카드에서 조건을 직접 고칠 수는 없습니다. 문장을 다시 써야 합니다.',
    category: 'blocker',
    routes: [appPaths.chat],
    related: ['relevance-score-meaning', 'search-slow-or-cancelled'],
    action: { label: '검색 화면 열기', to: appPaths.chat },
    updatedOn: '2026-09-12',
  },
  {
    id: 'relevance-score-meaning',
    question: '점수는 무슨 뜻인가요?',
    title: '점수는 무엇을 뜻하나요',
    summary: '점수는 검색어와 공고의 관련도입니다. 신청 자격이나 선정 가능성을 뜻하지 않습니다.',
    body: [
      '점수는 검색 문장과 공고 내용이 얼마나 가까운지를 나타냅니다. 점수가 높다고 신청할 수 있다는 뜻이 아니고, 낮다고 자격이 없다는 뜻도 아닙니다.',
      '자격은 점수와 따로 확인해 확인 필요 배지로 표시합니다. 자격을 확인하지 못했다는 이유로 점수를 깎지 않습니다.',
    ],
    limitation: '점수는 한 번의 검색 결과 안에서의 순서를 위한 값이라 다른 검색의 점수와 비교할 수 없습니다.',
    category: 'concept',
    routes: [appPaths.chat, appPaths.supportProgramDetail],
    related: ['eligibility-check-required'],
    action: { label: '검색 화면 열기', to: appPaths.chat },
    updatedOn: '2026-09-12',
  },
  {
    id: 'eligibility-check-required',
    question: '확인 필요는 왜 뜨나요?',
    title: '확인 필요는 무슨 뜻인가요',
    summary: '공고의 공식 요약만으로 지역·대상 조건을 판단할 수 없을 때 붙습니다. 자격이 없다는 뜻이 아닙니다.',
    body: [
      '자격 확인은 공고가 공식으로 제공한 본문만 근거로 씁니다. 지역이나 대상이 본문에 적혀 있지 않으면 없는 조건을 지어내는 대신 확인 필요로 남깁니다.',
      '확인 필요 공고는 원문에서 신청 대상을 직접 확인해 주세요. 점수가 높아도 확인 필요일 수 있습니다.',
    ],
    limitation: '자격 판정은 공식 본문 기준이라 최종 신청 자격을 보장하지 않습니다.',
    category: 'concept',
    routes: [appPaths.chat, appPaths.supportProgramDetail],
    related: ['relevance-score-meaning', 'insufficient-evidence'],
    action: { label: '검색 화면 열기', to: appPaths.chat },
    updatedOn: '2026-09-12',
  },
  {
    id: 'receipt-status-unknown',
    question: '접수 상태 미확인은 무슨 뜻인가요?',
    title: '접수 상태를 확인할 수 없는 공고',
    summary: '제공처가 신청 기간을 구조화해 주지 않아 접수 상태를 계산할 수 없는 공고입니다.',
    body: [
      '과학기술정보통신부와 충청남도 온라인수출지원시스템 공고에는 기계가 읽을 수 있는 신청 기간이 없습니다. 날짜를 추측해 접수 중으로 표시하면 이미 마감된 공고를 신청하게 되므로 상태를 비워 둡니다.',
      '접수 여부는 공식 원문에서 직접 확인해 주세요.',
    ],
    limitation: '이 두 제공처의 공고는 접수 상태로 걸러지지 않습니다.',
    category: 'blocker',
    routes: [appPaths.chat, appPaths.supportProgramDetail],
    related: ['relevance-score-meaning'],
    action: { label: '검색 화면 열기', to: appPaths.chat },
    updatedOn: '2026-09-12',
  },
  {
    id: 'insufficient-evidence',
    question: '근거를 찾지 못했다는 답은 왜 나오나요?',
    title: '근거가 없으면 답하지 않습니다',
    summary: '공고 원문에 답이 없으면 추측하지 않고 답변을 멈춥니다.',
    body: [
      '원문 질문은 공고 상세 원문에서 찾은 문장만 근거로 답합니다. 질문에 해당하는 문장이 없으면 그럴듯한 답을 지어내는 대신 근거가 없다고 알립니다.',
      '틀린 답 하나가 답하지 않는 것보다 나쁘기 때문입니다. 질문을 더 좁혀 다시 묻거나 공식 원문에서 직접 확인해 주세요.',
    ],
    limitation: '첨부파일과 기관 홈페이지 내용은 원문 질문의 근거에 들어가지 않습니다.',
    category: 'concept',
    routes: [appPaths.supportProgramQuestion, appPaths.supportProgramDetail],
    related: ['eligibility-check-required'],
    action: { label: '공고 검색 열기', to: appPaths.chat },
    updatedOn: '2026-09-12',
  },
  {
    id: 'review-input-revision',
    question: '중복 검토 결과가 왜 예전 내용인가요?',
    title: '검토 결과는 실행한 시점의 입력으로 저장됩니다',
    summary: '검토는 실행할 때의 입력을 그대로 보관합니다. 입력을 고쳤다면 다시 실행해야 합니다.',
    body: [
      '중복 지원 검토는 실행 시점의 입력을 붙잡아 결과와 함께 저장합니다. 나중에 입력을 바꿔도 이미 저장된 결과는 바뀌지 않습니다. 결과 화면의 입력 버전으로 어떤 입력이었는지 확인할 수 있습니다.',
      '입력을 고친 뒤 예전 요청을 다시 확인하면 버전 충돌로 거절됩니다. 거절된 요청을 정리하고 새로 실행해 주세요.',
    ],
    limitation: '지난 결과를 새 입력으로 자동으로 다시 계산하지 않습니다.',
    category: 'blocker',
    routes: [appPaths.combinationReviews, appPaths.combinationReviewNew, appPaths.combinationReviewDetail],
    related: ['insufficient-evidence'],
    action: { label: '중복 검토 열기', to: appPaths.combinationReviews },
    updatedOn: '2026-09-12',
  },
  {
    id: 'application-preparation-start',
    question: '신청 준비 목록이 왜 비어 있나요?',
    title: '신청 준비는 새 작성에서 시작합니다',
    summary: '공고를 저장한다고 신청 준비가 만들어지지는 않습니다. 새 작성에서 직접 시작해야 합니다.',
    body: [
      '신청 준비는 공식 첨부에서 찾은 양식과 지원 분야를 고른 뒤에 만들어집니다. 검색 결과를 열어 보는 것만으로는 목록에 아무것도 쌓이지 않습니다.',
      '새 작성에서 양식을 확인하고 시작하면 그때부터 목록에 남고 다시 열 수 있습니다.',
    ],
    limitation: '공식 첨부에서 작성용 양식을 찾지 못한 공고는 신청 준비를 시작할 수 없습니다.',
    category: 'blocker',
    routes: [appPaths.applicationPreparations, appPaths.applicationPreparationNew, appPaths.applicationPreparationDetail],
    related: ['review-input-revision'],
    action: { label: '신청 준비 열기', to: appPaths.applicationPreparations },
    updatedOn: '2026-09-12',
  },
  {
    id: 'recruitment-company-only',
    question: '모집글을 왜 쓸 수 없나요?',
    title: '파트너 모집글은 기업 정보를 등록해야 씁니다',
    summary: '파트너 모집글 작성과 제안은 기업 정보를 등록한 계정만 할 수 있습니다.',
    body: [
      '모집글은 기업 대 기업으로 파트너를 찾는 글이라 어느 기업이 올렸는지 확인할 수 있어야 합니다. 그래서 기업 정보를 등록한 계정에만 작성과 제안을 엽니다.',
      '내 프로필에서 사업자등록번호로 기업 정보를 등록하면 바로 쓸 수 있습니다.',
    ],
    limitation: '개인 회원은 모집글을 읽을 수만 있습니다.',
    category: 'blocker',
    routes: [appPaths.partners, appPaths.partnerNew, appPaths.myPartners, appPaths.proposals],
    related: ['saved-program-basics'],
    action: { label: '기업 정보 등록하기', to: appPaths.profile },
    updatedOn: '2026-09-12',
  },
  {
    id: 'search-slow-or-cancelled',
    question: '검색이 왜 느리거나 끊기나요?',
    title: '검색에 걸리는 시간과 제한',
    summary: '조건 해석과 공고 점수화를 거치므로 시간이 걸리고, 정해진 시간을 넘기면 요청을 중단합니다.',
    body: [
      '자연어 검색은 문장에서 조건을 읽고 후보 공고를 점수화하는 두 단계를 거칩니다. 화면은 조건 해석을 40초, 검색을 90초까지 기다리고 넘어가면 요청을 취소합니다.',
      '오래 걸린 요청을 결과 0건으로 바꿔 보여 주지 않습니다. 실패는 실패로 알리고 다시 시도할 수 있게 합니다.',
    ],
    limitation: '취소해도 이미 시작된 서버 작업은 끝까지 실행될 수 있습니다.',
    category: 'blocker',
    routes: [appPaths.chat],
    related: ['conversation-proposal-confirm'],
    action: { label: '검색 화면 열기', to: appPaths.chat },
    updatedOn: '2026-09-12',
  },
  {
    id: 'saved-program-basics',
    question: '관심 공고함은 어떻게 쓰나요?',
    title: '관심 공고함에 공고 담기',
    summary: '공고 상세에서 담은 공고가 관심 공고함에 최근 순서로 모입니다.',
    body: [
      '공고 상세 화면에서 담으면 관심 공고함에 쌓입니다. 목록에서 제목을 누르면 다시 상세로 가고, 접수 상태와 마감일은 열 때마다 다시 계산해 보여 줍니다.',
      '담는 것은 로그인한 계정에 저장하므로 다른 기기에서도 같은 목록을 봅니다.',
    ],
    limitation: '관심 공고함이 마감을 알려 주지는 않습니다. 마감일은 목록에서 직접 확인해 주세요.',
    category: 'concept',
    routes: [],
    related: [],
    action: { label: '관심 공고함 열기', to: appPaths.savedPrograms },
    updatedOn: '2026-09-12',
  },
] as const

/** 공개 화면에서 열었을 때 같은 내용을 보여 주는 경로입니다. 라우팅 규칙을 복사하지 않고 `appPaths`를 참조합니다. */
const publicMirrorPaths: Readonly<Record<string, string>> = {
  [appPaths.chat]: publicPaths.landing,
  [appPaths.pricing]: publicPaths.pricing,
  [appPaths.partners]: publicPaths.partners,
  [appPaths.partnerDetail]: publicPaths.partnerDetail,
  [appPaths.supportProgramDetail]: publicPaths.supportProgramDetail,
  [appPaths.supportProgramQuestion]: publicPaths.supportProgramQuestion,
}

function normalizePathname(pathname: string): string {
  return pathname.replace(/\/+$/, '') || '/'
}

/** `:preparationId` 같은 경로 변수는 한 칸을 통째로 대신합니다. */
function matchesRoute(pattern: string, pathname: string): boolean {
  const patternParts = pattern.split('/')
  const pathnameParts = pathname.split('/')
  if (patternParts.length !== pathnameParts.length) return false
  return patternParts.every((part, index) => part.startsWith(':') || part === pathnameParts[index])
}

export function helpEntryById(id: string): HelpEntry | undefined {
  return helpEntries.find((entry) => entry.id === id)
}

/**
 * 지금 보고 있는 화면에 맞는 항목을 앞에 두고 모자라면 전역 항목으로 채웁니다.
 * 공개 경로는 대응하는 `/app` 경로로 바꿔 맞춥니다.
 */
export function helpEntriesForRoute(pathname: string, limit = 3): HelpEntry[] {
  const normalized = normalizePathname(pathname)
  const appPathname = isAppPath(normalized) ? normalized : toAppPath(normalized)
  const matched = helpEntries.filter((entry) => entry.routes.some((route) => matchesRoute(route, appPathname)))
  const global = helpEntries.filter((entry) => entry.routes.length === 0)
  return [...matched, ...global].slice(0, limit)
}

function compact(text: string): string {
  return text.replace(/\s+/g, '').toLowerCase()
}

/**
 * 사용자가 직접 친 문장을 항목에 맞춥니다. 질문과 제목의 글자만 비교하며 뜻을 추측하지 않습니다.
 * 맞는 항목이 없으면 `undefined`를 주고, 화면은 안내에 없다고 알립니다.
 */
export function matchHelpEntry(text: string): HelpEntry | undefined {
  const asked = compact(text)
  if (asked.length < 2) return undefined
  return helpEntries.find((entry) => compact(entry.question) === asked)
    ?? helpEntries.find((entry) => compact(entry.question).includes(asked) || asked.includes(compact(entry.question)))
    ?? helpEntries.find((entry) => compact(entry.title).includes(asked) || asked.includes(compact(entry.title)))
}

/** 이어서 물어보기 목록입니다. 없는 id는 조용히 건너뛰지 않고 테스트가 막습니다. */
export function relatedHelpEntries(entry: HelpEntry): HelpEntry[] {
  return entry.related.flatMap((id) => {
    const related = helpEntryById(id)
    return related ? [related] : []
  })
}

/**
 * 항목의 행동 버튼이 실제로 열 주소입니다. 공개 화면에서 대응하는 화면이 없으면 로그인으로 보내며
 * 로그인 뒤 원래 가려던 화면으로 돌아갑니다.
 */
export function helpActionHref(action: HelpAction, inApp: boolean): string {
  if (inApp) return action.to
  const mirrored = publicMirrorPaths[action.to]
  return mirrored ?? `${publicPaths.login}?next=${encodeURIComponent(action.to)}`
}
