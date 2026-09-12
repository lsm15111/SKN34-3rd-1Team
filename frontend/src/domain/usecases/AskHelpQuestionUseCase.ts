import type { HelpAnswer } from '../entities/HelpAnswer'
import type { HelpQuestion, HelpRepository } from '../repositories/HelpRepository'

/** 화면이 가진 도움말 항목만 근거로 자유 질문에 답하는 유스케이스입니다. */
export class AskHelpQuestionUseCase {
  private readonly repository: HelpRepository

  constructor(repository: HelpRepository) {
    this.repository = repository
  }

  execute(command: HelpQuestion, signal?: AbortSignal): Promise<HelpAnswer> {
    return this.repository.answerHelpQuestion(
      { ...command, question: command.question.trim() },
      signal,
    )
  }
}
