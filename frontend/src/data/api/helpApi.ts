import type { HelpQuestion } from '../../domain/repositories/HelpRepository'
import { getCoreApiBaseUrl } from './coreApiConfig'
import { parseHelpAnswerDto, type HelpAnswerDto } from '../models/HelpAnswerDto'

const HELP_ANSWER_PATH = '/api/v1/help/answers'

/** 요청량 한도(429)와 그 밖의 실패를 구분해 화면이 다르게 안내하도록 합니다. */
export class HelpAnswerApiError extends Error {
  readonly status: number

  constructor(status: number) {
    super(`Core API returned HTTP ${status} for a help answer.`)
    this.name = 'HelpAnswerApiError'
    this.status = status
  }
}

/** 화면이 가진 도움말 항목과 질문을 보내고 검증된 답변을 받습니다. */
export async function answerHelpQuestionApi(
  command: HelpQuestion,
  signal?: AbortSignal,
): Promise<HelpAnswerDto> {
  const response = await fetch(`${getCoreApiBaseUrl()}${HELP_ANSWER_PATH}`, {
    method: 'POST',
    headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
    body: JSON.stringify(command),
    signal,
  })

  if (!response.ok) throw new HelpAnswerApiError(response.status)

  return parseHelpAnswerDto(await response.json(), command.entries.map((entry) => entry.id))
}
