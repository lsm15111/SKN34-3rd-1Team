import { z } from 'zod'

import type { HelpAnswer } from '../../domain/entities/HelpAnswer'

export const helpAnswerDtoSchema = z.object({
  answer: z.string().max(600),
  answerStatus: z.enum(['ANSWERED', 'OUT_OF_SCOPE_PROGRAM', 'OUT_OF_SCOPE_GENERAL', 'NOT_IN_HELP']),
  citationEntryIds: z.array(z.string().min(1).max(64)).max(3),
}).superRefine((answer, context) => {
  if (answer.answerStatus === 'ANSWERED' && (answer.citationEntryIds.length === 0 || answer.answer.trim() === '')) {
    context.addIssue({ code: 'custom', path: ['citationEntryIds'], message: 'ANSWERED 답변에는 본문과 근거가 필요합니다.' })
  }
  if (answer.answerStatus !== 'ANSWERED' && answer.citationEntryIds.length > 0) {
    context.addIssue({ code: 'custom', path: ['citationEntryIds'], message: '기권 답변에는 근거를 담을 수 없습니다.' })
  }
})

export type HelpAnswerDto = z.infer<typeof helpAnswerDtoSchema>

/**
 * 화면이 보낸 항목 안에서만 인용했는지 다시 확인합니다. Core도 검사하지만 화면에 그리는 것은 여기이므로
 * 모르는 항목을 가리키는 답변은 근거 없는 답으로 봅니다.
 */
export function parseHelpAnswerDto(payload: unknown, allowedEntryIds: readonly string[]): HelpAnswerDto {
  const answer = helpAnswerDtoSchema.parse(payload)
  const allowed = new Set(allowedEntryIds)
  if (answer.citationEntryIds.some((id) => !allowed.has(id))) {
    throw new Error('Core API returned a help citation outside the requested help entries.')
  }
  return answer
}

export function toHelpAnswer(dto: HelpAnswerDto): HelpAnswer {
  return {
    answer: dto.answer,
    answerStatus: dto.answerStatus,
    citationEntryIds: [...dto.citationEntryIds],
  }
}
