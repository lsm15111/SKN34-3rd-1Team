// 색상이나 CSS 속성이 아니라 도우미 화면에서 맡는 UI 역할을 이름으로 사용합니다.
// 카카오 채널 상담 화면처럼 왼쪽 안내 말풍선과 오른쪽 메뉴 버튼으로 구성합니다.
export const helpAssistantStyles = {
  launcher:
    'fixed right-6 bottom-6 z-40 grid size-[3.25rem] cursor-pointer place-items-center rounded-full text-lg font-bold text-white shadow-[0_6px_20px_-6px_rgb(8_127_70_/_55%)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-primary max-chat:right-3.5 max-chat:bottom-[max(0.875rem,env(safe-area-inset-bottom))] max-chat:size-[2.875rem] max-chat:text-base',
  launcherClosed: 'bg-brand-primary hover:bg-[#066538]',
  launcherOpen: 'bg-app-ink text-base hover:bg-black',
  /** 검색 화면은 아래에 입력창이 있어 버튼을 그만큼 올립니다. */
  launcherLifted: 'bottom-[5.75rem] max-chat:bottom-[max(5rem,env(safe-area-inset-bottom))]',

  panel:
    'fixed right-6 bottom-[5.5rem] z-50 flex max-h-[38rem] w-[23rem] flex-col overflow-hidden rounded-2xl border border-sample-border bg-white text-app-ink shadow-[0_2px_4px_rgb(23_27_25_/_6%),0_18px_40px_-18px_rgb(23_27_25_/_28%)] motion-safe:animate-help-panel-enter max-chat:inset-0 max-chat:max-h-none max-chat:w-auto max-chat:rounded-none max-chat:border-0',
  panelLifted: 'bottom-[9.75rem] max-chat:bottom-0',

  header: 'flex flex-none items-center justify-between gap-2 border-b border-sample-border px-4 py-3',
  headerTitle: 'm-0 flex items-center gap-2 text-[0.9rem] font-bold',
  headerDot: 'size-[0.4375rem] flex-none rounded-full bg-brand-primary',
  headerClose:
    'grid size-7 flex-none cursor-pointer place-items-center rounded-full text-[0.95rem] text-sample-muted hover:bg-app-canvas hover:text-app-ink focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-brand-primary',

  body: 'flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto overscroll-contain bg-white px-4 py-4',
  log: 'flex flex-col gap-4',

  // 안내는 왼쪽 말풍선, 사용자가 고른 메뉴는 오른쪽 말풍선입니다.
  botTurn: 'flex flex-col items-start gap-1.5',
  bubble:
    'max-w-[85%] rounded-[0.25rem_1rem_1rem_1rem] bg-app-canvas px-3.5 py-3 text-[0.82rem] leading-[1.7] break-keep [overflow-wrap:anywhere]',
  bubbleParagraph: 'm-0 mb-2 last:mb-0',
  bubbleDivider: 'my-3 h-px bg-sample-border',
  bubbleSectionTitle: 'm-0 mb-1 flex items-center gap-1.5 text-[0.8rem] font-bold',
  bubbleList: 'm-0 flex list-disc flex-col gap-0.5 pl-5 text-[0.8rem] text-sample-muted',
  sender: 'flex items-center gap-1.5 pl-1 text-[0.68rem] text-sample-muted',
  senderMark:
    'grid size-4 place-items-center rounded-full bg-brand-accent text-[0.5rem] font-bold text-brand-primary',

  userTurn: 'flex justify-end',
  userBubble:
    'max-w-[85%] rounded-[1rem_0.25rem_1rem_1rem] bg-brand-accent px-3.5 py-2.5 text-[0.82rem] leading-[1.6] font-medium text-[#0a5c36] break-keep',

  // 메뉴는 오른쪽으로 붙는 흰 알약 버튼입니다. 줄이 넘치면 오른쪽 정렬을 유지한 채 접힙니다.
  menu: 'flex flex-wrap justify-end gap-2',
  menuButton:
    'inline-flex min-h-10 max-w-full cursor-pointer items-center gap-1.5 rounded-full border border-sample-border bg-white px-3.5 py-2 text-[0.8rem] leading-tight break-keep text-app-ink hover:border-brand-primary hover:text-brand-primary focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-brand-primary',
  menuMark: 'flex-none text-[0.85rem]',
  menuLink:
    'inline-flex min-h-10 max-w-full items-center gap-1.5 rounded-full border border-brand-primary bg-brand-primary px-3.5 py-2 text-[0.8rem] leading-tight font-medium break-keep text-white hover:bg-[#066538] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-primary',

  footer: 'flex-none border-t border-sample-border px-4 py-2.5',
  footerNote: 'm-0 text-[0.68rem] leading-relaxed text-[#98a0a6]',
  restart:
    'cursor-pointer rounded font-medium text-brand-primary underline underline-offset-2 hover:text-[#066538] focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-brand-primary',
} as const
