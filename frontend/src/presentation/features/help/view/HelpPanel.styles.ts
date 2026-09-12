// 색상이나 CSS 속성이 아니라 도움말 패널에서 맡는 UI 역할을 이름으로 사용합니다.
// 치수는 도움말 챗봇 화면 명세를 따르고, 배치는 2026-09-12에 확인한 다른 제품 챗봇을 참고했습니다.
// 참고한 것: 범위를 머리에 늘 두기(Fin), 답변 아래 메타 줄(Fin), 입력창 한 상자(Fin·Vercel),
// 추천 질문을 입력창 바로 위에 가볍게(Vercel), 단축키 안내(Vercel), 번호 인용(AI 채팅 UI 관행).
export const helpPanelStyles = {
  launcher:
    'fixed right-6 bottom-6 z-40 grid size-[3.25rem] cursor-pointer place-items-center rounded-full text-lg font-bold text-white shadow-[0_6px_20px_-6px_rgb(8_127_70_/_55%)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-primary max-chat:right-3.5 max-chat:bottom-[max(0.875rem,env(safe-area-inset-bottom))] max-chat:size-[2.875rem] max-chat:text-base',
  launcherClosed: 'bg-brand-primary hover:bg-[#066538]',
  launcherOpen: 'bg-app-ink text-base hover:bg-black',
  launcherLifted: 'bottom-[5.75rem] max-chat:bottom-[max(5rem,env(safe-area-inset-bottom))]',

  // 런처(52) 위로 12만큼 띄워 겹치지 않게 합니다. 좁은 화면은 전체 화면 시트라 이 값을 쓰지 않습니다.
  panel:
    'fixed right-6 bottom-[5.5rem] z-50 flex max-h-[35rem] w-[22rem] flex-col overflow-hidden rounded-2xl border border-sample-border bg-white text-app-ink shadow-[0_2px_4px_rgb(23_27_25_/_6%),0_18px_40px_-18px_rgb(23_27_25_/_28%)] motion-safe:animate-help-panel-enter max-chat:inset-0 max-chat:max-h-none max-chat:w-auto max-chat:rounded-none max-chat:border-0',
  panelLifted: 'bottom-[9.75rem] max-chat:bottom-0',

  // 머리를 두 줄로 두어 "무엇을 답하는 곳인지"가 대화 중에도 사라지지 않게 합니다.
  header: 'flex flex-none items-start justify-between gap-2 border-b border-sample-border px-3.5 py-2.5',
  headerText: 'flex min-w-0 flex-col gap-0.5',
  headerTitle: 'm-0 flex items-center gap-2 text-[0.845rem] font-semibold leading-tight',
  headerDot: 'size-[0.4375rem] flex-none rounded-full bg-brand-primary',
  headerScope: 'text-[0.6875rem] leading-tight text-sample-muted',
  headerClose:
    'grid size-7 flex-none cursor-pointer place-items-center rounded-full text-[0.95rem] text-sample-muted hover:bg-app-canvas hover:text-app-ink focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-brand-primary',

  body: 'relative flex min-h-0 flex-1 flex-col gap-3.5 overflow-y-auto overscroll-contain bg-white p-3.5',
  log: 'flex flex-col gap-3.5 empty:hidden',

  // 빈 화면은 카드를 쌓지 않고 조용히 안내만 합니다. 도움말은 작업 중에 곁눈질로 보는 화면입니다.
  emptyNote: 'm-0 mt-auto text-[0.72rem] leading-[1.65] text-sample-muted',
  shortcutHint: 'm-0 px-1.5 text-[0.655rem] leading-tight text-[#98a0a6]',
  shortcutKey: 'rounded border border-sample-border bg-app-canvas px-1 py-px font-medium text-sample-muted',

  // 추천 질문은 입력창 바로 위에 둡니다. 테두리 없는 행이라 답변을 읽는 흐름을 끊지 않습니다.
  suggestionDock: 'flex flex-none flex-col gap-1.5 px-3 pb-2',
  suggestions: 'flex flex-col',
  suggestionsLabel: 'mb-1 text-[0.625rem] font-semibold tracking-[0.07em] text-[#98a0a6] uppercase',
  suggestionButton:
    'flex w-full min-h-9 cursor-pointer items-center gap-2 rounded-md px-1.5 py-1.5 text-left text-[0.78rem] leading-[1.5] text-app-ink hover:bg-app-canvas focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-brand-primary',
  suggestionMark: 'flex-none text-[0.72rem] text-brand-primary',

  question: 'max-w-[85%] self-end rounded-[0.75rem_0.75rem_0.2rem_0.75rem] bg-app-canvas px-3 py-2 text-[0.78rem] leading-[1.6] text-app-ink [overflow-wrap:anywhere]',

  // 답변은 말풍선을 두르지 않습니다. 352px 패널에서 테두리·여백은 읽는 폭만 줄입니다.
  answer: 'flex flex-col text-[0.8125rem] leading-[1.72] [overflow-wrap:anywhere]',
  answerLead: 'm-0 mb-2 font-semibold last:mb-0',
  answerParagraph: 'm-0 mb-2 last:mb-0',
  answerLimitation: 'm-0 mb-2 text-sample-muted last:mb-0',
  answerPreparing:
    'mb-2 inline-flex w-fit items-center rounded bg-[#fffaf3] px-1.5 py-px text-[0.625rem] font-medium text-[#9a5b1d]',

  // 답변이 무엇으로 만들어졌는지 밑줄처럼 붙는 한 줄. 배지를 본문 위에 올리지 않습니다.
  meta: 'mt-2 flex flex-wrap items-center gap-x-1.5 gap-y-1 text-[0.655rem] text-[#98a0a6]',
  metaMark: 'text-brand-primary',

  citations: 'mt-1.5 flex flex-col gap-1',
  citation:
    'flex w-full cursor-pointer items-start gap-1.5 rounded px-1 py-0.5 text-left text-[0.72rem] leading-[1.5] text-sample-muted hover:bg-app-canvas hover:text-app-ink focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-brand-primary',
  citationNumber:
    'mt-px grid size-[0.9375rem] flex-none place-items-center rounded bg-brand-accent text-[0.5625rem] font-semibold text-brand-primary',
  citationPopover:
    'mt-1 flex flex-col gap-2 rounded-[0.6rem] border border-sample-border bg-white p-2.5 text-left text-[0.72rem] leading-[1.6] shadow-[0_10px_30px_-12px_rgb(0_0_0_/_20%)]',
  citationPopoverBody: 'm-0 text-sample-muted',
  citationPopoverDate: 'text-[0.625rem] text-[#98a0a6]',

  actions: 'mt-2.5 flex flex-wrap gap-1.5',
  relatedBlock: 'mt-3',
  actionPrimary:
    'inline-flex min-h-9 items-center gap-[0.3125rem] rounded-full bg-brand-primary px-3 py-[0.3125rem] text-[0.72rem] font-medium text-white hover:bg-[#066538] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-primary',
  actionSoft:
    'inline-flex min-h-9 items-center gap-[0.3125rem] rounded-full bg-brand-accent px-3 py-[0.3125rem] text-[0.72rem] font-medium text-brand-primary hover:bg-[#d7ecdf] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-primary',

  // 점 세 개는 사람이 타이핑 중이라는 신호라 AI 답변에는 상태 문구와 스켈레톤을 씁니다.
  pendingStatus: 'flex items-center gap-[0.4375rem] text-[0.75rem] text-sample-muted',
  pendingDot: 'size-[0.4375rem] flex-none rounded-full bg-[#9aa1a8]',
  pendingLines: 'mt-2 flex flex-col gap-1.5',
  pendingLine: 'h-[0.5625rem] rounded bg-[#eef1f3]',

  warning: 'rounded-[0.6rem] border border-[#f0d9bd] bg-[#fffaf3] px-3 py-[0.6875rem] text-[0.75rem] leading-[1.65]',
  warningTitle: 'm-0 mb-0.5 text-[0.78rem] font-semibold text-app-ink',
  warningNote: 'm-0 text-sample-muted',

  latestToast:
    'absolute inset-x-0 top-2.5 z-10 mx-auto flex w-fit cursor-pointer items-center gap-1.5 rounded-full bg-app-ink px-3 py-[0.3125rem] text-[0.72rem] text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-primary',

  // 입력창은 테두리 하나로 묶고 전송 버튼을 그 안에 둡니다. 두 제품 모두 같은 형태였습니다.
  footer: 'flex-none bg-white px-3 pt-1 pb-[max(0.625rem,env(safe-area-inset-bottom))]',
  composer:
    'flex items-end gap-2 rounded-xl border border-sample-border bg-white px-2.5 py-2 focus-within:border-brand-primary focus-within:ring-2 focus-within:ring-brand-primary/10',
  composerInput:
    'max-h-20 min-h-6 flex-1 resize-none border-0 bg-transparent text-[0.78rem] leading-[1.5] text-app-ink placeholder:text-[#9aa1a8] outline-0 [field-sizing:content]',
  composerSend:
    'grid size-7 flex-none cursor-pointer place-items-center rounded-full bg-brand-primary text-[0.72rem] text-white hover:bg-[#066538] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-primary disabled:cursor-not-allowed disabled:bg-[#ccd1d5]',
  composerStop:
    'grid size-7 flex-none cursor-pointer place-items-center rounded-full bg-app-ink text-[0.72rem] text-white hover:bg-black focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-primary',
  footerNote: 'm-0 px-1 pt-1.5 text-[0.655rem] text-[#98a0a6]',
} as const
