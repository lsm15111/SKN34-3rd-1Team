/**
 * 원문 근거 답변이 답하지 못한 이유를 알리는 문구입니다. 원문 질문 화면과 도움말 패널이 같은 문장을 씁니다.
 * 표면마다 따로 쓰면 같은 상황을 다르게 설명하게 됩니다.
 */
export const supportProgramEvidenceMessages = {
  cancelled: '질문 요청을 취소했습니다.',
  'timed-out': '답변 시간이 초과되었습니다. 입력한 질문을 다시 전송해 주세요.',
  'insufficient-evidence': '공고 원문에서 이 질문에 답할 만큼 충분한 근거를 찾지 못했습니다. 원문 공고를 확인해 주세요.',
  'not-supported': '이 제공처 공고는 아직 원문 근거 답변을 지원하지 않습니다. 원문 공고에서 확인해 주세요.',
  unavailable: '원문 근거 답변을 지금 준비하지 못했습니다. 잠시 후 다시 시도해 주세요.',
  failed: '질문에 답하지 못했습니다. 잠시 후 다시 시도해 주세요.',
} as const

export type SupportProgramEvidenceMessageKey = keyof typeof supportProgramEvidenceMessages
