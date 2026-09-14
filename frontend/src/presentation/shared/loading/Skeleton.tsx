/**
 * 첫 진입 자리 표시(스켈레톤) 조각입니다. 실제 내용이 놓일 자리와 비슷한 모양·높이로 두어 화면이 튀지 않게 합니다.
 * 색·움직임은 한 곳에서만 정하고(`motion-safe`에서만 깜빡임), 조각 자체는 읽어 주지 않으므로 항상 `LoadingRegion` 같은
 * `aria-hidden` 컨테이너 안에서 씁니다. 버튼·입력 같은 동작 요소나 5초 넘게 걸리는 작업에는 쓰지 않습니다.
 */
const bone = 'block rounded-full bg-[#eceff1] motion-safe:animate-pulse'

export function SkeletonLine({ width = 'w-full', height = 'h-3', className = '' }: { width?: string; height?: string; className?: string }) {
  return <span className={`${bone} ${height} ${width} ${className}`} />
}

/** 문단 자리입니다. 마지막 줄만 짧게 두어 글처럼 보이게 합니다. */
export function SkeletonText({ lines = 3, className }: { lines?: number; className?: string }) {
  const text = <div className="grid gap-2.5">
    {Array.from({ length: Math.max(1, lines) }, (_, index) => (
      <SkeletonLine key={index} width={index === lines - 1 ? 'w-2/3' : 'w-full'} />
    ))}
  </div>
  return className ? <div className={className}>{text}</div> : text
}

/** 상세 화면 자리입니다: 제목, 짧은 부제, 문단 두 덩어리. 공고·모집글·계정·기업 상세가 같은 모양을 씁니다. */
export function SkeletonDetail({ className }: { className?: string }) {
  // 바깥 className은 카드 같은 껍데기용이라 안쪽 격자와 섞지 않습니다(카드가 flex여도 배치가 깨지지 않게).
  const detail = <div className="grid gap-5">
    <div className="grid gap-3">
      <SkeletonLine width="w-1/4" />
      <SkeletonLine width="w-3/4" height="h-6" />
      <SkeletonLine width="w-1/3" />
    </div>
    <SkeletonText lines={4} />
    <SkeletonText lines={3} />
  </div>
  return className ? <div className={className}>{detail}</div> : detail
}

/** 목록 행 자리입니다: 제목 한 줄과 보조 정보 한 줄이 행마다 반복됩니다. */
export function SkeletonRows({ rows = 3, className }: { rows?: number; className?: string }) {
  const list = <div className="grid gap-4">
    {Array.from({ length: Math.max(1, rows) }, (_, index) => (
      <div key={index} className="grid gap-2">
        <SkeletonLine width="w-3/4" height="h-4" />
        <SkeletonLine width="w-1/2" />
      </div>
    ))}
  </div>
  return className ? <div className={className}>{list}</div> : list
}

/** 카드 격자 자리입니다. 격자 클래스는 실제 화면의 것을 그대로 넘겨 열 수·간격을 맞춥니다. */
export function SkeletonCards({ cards = 3, gridClassName, cardClassName = '' }: { cards?: number; gridClassName: string; cardClassName?: string }) {
  return <div className={gridClassName}>
    {Array.from({ length: Math.max(1, cards) }, (_, index) => (
      <div key={index} className={`grid min-h-44 gap-3 rounded-[1.25rem] border border-sample-border bg-white p-5 ${cardClassName}`}>
        <SkeletonLine width="w-1/3" />
        <SkeletonLine width="w-5/6" height="h-4" />
        <SkeletonLine width="w-2/3" />
        <SkeletonLine width="w-1/2" className="mt-auto" />
      </div>
    ))}
  </div>
}
