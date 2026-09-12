import { answerHelpQuestionApi, HelpAnswerApiError } from '../api/helpApi'
import { toHelpAnswer } from '../models/HelpAnswerDto'
import type { HelpAnswer } from '../../domain/entities/HelpAnswer'
import { HelpAnswerError } from '../../domain/errors/HelpAnswerError'
import type { HelpQuestion, HelpRepository } from '../../domain/repositories/HelpRepository'

/** Core API 응답을 검증된 Domain 답변으로 바꾸고 실패를 화면이 구분할 수 있는 오류로 옮깁니다. */
export class HelpRepositoryImpl implements HelpRepository {
  async answerHelpQuestion(command: HelpQuestion, signal?: AbortSignal): Promise<HelpAnswer> {
    try {
      return toHelpAnswer(await answerHelpQuestionApi(command, signal))
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') throw error
      if (error instanceof HelpAnswerApiError && error.status === 429) {
        throw new HelpAnswerError('rate-limited')
      }
      throw new HelpAnswerError('unavailable')
    }
  }
}
