/**
 * 도우미가 보여 줄 메뉴 한 벌입니다. 지금은 화면 틀만 만드는 단계라 요금제 안내 하나만 둡니다.
 * 메뉴를 늘릴 때 이 목록에만 추가하면 화면은 그대로 그립니다.
 */
export type HelpAssistantMenuItem = {
  id: string
  /** 버튼 왼쪽 그림입니다. 보조기술에는 읽히지 않습니다. */
  mark: string
  label: string
  /** 고른 뒤 도우미가 먼저 하는 말입니다. */
  reply: string
}

export const helpAssistantGreeting = [
  '안녕하세요! 정부지원사업을 자연어로 찾고, 공고 원문을 근거로 답해 드리는 GovBiz입니다.',
  '무엇을 도와드릴까요?',
] as const

export const helpAssistantOfficeHours = ['월-금 09:30~18:30', '점심 12:00~13:00 · 주말·공휴일 휴무'] as const

export const helpAssistantMenu: readonly HelpAssistantMenuItem[] = [
  {
    id: 'pricing',
    mark: '💳',
    label: '요금제 확인',
    reply: '지원사업 탐색은 무료로 시작하실 수 있습니다. 요금제별로 무엇이 들어가는지 정리해 드릴게요.',
  },
] as const
