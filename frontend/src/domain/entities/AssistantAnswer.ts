import type { ApplicationProgressStage } from './ApplicationPreparation'

/**
 * Core가 AI Service 분류를 검증해 돌려준 도우미 의도입니다. 화면은 이 값으로 말풍선 모양을 정합니다.
 * `PARTNER_MATCH`·`SAVED_PROGRAMS_QUESTION`은 Core의 도구 에이전트가 켜져 있을 때만 옵니다.
 */
export type AssistantIntent =
  | 'PRODUCT_HELP' | 'ACCOUNT_STATE' | 'SEARCH' | 'PROGRAM_QUESTION' | 'OUT_OF_SCOPE' | 'UNCLEAR'
  | 'PARTNER_MATCH' | 'SAVED_PROGRAMS_QUESTION'

export type AssistantAccountTopic =
  | 'SAVED_PROGRAMS' | 'RECEIVED_PROPOSALS' | 'COMPANY_PROFILE'
  | 'APPLICATION_PREPARATIONS' | 'COMBINATION_REVIEWS' | 'DAILY_REPORT'

/** 답 뒤에 붙는 이동 버튼 하나입니다. `to`는 Core가 허용한 `/app` 아래 경로입니다. */
export type AssistantNavigation = {
  label: string
  to: string
}

export type AssistantCardKind = 'RECRUITMENT' | 'PROGRAM' | 'PREPARATION' | 'REVIEW'

/** 도구 에이전트가 회원 자료에서 고른 항목 하나입니다. 제목을 누르면 `to`의 상세 화면으로 갑니다. */
export type AssistantCard = {
  kind: AssistantCardKind
  /** 모집글·신청 준비·중복 검토는 번호, 공고는 `sourceCode:sourceProgramId`입니다. */
  id: string
  title: string
  subtitle: string | null
  /** 이 항목을 고른 이유 한 문장입니다. */
  reason: string
  /** 관심 공고 묶음 질문에서만: 공고 원문에서 그대로 옮긴 근거 구절입니다. Core가 원문과 대조한 것만 옵니다. */
  to: string
}

export type AssistantActionKind =
  | 'SAVE_PROGRAM' | 'UNSAVE_PROGRAM' | 'START_APPLICATION_PREPARATION' | 'SET_PREPARATION_STAGE' | 'RUN_COMBINATION_REVIEW'

/**
 * 사용자가 눌러야 실행되는 제안 하나입니다. 문구와 대상은 Core가 자기 자료로 만든 값이고,
 * 실행은 화면이 기존 기능 API로 합니다. 가이드가 대신 실행하지는 않습니다.
 */
export type AssistantAction =
  | { kind: 'SAVE_PROGRAM'; label: string; confirm: string; sourceCode: string; sourceProgramId: string }
  | { kind: 'UNSAVE_PROGRAM'; label: string; confirm: string; sourceCode: string; sourceProgramId: string }
  | { kind: 'START_APPLICATION_PREPARATION'; label: string; confirm: string; to: string }
  | { kind: 'SET_PREPARATION_STAGE'; label: string; confirm: string; preparationId: number; stage: ApplicationProgressStage }
  | { kind: 'RUN_COMBINATION_REVIEW'; label: string; confirm: string; reviewId: number }

/** 확인 버튼을 눌러 실제로 실행하는 제안입니다. 화면만 여는 제안(신청 문서 준비 시작)은 이동 버튼으로 답니다. */
export type AssistantExecutableAction = Exclude<AssistantAction, { kind: 'START_APPLICATION_PREPARATION' }>

/** 도우미 자유 질문 한 건의 답입니다. 의도에 따라 채워지는 필드가 다르고, `UNCLEAR`만 `answer`가 없습니다. */
export type AssistantAnswer = {
  intent: AssistantIntent
  answer: string | null
  /** 근거가 된 도움말 항목 id입니다. 요청에 실어 보낸 항목만 옵니다. */
  citations: string[]
  clarificationQuestion: string | null
  searchQuery: string | null
  accountTopic: AssistantAccountTopic | null
  navigation: AssistantNavigation | null
  /** 도구 의도의 답에만 붙는 항목 목록(최대 5장)입니다. 그 밖에는 빈 배열입니다. */
  cards: AssistantCard[]
  /** 확인 버튼으로 보여 줄 실행 제안(최대 2개)입니다. 비로그인에게는 언제나 빈 배열입니다. */
  actions: AssistantAction[]
}
