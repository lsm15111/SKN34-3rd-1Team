/**
 * 도움말 항목의 타입 계약입니다. 가이드·FAQ·매뉴얼·챗봇 네 표면이 이 한 벌을 함께 읽습니다.
 * 표면마다 따로 문구를 두면 서로 어긋나고, 어긋난 도움말은 없는 것보다 나쁩니다.
 */

/** `blocker`는 사용자가 실제로 막히는 지점, `concept`은 화면 표시나 기능의 뜻입니다. */
export type HelpCategory = 'blocker' | 'concept'

/** 답변이 끝나고 갈 곳입니다. `to`는 항상 `/app` 경로이며 공개 화면 변환은 `helpActionHref()`가 맡습니다. */
export type HelpAction = {
  label: string
  to: string
}

export type HelpEntry = {
  /** kebab-case 식별자입니다. 인용과 `related`가 이 값을 가리킵니다. */
  id: string
  /** 사용자가 실제로 칠 말입니다. 추천 질문 버튼에 이 문구를 그대로 씁니다. */
  question: string
  /** 매뉴얼과 인용 칩에 쓰는 항목 이름입니다. */
  title: string
  /** 결론 한 문장입니다. 답변 카드의 첫 줄이 됩니다. */
  summary: string
  /** 한 항목당 한 문단입니다. 마크다운을 쓰지 않으므로 파서 의존성이 없습니다. */
  body: string[]
  /** 지금 안 되는 것입니다. 빼먹을 수 없도록 필수 필드로 둡니다. */
  limitation: string
  category: HelpCategory
  /** 이 항목을 추천할 화면입니다. `:id` 같은 경로 변수는 한 칸을 통째로 대신합니다. 비면 전역 항목입니다. */
  routes: string[]
  /** 이어서 물어보기에 쓰는 다른 항목 id입니다. */
  related: string[]
  action?: HelpAction
  /** `YYYY-MM-DD`. 인용 팝오버에 함께 보여 오래된 안내를 감추지 않습니다. */
  updatedOn: string
}
