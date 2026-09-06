// 비로그인 검색 제한 안내 모달의 UI 역할별 클래스입니다.
export const authGateModalStyles = {
  backdrop: 'fixed inset-0 z-20 grid place-items-center bg-[rgb(10_15_32_/_48%)] px-4',
  dialog: 'w-full max-w-[430px] rounded-[1.25rem] bg-white p-7 shadow-[0_24px_70px_rgb(11_17_40_/_25%)]',
  eyebrow: 'mb-3 text-xs font-extrabold tracking-[0.12em] text-[#6471a0] uppercase',
  title: 'm-0 text-2xl font-extrabold tracking-[-0.04em] text-[#182342]',
  description: 'mt-3 text-sm leading-6 text-[#6d7898]',
  actions: 'mt-7 grid gap-3',
  primaryAction: 'grid min-h-12 place-items-center rounded-[0.7rem] bg-brand-primary text-sm font-extrabold text-white no-underline hover:bg-[#5051b8]',
  secondaryAction: 'grid min-h-12 place-items-center rounded-[0.7rem] border border-[#d7dcef] text-sm font-extrabold text-[#5e5fc8] no-underline',
  dismissButton: 'mt-1 cursor-pointer border-0 bg-transparent text-sm text-[#8a94ae]',
} as const
