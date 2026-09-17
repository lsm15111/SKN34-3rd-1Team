import type { AssistantAnswer } from '../entities/AssistantAnswer'

/** 사용자가 지금 보고 있는 화면입니다. `programSelected`는 공고 상세처럼 원문 질문을 열 수 있는 화면인지입니다. */
export type AssistantScreenContext = {
  route: string
  programSelected: boolean
}

/**
 * 자유 질문 한 건입니다. 이전 대화는 [conversationId]로 서버가 저장해 둔 것을 쓰고, 답의 근거인 도움말도 Core가 가지므로
 * 대화 본문·도움말은 보내지 않습니다. [conversationId]는 대화를 시작할 때 브라우저가 만든 UUID입니다.
 */
export type AssistantQuestion = {
  message: string
  conversationId: string
  context: AssistantScreenContext
}

/** 자유 질문 결과입니다. 한도와 AI 장애는 화면이 다르게 안내하고, 그 밖의 실패는 예외로 올라갑니다. */
export type AskAssistantResult =
  | { outcome: 'answered'; answer: AssistantAnswer }
  | { outcome: 'rate-limited'; retryAfterSeconds: number | null }
  | { outcome: 'unavailable' }

export interface AssistantRepository {
  /** 도우미 자유 질문 한 건을 Core에 보내고 의도별 답을 받습니다. 세션이 있으면 쿠키로 함께 갑니다. */
  ask(question: AssistantQuestion, signal?: AbortSignal): Promise<AskAssistantResult>
}
