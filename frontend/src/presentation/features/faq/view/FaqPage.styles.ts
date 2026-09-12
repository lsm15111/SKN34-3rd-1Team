// 색상이나 CSS 속성이 아니라 자주 묻는 질문 화면에서 맡는 UI 역할을 이름으로 사용합니다.
export const faqPageStyles = {
  page: 'mx-auto flex w-[min(820px,calc(100%_-_3rem))] min-w-0 flex-col gap-10 pt-[clamp(2.5rem,5vw,4rem)] pb-16 text-app-ink max-chat:w-[calc(100%_-_2rem)] max-chat:gap-8',
  hero: 'flex flex-col gap-3',
  title: 'm-0 break-keep text-[clamp(1.75rem,3.5vw,2.6rem)] font-extrabold leading-[1.3] tracking-[-0.05em]',
  description: 'm-0 max-w-[560px] break-keep text-[0.95rem] leading-[1.8] text-sample-muted',

  searchLabel: 'sr-only',
  searchBox: 'flex items-center gap-2 rounded-2xl border border-sample-border bg-white px-4 py-3 focus-within:border-brand-primary focus-within:ring-2 focus-within:ring-brand-primary/10',
  searchMark: 'flex-none text-sample-muted',
  searchInput: 'w-full border-0 bg-transparent text-[0.95rem] leading-6 text-app-ink placeholder:text-sample-muted outline-0',
  searchCount: 'm-0 text-[0.8rem] text-sample-muted',

  group: 'flex flex-col gap-3',
  groupTitle: 'm-0 text-[0.72rem] font-extrabold tracking-[0.12em] text-brand-primary uppercase',
  list: 'm-0 flex list-none flex-col gap-2 p-0',

  item: 'overflow-hidden rounded-2xl border border-sample-border bg-white',
  question:
    'flex cursor-pointer list-none items-center justify-between gap-3 px-5 py-4 text-[0.95rem] font-bold break-keep marker:content-none focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-brand-primary [&::-webkit-details-marker]:hidden',
  questionMark: 'flex-none text-[0.85rem] text-sample-muted transition-transform group-open:rotate-180',
  answer: 'flex flex-col gap-3 border-t border-sample-border px-5 py-4 text-[0.9rem] leading-[1.85]',
  summary: 'm-0 font-bold break-keep',
  paragraph: 'm-0 break-keep text-app-ink',
  limitation: 'm-0 break-keep text-sample-muted',
  answerFooter: 'flex flex-wrap items-center justify-between gap-3 pt-1',
  action:
    'inline-flex min-h-10 items-center rounded-full bg-brand-accent px-4 text-[0.8rem] font-bold text-brand-primary hover:bg-[#d7ecdf] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-primary',
  updatedOn: 'text-[0.72rem] text-sample-muted',

  empty: 'rounded-2xl border border-dashed border-sample-border bg-white px-5 py-8 text-center text-[0.9rem] text-sample-muted',
  closing: 'flex flex-col gap-2 rounded-2xl bg-app-canvas px-5 py-5',
  closingTitle: 'm-0 text-[0.95rem] font-bold',
  closingNote: 'm-0 text-[0.85rem] leading-[1.8] text-sample-muted',
} as const
