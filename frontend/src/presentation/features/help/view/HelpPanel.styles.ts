// 색상이나 CSS 속성이 아니라 도움말 패널에서 맡는 UI 역할을 이름으로 사용합니다.
// 치수·색은 도움말 챗봇 화면 명세의 확정값이며 새 색 토큰을 추가하지 않습니다.
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
  header: 'flex flex-none items-center justify-between gap-2 border-b border-sample-border px-3.5 py-3',
  headerTitle: 'm-0 flex items-center gap-2 text-[0.845rem] font-semibold',
  headerDot: 'size-[0.4375rem] flex-none rounded-full bg-brand-primary',
  headerClose:
    'grid size-7 cursor-pointer place-items-center rounded-full text-[0.95rem] text-sample-muted hover:bg-app-canvas hover:text-app-ink focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-brand-primary',

  body: 'relative flex min-h-0 flex-1 flex-col gap-2.5 overflow-y-auto overscroll-contain bg-app-canvas p-3.5',
  scope: 'm-0 rounded-[0.55rem] bg-brand-accent px-3 py-2.5 text-[0.72rem] leading-[1.62] text-[#0a5c36]',
  scopeStrong: 'font-semibold',

  suggestions: 'flex flex-col gap-[0.4375rem]',
  suggestionsLabel: 'text-[0.625rem] font-semibold tracking-[0.07em] text-sample-muted uppercase',
  suggestionButton:
    'flex w-full min-h-11 cursor-pointer items-center justify-between gap-2 rounded-[0.55rem] border border-sample-border bg-white px-3 py-[0.5625rem] text-left text-[0.78rem] leading-[1.5] text-app-ink hover:border-brand-primary focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-brand-primary',
  suggestionArrow: 'flex-none text-[0.69rem] text-sample-muted',

  question: 'max-w-[80%] self-end rounded-[0.75rem_0.75rem_0.2rem_0.75rem] bg-app-ink px-3 py-2 text-[0.78rem] leading-[1.6] text-white [overflow-wrap:anywhere]',
  answer: 'rounded-[0.75rem_0.75rem_0.75rem_0.2rem] border border-sample-border bg-white px-[0.8125rem] py-3 text-[0.8125rem] leading-[1.72] [overflow-wrap:anywhere]',
  answerFlag: 'mb-[0.4375rem] inline-flex items-center gap-1 rounded-[0.25rem] bg-[#f0f2f4] px-1.5 py-[0.0625rem] text-[0.625rem] font-medium text-sample-muted',
  answerLead: 'm-0 mb-2 font-semibold',
  answerParagraph: 'm-0 mb-2 last:mb-0',
  answerLimitation: 'm-0 mb-2 text-sample-muted last:mb-0',
  answerPreparing: 'mb-2 inline-flex items-center rounded-[0.25rem] bg-[#fffaf3] px-1.5 py-[0.0625rem] text-[0.625rem] font-medium text-[#9a5b1d]',

  citations: 'mt-2.5 flex flex-col gap-[0.3125rem] border-t border-dashed border-sample-border pt-[0.5625rem]',
  citation:
    'flex w-fit cursor-pointer items-center gap-[0.4375rem] rounded text-left text-[0.72rem] text-brand-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-brand-primary',
  citationMark: 'text-[0.56rem] opacity-75',
  citationPopover:
    'absolute z-10 mt-1 flex w-[15.625rem] flex-col gap-2 rounded-[0.6rem] border border-sample-border bg-white p-3 text-left text-[0.72rem] leading-[1.6] shadow-[0_10px_30px_-12px_rgb(0_0_0_/_32%)]',
  citationPopoverTitle: 'm-0 text-[0.75rem] font-semibold text-app-ink',
  citationPopoverBody: 'm-0 text-sample-muted',
  citationPopoverFooter: 'flex items-center justify-between border-t border-sample-border pt-2',
  citationPopoverDate: 'text-[0.625rem] text-sample-muted',

  actions: 'mt-2.5 flex flex-wrap gap-1.5',
  actionPrimary:
    'inline-flex min-h-9 items-center gap-[0.3125rem] rounded-full bg-brand-primary px-3 py-[0.3125rem] text-[0.72rem] font-medium text-white hover:bg-[#066538] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-primary',
  actionSoft:
    'inline-flex min-h-9 items-center gap-[0.3125rem] rounded-full bg-brand-accent px-3 py-[0.3125rem] text-[0.72rem] font-medium text-brand-primary hover:bg-[#d7ecdf] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-primary',

  // 점 세 개는 사람이 타이핑 중이라는 신호라 AI 답변에는 상태 문구와 스켈레톤을 씁니다.
  pendingStatus: 'flex items-center gap-[0.4375rem] py-0.5 text-[0.75rem] text-sample-muted',
  pendingDot: 'size-[0.4375rem] flex-none rounded-full bg-[#9aa1a8]',
  pendingLines: 'mt-[0.5625rem] flex flex-col gap-1.5',
  pendingLine: 'h-[0.5625rem] rounded bg-[#e9ecef]',

  warning: 'rounded-[0.6rem] border border-[#f0d9bd] bg-[#fffaf3] px-3 py-[0.6875rem] text-[0.75rem] leading-[1.65]',
  warningTitle: 'm-0 mb-0.5 text-[0.78rem] font-semibold text-app-ink',
  warningNote: 'm-0 text-sample-muted',

  latestToast:
    'absolute inset-x-0 top-2.5 z-10 mx-auto flex w-fit cursor-pointer items-center gap-1.5 rounded-full bg-app-ink px-3 py-[0.3125rem] text-[0.72rem] text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-primary',

  footer: 'flex-none border-t border-sample-border bg-white pb-[env(safe-area-inset-bottom)]',
  composer: 'flex items-end gap-2 px-3 py-2.5',
  composerInput:
    'max-h-20 min-h-6 flex-1 resize-none border-0 bg-transparent text-[0.78rem] leading-[1.5] text-app-ink placeholder:text-[#9aa1a8] outline-0 [field-sizing:content]',
  composerSend:
    'grid size-7 flex-none cursor-pointer place-items-center rounded-full bg-brand-primary text-[0.72rem] text-white hover:bg-[#066538] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-primary disabled:cursor-not-allowed disabled:bg-[#ccd1d5]',
  // 말풍선 옆에 두면 스트리밍으로 밀려 버튼을 쫓아가야 하므로 자리가 고정된 입력줄에 둡니다.
  composerStop:
    'grid size-7 flex-none cursor-pointer place-items-center rounded-full bg-app-ink text-[0.72rem] text-white hover:bg-black focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-primary',
  footerNote: 'm-0 px-3 pb-[0.5625rem] text-[0.655rem] text-[#98a0a6]',
} as const
