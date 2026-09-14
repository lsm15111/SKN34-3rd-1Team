export const resultsRegionStyles = {
  root: 'relative min-w-0',
  refreshTrack: 'mb-3 h-1 overflow-hidden rounded-full bg-brand-accent',
  refreshSweep: 'block h-full w-1/3 rounded-full bg-brand-primary/75 motion-safe:animate-chat-loading-sweep motion-reduce:mx-auto motion-reduce:animate-none',
  stale: 'opacity-55 motion-safe:transition-opacity motion-safe:duration-200',
  inlineError: 'mb-3 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[#f1c7cc] bg-[#fdf3f4] px-4 py-3 text-sm text-[#9a3947]',
  inlineErrorButton: 'min-h-9 cursor-pointer rounded-lg border border-[#e5a5ae] bg-white px-3 text-xs font-bold text-[#9a3947] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-primary',
} as const
