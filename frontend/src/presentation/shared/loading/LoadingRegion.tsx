import type { ReactNode } from 'react'

/**
 * 데이터가 아직 없는 첫 진입 자리입니다. 스켈레톤은 보조기기에서 숨기고(`aria-hidden`), 읽어 주는 안내는 `role="status"`로 따로 둡니다.
 * 컨테이너에 `aria-busy`를 걸어 영역이 채워지는 중임을 알립니다. 조건 변경으로 이전 목록을 유지하며 다시 읽는 경우는
 * `shared/data/ResultsRegion`이 맡고, 이 컴포넌트는 "아직 아무것도 없는" 순간만 맡습니다.
 */
export function LoadingRegion({ label, skeleton, className }: {
  /** 스크린리더가 읽는 문장입니다. 예: "모집글을 불러오는 중입니다." */
  label: string
  skeleton: ReactNode
  className?: string
}) {
  return (
    <div className={className} aria-busy="true">
      <div aria-hidden="true">{skeleton}</div>
      <p role="status" className="sr-only">{label}</p>
    </div>
  )
}
