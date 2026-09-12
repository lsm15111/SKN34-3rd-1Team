/** 도움말 항목만 근거로 만든 답변입니다. 기권이면 answer는 비어 있고 화면이 상태별 문구를 씁니다. */
export type HelpAnswerStatus =
  | 'ANSWERED'
  | 'OUT_OF_SCOPE_PROGRAM'
  | 'OUT_OF_SCOPE_GENERAL'
  | 'NOT_IN_HELP'

export type HelpAnswer = {
  answer: string
  answerStatus: HelpAnswerStatus
  citationEntryIds: string[]
}

/** 답변의 근거로 보낼 도움말 항목입니다. 화면 표시용 필드는 보내지 않습니다. */
export type HelpAnswerSource = {
  id: string
  title: string
  summary: string
  body: string[]
  limitation: string
  status: 'available' | 'preparing'
}
