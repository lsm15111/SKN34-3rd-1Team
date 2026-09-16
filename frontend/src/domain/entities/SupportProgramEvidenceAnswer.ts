/**
 * 공고 원문 근거 질문을 지원하는 제공처입니다. 기업마당은 상세 원문, 과학기술정보통신부는 공식 첨부 공고문을 근거로 씁니다.
 */
export const evidenceQuestionSourceCodes = ['BIZINFO', 'MSIT'] as const

export function supportsEvidenceQuestion(sourceCode: string | undefined): boolean {
  return (evidenceQuestionSourceCodes as readonly string[]).includes(sourceCode ?? '')
}

/** 공고 원문에서 찾은 문장과 원문 위치를 함께 보여 주는 답변 근거입니다. */
export type SupportProgramEvidenceCitation = {
  excerpt: string
  sourceUrl: string
  chunkOrder: number
}

export type SupportProgramEvidenceAnswerStatus = 'ANSWERED' | 'INSUFFICIENT_EVIDENCE'

/** 특정 공고 원문만 근거로 생성한 질문 답변입니다. */
export type SupportProgramEvidenceAnswer = {
  answer: string
  answerStatus: SupportProgramEvidenceAnswerStatus
  citations: SupportProgramEvidenceCitation[]
}
