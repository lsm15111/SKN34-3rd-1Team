import type { HelpAnswer, HelpAnswerSource } from '../entities/HelpAnswer'

export type HelpQuestion = {
  question: string
  entries: HelpAnswerSource[]
}

export interface HelpRepository {
  answerHelpQuestion(command: HelpQuestion, signal?: AbortSignal): Promise<HelpAnswer>
}
