import { useCallback, useEffect, useRef, useState } from 'react'

import { useQueryCache } from './queryCacheContext'

export type KeyedQueryPhase = 'loading' | 'ready' | 'failed'

export type KeyedQueryResult<T> = {
  phase: KeyedQueryPhase
  /** 지금 보여 줄 값입니다. 조회 중에는 캐시 또는 직전 결과이고, 처음이라 아무것도 없으면 null입니다. */
  data: T | null
  /** 이전 결과를 보여 주면서 새 조건을 조회하는 중입니다. 목록을 지우지 말고 흐리게 두는 상태입니다. */
  isRefreshing: boolean
  /** 캐시에서 온 값을 보여 주면서 다시 조회하는 중입니다. */
  isStale: boolean
  retry: () => void
}

type State<T> = { key: string; phase: KeyedQueryPhase; data: T | null; fromCache: boolean }

/**
 * 조건(key)별 목록 조회의 공통 규칙입니다. 목록 화면들이 각자 복사해 쓰던 것을 한곳에 모았습니다.
 * - 키가 바뀌면 이전 요청을 취소하고 늦게 온 응답은 무시합니다(최신 요청만 반영).
 * - 조회 중에도 직전 결과를 돌려주고, 캐시 이름이 있으면 캐시를 먼저 돌려준 뒤 다시 조회합니다.
 * - `timeoutMs` 안에 끝나지 않으면 취소하고 실패로 둡니다. 실패해도 직전 결과는 유지합니다.
 * `fetch`는 최신 함수를 쓰되 다시 조회하는 조건은 key·재시도뿐입니다.
 */
export function useKeyedQuery<T>({ key, fetch, timeoutMs = 10_000, cache }: {
  key: string
  fetch: (signal: AbortSignal) => Promise<T>
  timeoutMs?: number
  /** 캐시 이름공간. 없으면 캐시하지 않고 직전 결과만 유지합니다. */
  cache?: string
}): KeyedQueryResult<T> {
  const queryCache = useQueryCache()
  const fetchRef = useRef(fetch)
  fetchRef.current = fetch
  const [version, setVersion] = useState(0)
  const [state, setState] = useState<State<T>>(() => {
    const cached = cache === undefined ? null : queryCache.get<T>(cache, key)
    return { key, phase: 'loading', data: cached, fromCache: cached !== null }
  })

  useEffect(() => {
    const controller = new AbortController()
    let current = true
    const cached = cache === undefined ? null : queryCache.get<T>(cache, key)
    setState((previous) => ({ key, phase: 'loading', data: cached ?? previous.data, fromCache: cached !== null }))
    const timer = setTimeout(() => {
      if (!current) return
      controller.abort()
      setState((previous) => ({ ...previous, key, phase: 'failed' }))
    }, timeoutMs)
    void Promise.resolve()
      .then(() => fetchRef.current(controller.signal))
      .then((data) => {
        if (!current || controller.signal.aborted) return
        if (cache !== undefined) queryCache.set(cache, key, data)
        setState({ key, phase: 'ready', data, fromCache: false })
      })
      .catch(() => {
        if (current && !controller.signal.aborted) setState((previous) => ({ ...previous, key, phase: 'failed' }))
      })
      .finally(() => clearTimeout(timer))
    return () => {
      current = false
      clearTimeout(timer)
      controller.abort()
    }
  }, [key, version, timeoutMs, cache, queryCache])

  const retry = useCallback(() => setVersion((value) => value + 1), [])

  // 키가 바뀐 직후의 한 렌더에서는 이전 키의 phase가 남아 있으므로 loading으로 봅니다. 값은 캐시가 있으면 캐시, 없으면 직전 결과입니다.
  const sameKey = state.key === key
  const cachedNow = sameKey || cache === undefined ? null : queryCache.get<T>(cache, key)
  const phase: KeyedQueryPhase = sameKey ? state.phase : 'loading'
  const data = sameKey ? state.data : cachedNow ?? state.data
  const fromCache = sameKey ? state.fromCache : cachedNow !== null
  return {
    phase,
    data,
    isRefreshing: phase === 'loading' && data !== null,
    isStale: phase === 'loading' && fromCache,
    retry,
  }
}
