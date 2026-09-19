import { z } from 'zod'

import { applicationProgressStages } from '../../domain/entities/ApplicationPreparation'
import type { AssistantAction, AssistantAnswer } from '../../domain/entities/AssistantAnswer'

export const assistantIntentSchema = z.enum([
  'PRODUCT_HELP', 'ACCOUNT_STATE', 'SEARCH', 'PROGRAM_QUESTION', 'OUT_OF_SCOPE', 'UNCLEAR', 'PARTNER_MATCH', 'SAVED_PROGRAMS_QUESTION',
])
export const assistantAccountTopicSchema = z.enum([
  'SAVED_PROGRAMS', 'RECEIVED_PROPOSALS', 'COMPANY_PROFILE', 'APPLICATION_PREPARATIONS', 'COMBINATION_REVIEWS', 'DAILY_REPORT',
])
export const assistantCardKindSchema = z.enum(['RECRUITMENT', 'PROGRAM', 'PREPARATION', 'REVIEW'])

/** 이동 버튼의 경로는 Core가 `/app` 아래 내부 경로만 내려주지만, 화면은 한 번 더 절대 경로인지 확인합니다. */
export const assistantNavigationDtoSchema = z.object({
  label: z.string().trim().min(1).max(160),
  to: z.string().regex(/^\/[A-Za-z0-9._~!$&'()*+,;=:@%/-]*$/),
})

/** 가이드가 고른 항목(모집글·공고·신청 준비·중복 검토)입니다. 경로는 `/app` 아래 내부 경로에 질의만 허용합니다. */
export const assistantCardDtoSchema = z.object({
  kind: assistantCardKindSchema,
  id: z.string().regex(/^[A-Za-z0-9_:.-]{1,80}$/),
  title: z.string().trim().min(1).max(160),
  subtitle: z.string().trim().min(1).max(160).nullable(),
  reason: z.string().trim().min(1).max(200),
  /** 관심 공고 묶음 질문에서만: 공고 원문에서 그대로 옮긴 근거 구절입니다. */
  to: z.string().regex(/^\/app\/[A-Za-z0-9/_-]+(\?[A-Za-z0-9_=&%.:+-]*)?$/),
})

export const assistantActionKindSchema = z.enum([
  'SAVE_PROGRAM', 'UNSAVE_PROGRAM', 'START_APPLICATION_PREPARATION', 'SET_PREPARATION_STAGE', 'RUN_COMBINATION_REVIEW',
])

/**
 * 확인 버튼 하나입니다. Core가 종류마다 필요한 값만 채우므로 여기서는 느슨하게 받고, 종류에 맞지 않는 제안은
 * [toAssistantAction]이 버립니다. 실행 대상은 화면이 기존 API에 그대로 넘깁니다.
 */
export const assistantActionDtoSchema = z.object({
  kind: assistantActionKindSchema,
  label: z.string().trim().min(1).max(80),
  confirm: z.string().trim().min(1).max(200),
  sourceCode: z.string().regex(/^[A-Z][A-Z0-9_]{0,39}$/).nullable(),
  sourceProgramId: z.string().trim().min(1).max(255).nullable(),
  preparationId: z.number().int().positive().nullable(),
  stage: z.enum(applicationProgressStages).nullable(),
  reviewId: z.number().int().positive().nullable(),
  to: z.string().regex(/^\/app\/[A-Za-z0-9/_-]+(\?[A-Za-z0-9_=&%.:+-]*)?$/).nullable(),
})

export type AssistantActionDto = z.infer<typeof assistantActionDtoSchema>

/** 종류에 필요한 값이 빠진 제안은 버튼을 만들지 않습니다. 눌러도 아무 일도 하지 않는 버튼을 두지 않기 위해서입니다. */
export function toAssistantAction(dto: AssistantActionDto): AssistantAction | null {
  const common = { label: dto.label, confirm: dto.confirm }
  switch (dto.kind) {
    case 'SAVE_PROGRAM':
    case 'UNSAVE_PROGRAM':
      return dto.sourceCode === null || dto.sourceProgramId === null
        ? null
        : { ...common, kind: dto.kind, sourceCode: dto.sourceCode, sourceProgramId: dto.sourceProgramId }
    case 'START_APPLICATION_PREPARATION':
      return dto.to === null ? null : { ...common, kind: dto.kind, to: dto.to }
    case 'SET_PREPARATION_STAGE':
      return dto.preparationId === null || dto.stage === null
        ? null
        : { ...common, kind: dto.kind, preparationId: dto.preparationId, stage: dto.stage }
    case 'RUN_COMBINATION_REVIEW':
      return dto.reviewId === null ? null : { ...common, kind: dto.kind, reviewId: dto.reviewId }
  }
}

export const assistantAnswerDtoSchema = z.object({
  intent: assistantIntentSchema,
  answer: z.string().trim().min(1).max(600).nullable(),
  citations: z.array(z.string().regex(/^[a-z0-9]+(-[a-z0-9]+)*$/)).max(3),
  clarificationQuestion: z.string().trim().min(1).max(160).nullable(),
  searchQuery: z.string().trim().min(1).max(500).nullable(),
  accountTopic: assistantAccountTopicSchema.nullable(),
  navigation: assistantNavigationDtoSchema.nullable(),
  cards: z.array(assistantCardDtoSchema).max(5),
  actions: z.array(assistantActionDtoSchema).max(2),
})

export type AssistantAnswerDto = z.infer<typeof assistantAnswerDtoSchema>

export function toAssistantAnswer(dto: AssistantAnswerDto): AssistantAnswer {
  return {
    intent: dto.intent,
    answer: dto.answer,
    citations: [...dto.citations],
    clarificationQuestion: dto.clarificationQuestion,
    searchQuery: dto.searchQuery,
    accountTopic: dto.accountTopic,
    navigation: dto.navigation === null ? null : { label: dto.navigation.label, to: dto.navigation.to },
    cards: dto.cards.map((card) => ({ kind: card.kind, id: card.id, title: card.title, subtitle: card.subtitle, reason: card.reason, to: card.to })),
    actions: dto.actions.map(toAssistantAction).filter((action): action is AssistantAction => action !== null),
  }
}
