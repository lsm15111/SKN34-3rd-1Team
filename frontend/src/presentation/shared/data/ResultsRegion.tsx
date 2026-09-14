import type { ReactNode } from 'react'

import { useDelayedPending } from '../loading/useDelayedPending'
import { resultsRegionStyles } from './ResultsRegion.styles'
import type { KeyedQueryPhase } from './useKeyedQuery'

/**
 * 조건별 목록의 "데이터가 없거나 바뀌는 동안"을 한 가지 규칙으로 보여 줍니다.
 * - 첫 진입(데이터 없음): 스켈레톤 + 읽어 주는 안내
 * - 조건 변경(이전 데이터 있음): 이전 목록을 흐리게 유지 + 얇은 진행바 + `aria-busy`. 필터는 잠그지 않습니다.
 * - 실패: 이전 데이터가 있으면 인라인 배너, 없으면 호출부의 오류 화면
 * 목록·빈 상태·페이지네이션은 호출부가 `children`으로 그대로 그립니다.
 */
export function ResultsRegion({
  phase, hasData, subtle = false, loadingLabel, refreshLabel = '결과를 갱신하는 중입니다',
  failedLabel = '새 조건의 결과를 불러오지 못했습니다. 이전 결과를 보여 드리고 있어요.',
  skeleton, error, onRetry, children,
}: {
  phase: KeyedQueryPhase
  hasData: boolean
  /** 뒤로 가기·재방문처럼 캐시를 보여 주며 조용히 다시 읽을 때입니다. 흐림·진행바·안내 없이 결과가 오면 조용히 바꿉니다. */
  subtle?: boolean
  /** 첫 진입 안내입니다. 예: "공고를 불러오고 있어요…" */
  loadingLabel: string
  refreshLabel?: string
  failedLabel?: string
  skeleton: ReactNode
  /** 이전 데이터가 없을 때의 실패 화면입니다. */
  error: ReactNode
  onRetry: () => void
  children: ReactNode
}) {
  // 캐시를 보여 주는 조용한 재확인은 눈에 보이는 표시를 두지 않습니다(다른 사이트들의 관례). 사용자가 조건을 바꾼 조회만 0.2초 뒤에 알립니다.
  const refreshing = useDelayedPending(phase === 'loading' && hasData) && !subtle
  const busy = phase === 'loading'

  if (!hasData) {
    return (
      <div className={resultsRegionStyles.root} aria-busy={busy}>
        {phase === 'failed' ? error : (
          <>
            <div aria-hidden="true">{skeleton}</div>
            <p role="status" className="sr-only">{loadingLabel}</p>
          </>
        )}
      </div>
    )
  }

  return (
    <div className={resultsRegionStyles.root} aria-busy={busy}>
      {refreshing ? (
        <>
          <div className={resultsRegionStyles.refreshTrack} aria-hidden="true"><span className={resultsRegionStyles.refreshSweep} /></div>
          <p role="status" className="sr-only">{refreshLabel}</p>
        </>
      ) : null}
      {phase === 'failed' ? (
        <div role="alert" className={resultsRegionStyles.inlineError}>
          <span>{failedLabel}</span>
          <button type="button" className={resultsRegionStyles.inlineErrorButton} onClick={onRetry}>다시 불러오기</button>
        </div>
      ) : null}
      <div className={refreshing ? resultsRegionStyles.stale : undefined}>{children}</div>
    </div>
  )
}
