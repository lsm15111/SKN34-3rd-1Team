import type { AskAssistantResult, AssistantQuestion, AssistantRepository } from '../repositories/AssistantRepository'

type AskAssistantRepository = Pick<AssistantRepository, 'ask'>

/** 서버와 같은 상한입니다. 질문 500자. */
export const assistantQuestionLimits = { message: 500 } as const

const conversationIdPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/

export function isValidAssistantMessage(message: string): boolean {
  const trimmed = message.trim()
  return trimmed.length > 0 && trimmed.length <= assistantQuestionLimits.message
}

/** 가이드 자유 질문 한 건을 보냅니다. 질문은 다듬고, 서버가 대화를 찾을 수 있게 소문자 UUID 대화 id를 확인합니다. */
export class AskAssistantUseCase {
  private readonly repository: AskAssistantRepository

  constructor(repository: AskAssistantRepository) {
    this.repository = repository
  }

  execute(question: AssistantQuestion, signal?: AbortSignal): Promise<AskAssistantResult> {
    const message = question.message.trim()
    if (!isValidAssistantMessage(message)) throw new RangeError(`message must be 1~${assistantQuestionLimits.message} characters`)
    if (!conversationIdPattern.test(question.conversationId)) throw new RangeError('conversationId must be a lowercase UUID')
    return this.repository.ask({ ...question, message }, signal)
  }
}
