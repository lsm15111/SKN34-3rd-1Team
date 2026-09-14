import { useEffect, useState } from 'react'

/**
 * 짧게 끝나는 조회에 로딩 표시가 번쩍이지 않게 합니다. `pending`이 `delayMs` 안에 끝나면 false로 남고,
 * 한 번 켜졌으면 `minDurationMs` 동안은 유지합니다. 처음부터 보여 줘야 하는 첫 진입 스켈레톤에는 쓰지 않습니다.
 */
export function useDelayedPending(pending: boolean, { delayMs = 200, minDurationMs = 400 }: { delayMs?: number; minDurationMs?: number } = {}): boolean {
  const [shown, setShown] = useState(false)
  const [shownAt, setShownAt] = useState<number | null>(null)

  useEffect(() => {
    if (pending) {
      if (shown) return
      const timer = setTimeout(() => {
        setShown(true)
        setShownAt(Date.now())
      }, delayMs)
      return () => clearTimeout(timer)
    }
    if (!shown) return
    const elapsed = shownAt === null ? minDurationMs : Date.now() - shownAt
    const remaining = Math.max(0, minDurationMs - elapsed)
    const timer = setTimeout(() => {
      setShown(false)
      setShownAt(null)
    }, remaining)
    return () => clearTimeout(timer)
  }, [pending, shown, shownAt, delayMs, minDurationMs])

  return shown
}
