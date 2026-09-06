function classes(...groups: string[]) {
  return groups.join(' ')
}

// 파트너 모집 화면들이 공유하는 UI 역할별 클래스입니다. 채팅·상세 화면과 같은 토큰을 씁니다.
export const recruitmentStyles = {
  page: 'mx-auto w-[min(1040px,calc(100%_-_2rem))] py-[clamp(1.5rem,5vw,3.5rem)] text-app-ink',
  narrowPage: 'mx-auto w-[min(880px,calc(100%_-_2rem))] py-[clamp(1.5rem,5vw,3.5rem)] text-app-ink',
  header: 'mb-6 flex flex-wrap items-end justify-between gap-4',
  backLink: 'inline-block text-[0.82rem] font-bold text-[#5e5fc8] no-underline hover:underline',
  eyebrow: 'mt-3 mb-1 text-[0.7rem] font-extrabold tracking-[0.12em] text-[#6471a0] uppercase',
  title: 'm-0 text-[clamp(1.5rem,3.5vw,2.1rem)] font-bold leading-[1.25] tracking-[-0.04em]',
  description: 'mt-2 mb-0 text-[0.9rem] leading-[1.6] text-[#536087]',
  headerActions: 'flex flex-wrap items-center gap-2',
  primaryButton: classes(
    'inline-grid min-h-11 cursor-pointer place-items-center rounded-[0.7rem] border-0 bg-brand-primary px-4',
    'text-sm font-extrabold text-white no-underline hover:bg-[#5051b8] disabled:cursor-not-allowed disabled:opacity-45',
  ),
  secondaryButton: classes(
    'inline-grid min-h-11 cursor-pointer place-items-center rounded-[0.7rem] border border-[#d7dcef] bg-white px-4',
    'text-sm font-extrabold text-[#5e5fc8] no-underline hover:border-[#7774d7] disabled:cursor-not-allowed disabled:opacity-45',
  ),
  dangerButton: classes(
    'inline-grid min-h-11 cursor-pointer place-items-center rounded-[0.7rem] border border-[#dcaab2] bg-white px-4',
    'text-sm font-extrabold text-[#8f3340] hover:bg-[#fff5f6] disabled:cursor-not-allowed disabled:opacity-45',
  ),
  notice: 'mb-5 rounded-[0.8rem] border border-[#d8ddec] bg-[#f7f8fc] px-4 py-3 text-[0.82rem] leading-[1.55] text-[#3e4a6d]',
  error: 'mb-5 rounded-[0.8rem] border border-[#f0cfd4] bg-[#fff5f6] px-4 py-3 text-[0.82rem] leading-[1.55] text-[#9a3947]',
  empty: 'rounded-2xl border border-dashed border-[#d7dcef] bg-white px-6 py-12 text-center text-sm text-[#6d7898]',
  cardList: 'grid gap-4',
  card: 'rounded-2xl border border-[#e4e8f5] bg-white p-5 shadow-[0_10px_30px_rgb(47_67_129_/_7%)]',
  cardOwn: 'border-dashed border-[#a9b0e0]',
  cardTop: 'flex flex-wrap items-center justify-between gap-2',
  programTag: 'rounded-[0.35rem] bg-[#f0f2ff] px-[0.48rem] py-1 text-[0.68rem] font-extrabold text-[#5e5fc8]',
  deadline: 'text-[0.74rem] font-extrabold text-[#b75561]',
  cardTitle: 'mt-3 mb-1 text-[1.02rem] font-bold tracking-[-0.025em]',
  cardTitleLink: 'text-app-ink no-underline hover:text-[#504ebd]',
  cardProgram: 'm-0 text-[0.78rem] text-[#7883a3]',
  cardCompany: 'mt-3 flex items-center gap-2 text-[0.8rem] font-bold text-[#3e4a6d]',
  companyMark: 'grid size-7 place-items-center rounded-[0.6rem] bg-[#eef0fb] text-[0.78rem] font-black text-[#3b4b8a]',
  chipRow: 'mt-3 flex flex-wrap gap-[0.4rem]',
  chip: 'rounded-full bg-[#f4f6fc] px-[0.6rem] py-[0.28rem] text-[0.72rem] font-bold text-[#536087]',
  chipAccent: 'rounded-full bg-[#f0f9e9] px-[0.6rem] py-[0.28rem] text-[0.72rem] font-bold text-[#536d37]',
  cardFooter: 'mt-4 flex flex-wrap items-center justify-between gap-2 text-[0.74rem] text-[#7883a3]',
  statusBadge: 'rounded-full px-[0.6rem] py-[0.28rem] text-[0.7rem] font-extrabold',
  statusOpen: 'bg-[#eaf7ee] text-[#2f7d4f]',
  statusClosed: 'bg-[#f4f6fc] text-[#6d7898]',
  statusHidden: 'bg-[#fdeef0] text-[#b13a46]',
  pagination: 'mt-6 flex items-center justify-between gap-3 text-sm text-[#536087]',
  pageButton: 'cursor-pointer rounded-[0.55rem] border border-[#d7dcef] bg-white px-3 py-[0.4rem] text-[0.78rem] font-bold text-[#5e5fc8] disabled:cursor-not-allowed disabled:opacity-40',
  detailLayout: 'grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]',
  section: 'rounded-3xl border border-[#e1e6f4] bg-white p-[clamp(1.3rem,3.5vw,2rem)]',
  sectionTitle: 'm-0 mb-4 text-[1.05rem] font-bold tracking-[-0.03em]',
  conditionGrid: 'grid grid-cols-2 gap-3 max-chat:grid-cols-1',
  conditionItem: 'rounded-xl bg-[#f7f8fc] p-4',
  conditionLabel: 'mb-1 text-[0.7rem] font-extrabold tracking-[0.08em] text-[#6471a0] uppercase',
  conditionValue: 'text-[0.92rem] font-bold text-[#26305a]',
  body: 'whitespace-pre-line leading-[1.75] text-[#293454]',
  programCard: 'rounded-2xl bg-[#f6f8ff] p-5',
  programTitle: 'm-0 mt-2 text-[1rem] font-bold leading-[1.4]',
  programMeta: 'mt-2 mb-0 text-[0.8rem] leading-[1.6] text-[#536087]',
  programLinks: 'mt-4 flex flex-wrap gap-2',
  linkButton: 'rounded-[0.55rem] bg-[#f1f2ff] px-[0.7rem] py-[0.5rem] text-[0.74rem] font-extrabold text-[#5e5fc8] no-underline',
  hint: 'mt-3 mb-0 text-[0.74rem] leading-[1.5] text-[#7b86a3]',
  form: 'grid gap-6',
  fieldset: 'grid gap-4 rounded-3xl border border-[#e1e6f4] bg-white p-[clamp(1.3rem,3.5vw,2rem)]',
  legend: 'px-1 text-[0.95rem] font-bold',
  field: 'grid gap-2 text-sm font-bold text-[#3e4a6d]',
  input: classes(
    'min-h-11 rounded-[0.7rem] border border-[#d7dcef] bg-white px-4 text-sm font-normal text-[#1b2544] outline-0',
    'focus:border-[#7774d7] focus:shadow-[0_0_0_3px_rgb(119_116_215_/_15%)] aria-[invalid]:border-[#dcaab2]',
  ),
  textarea: classes(
    'min-h-40 resize-y rounded-[0.7rem] border border-[#d7dcef] bg-white px-4 py-3 text-sm font-normal leading-[1.6] text-[#1b2544] outline-0',
    'focus:border-[#7774d7] focus:shadow-[0_0_0_3px_rgb(119_116_215_/_15%)] aria-[invalid]:border-[#dcaab2]',
  ),
  fieldHint: 'text-xs font-normal leading-5 text-[#8a94ae]',
  fieldError: 'text-xs font-normal leading-5 text-[#9a3947]',
  radioRow: 'flex flex-wrap gap-2',
  radioOption: classes(
    'flex cursor-pointer items-center gap-2 rounded-[0.7rem] border border-[#d7dcef] bg-white px-3 py-2 text-sm font-bold text-[#3e4a6d]',
    'has-[:checked]:border-[#7774d7] has-[:checked]:bg-[#f1f2ff] has-[:checked]:text-[#504ebd]',
  ),
  twoColumns: 'grid gap-4 sm:grid-cols-2',
  searchRow: 'grid gap-2 sm:grid-cols-[1fr_auto]',
  searchResults: 'grid gap-2',
  searchResult: classes(
    'grid cursor-pointer gap-1 rounded-xl border border-[#e4e8f5] bg-white p-3 text-left',
    'hover:border-[#7774d7]',
  ),
  searchResultTitle: 'text-[0.9rem] font-bold text-app-ink',
  searchResultMeta: 'text-[0.74rem] text-[#7883a3]',
  formActions: 'flex flex-wrap items-center justify-end gap-2',
  tabs: 'mb-5 flex gap-2 border-b border-[#e1e6f4]',
  tab: classes(
    'cursor-pointer border-0 border-b-2 border-transparent bg-transparent px-1 pb-3 text-sm font-bold text-[#6d7898] no-underline',
    'hover:text-[#504ebd]',
  ),
  tabActive: 'border-[#5e5fc8] text-[#504ebd]',
  proposalMessage: 'mt-3 mb-0 whitespace-pre-line text-[0.9rem] leading-[1.65] text-[#293454]',
  contactBox: 'mt-3 mb-0 rounded-xl bg-[#eaf7ee] px-4 py-3 text-[0.82rem] font-bold text-[#2f7d4f]',
  contactLink: 'text-[#2f7d4f] underline',
  cardActions: 'mt-4 flex flex-wrap gap-2',
  proposalPending: 'bg-[#fff4e0] text-[#9a6412]',
  proposalAccepted: 'bg-[#eaf7ee] text-[#2f7d4f]',
  proposalEnded: 'bg-[#f4f6fc] text-[#6d7898]',
} as const

export function proposalStatusClassName(status: 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'WITHDRAWN' | 'EXPIRED' | 'CLOSED') {
  const variant = status === 'PENDING'
    ? recruitmentStyles.proposalPending
    : status === 'ACCEPTED' ? recruitmentStyles.proposalAccepted : recruitmentStyles.proposalEnded
  return `${recruitmentStyles.statusBadge} ${variant}`
}

export function recruitmentStatusClassName(status: 'OPEN' | 'CLOSED' | 'HIDDEN') {
  const variant = status === 'OPEN'
    ? recruitmentStyles.statusOpen
    : status === 'HIDDEN' ? recruitmentStyles.statusHidden : recruitmentStyles.statusClosed
  return `${recruitmentStyles.statusBadge} ${variant}`
}
