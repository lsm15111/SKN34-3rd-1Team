import { appPaths, publicPaths } from '../../../shared/routes/appPaths'

/**
 * 도우미가 보여 줄 메뉴 한 벌입니다. 이 단계는 화면만 만들므로 AI를 호출하지 않고,
 * 고른 메뉴에 정해진 안내와 갈 곳만 보여 줍니다. 답변 문구는 여기 한 곳에서만 고칩니다.
 */
export type HelpAssistantMenuItem = {
  id: string
  /** 버튼 왼쪽 그림입니다. 보조기술에는 읽히지 않습니다. */
  mark: string
  label: string
  /** 고른 뒤 도우미가 답하는 문장입니다. */
  reply: string
  /** 답변에 함께 붙는 갈 곳입니다. 로그인해야 열리는 화면은 `requiresSignIn`으로 표시합니다. */
  action?: { label: string; to: string; publicTo?: string; requiresSignIn?: boolean }
}

export const helpAssistantGreeting = [
  '안녕하세요! 정부지원사업을 자연어로 찾고, 공고 원문을 근거로 답해 드리는 GovBiz입니다.',
  '무엇을 도와드릴까요?',
] as const

export const helpAssistantOfficeHours = ['월-금 09:30~18:30', '점심 12:00~13:00 · 주말·공휴일 휴무'] as const

export const helpAssistantMenu: readonly HelpAssistantMenuItem[] = [
  {
    id: 'search',
    mark: '🔍',
    label: '지원사업 찾기',
    reply: '지역·업종·필요한 지원을 한 문장으로 알려 주시면 관련 공고와 확인할 조건을 함께 보여 드립니다.',
    action: { label: '검색 화면 열기', to: appPaths.chat, publicTo: publicPaths.landing },
  },
  {
    id: 'evidence',
    mark: '📄',
    label: '공고 원문에 질문하기',
    reply: '공고 상세에서 질문하면 공고 원문에서 찾은 문장만 근거로 답합니다. 근거를 찾지 못하면 답을 만들지 않습니다.',
    action: { label: '공고 찾으러 가기', to: appPaths.chat, publicTo: publicPaths.landing },
  },
  {
    id: 'saved',
    mark: '📌',
    label: '관심 공고함 보기',
    reply: '공고 상세에서 담은 공고가 관심 공고함에 최근 순서로 모입니다. 접수 상태와 마감일은 열 때마다 다시 계산합니다.',
    action: { label: '관심 공고함 열기', to: appPaths.savedPrograms, requiresSignIn: true },
  },
  {
    id: 'partners',
    mark: '🤝',
    label: '파트너 모집 보기',
    reply: '공고 하나에 모집글 하나를 올려 함께 지원할 기업을 찾습니다. 모집글 작성과 제안은 기업 정보를 등록한 계정만 할 수 있습니다.',
    action: { label: '파트너 모집 열기', to: appPaths.partners, publicTo: publicPaths.partners },
  },
  {
    id: 'company',
    mark: '🏢',
    label: '기업 정보 등록하기',
    reply: '내 프로필에서 사업자등록번호로 기업을 등록하면 맞춤 리포트와 파트너 모집을 쓸 수 있습니다.',
    action: { label: '내 프로필 열기', to: appPaths.profile, requiresSignIn: true },
  },
  {
    id: 'pricing',
    mark: '💳',
    label: '요금제 확인',
    reply: '지원사업 탐색은 무료입니다. 요금제 화면에서 무료·프로·팀에 무엇이 들어가는지 확인하실 수 있습니다.',
    action: { label: '요금제 열기', to: appPaths.pricing, publicTo: publicPaths.pricing },
  },
  {
    id: 'scope',
    mark: '💬',
    label: '무엇을 답해 주나요?',
    reply: '화면 사용법과 공고 원문에 적힌 내용까지 답합니다. 지원사업 제도 일반과 선정 가능성은 근거가 없어 답하지 않습니다.',
  },
  {
    id: 'contact',
    mark: '📮',
    label: '담당자 문의하기',
    reply: '상담 운영 시간에 담당자가 확인합니다. 운영 시간이 지난 문의는 다음 영업일에 답변드립니다.',
  },
] as const
