// @vitest-environment jsdom
import { act, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { QueryCache } from './queryCache'
import { QueryCacheContext } from './queryCacheContext'
import { useKeyedQuery } from './useKeyedQuery'

afterEach(() => { vi.useRealTimers() })

type Deferred = { resolve: (value: string) => void; reject: (error: Error) => void; signal: AbortSignal }

/** 호출 순서대로 보류된 요청을 모아 두고 테스트가 직접 끝냅니다. */
function deferredFetch() {
  const calls: Deferred[] = []
  const fetch = vi.fn((signal: AbortSignal) => new Promise<string>((resolve, reject) => { calls.push({ resolve, reject, signal }) }))
  return { calls, fetch }
}

describe('useKeyedQuery', () => {
  it('처음에는 값 없이 loading이고 응답이 오면 ready가 된다', async () => {
    const { calls, fetch } = deferredFetch()
    const { result } = renderHook(() => useKeyedQuery({ key: 'a', fetch }))
    expect(result.current).toMatchObject({ phase: 'loading', data: null, isRefreshing: false, isStale: false })
    await waitFor(() => expect(calls).toHaveLength(1))
    await act(async () => calls[0]!.resolve('A'))
    expect(result.current).toMatchObject({ phase: 'ready', data: 'A', isRefreshing: false })
  })

  it('키가 바뀌면 이전 요청을 취소하고 직전 결과를 유지한 채 refreshing이 되며, 늦게 온 이전 응답은 무시한다', async () => {
    const { calls, fetch } = deferredFetch()
    const { result, rerender } = renderHook(({ key }) => useKeyedQuery({ key, fetch }), { initialProps: { key: 'a' } })
    await waitFor(() => expect(calls).toHaveLength(1))
    await act(async () => calls[0]!.resolve('A'))
    rerender({ key: 'b' })
    expect(result.current).toMatchObject({ phase: 'loading', data: 'A', isRefreshing: true, isStale: false })
    await waitFor(() => expect(calls).toHaveLength(2))
    expect(calls[0]!.signal.aborted).toBe(true)
    rerender({ key: 'c' })
    await waitFor(() => expect(calls).toHaveLength(3))
    await act(async () => calls[1]!.resolve('B-late'))
    expect(result.current.data).toBe('A')
    await act(async () => calls[2]!.resolve('C'))
    expect(result.current).toMatchObject({ phase: 'ready', data: 'C', isRefreshing: false })
  })

  it('실패해도 직전 결과를 유지하고 재시도하면 같은 키로 다시 조회한다', async () => {
    const { calls, fetch } = deferredFetch()
    const { result, rerender } = renderHook(({ key }) => useKeyedQuery({ key, fetch }), { initialProps: { key: 'a' } })
    await waitFor(() => expect(calls).toHaveLength(1))
    await act(async () => calls[0]!.resolve('A'))
    rerender({ key: 'b' })
    await waitFor(() => expect(calls).toHaveLength(2))
    await act(async () => calls[1]!.reject(new Error('down')))
    expect(result.current).toMatchObject({ phase: 'failed', data: 'A', isRefreshing: false })
    act(() => result.current.retry())
    await waitFor(() => expect(calls).toHaveLength(3))
    expect(result.current).toMatchObject({ phase: 'loading', data: 'A', isRefreshing: true })
    await act(async () => calls[2]!.resolve('B'))
    expect(result.current).toMatchObject({ phase: 'ready', data: 'B' })
  })

  it('제한 시간이 지나면 요청을 취소하고 실패로 둔다', async () => {
    vi.useFakeTimers()
    const { calls, fetch } = deferredFetch()
    const { result } = renderHook(() => useKeyedQuery({ key: 'a', fetch, timeoutMs: 1_000 }))
    await act(async () => { await vi.advanceTimersByTimeAsync(0) })
    expect(calls).toHaveLength(1)
    await act(async () => { await vi.advanceTimersByTimeAsync(1_001) })
    expect(calls[0]!.signal.aborted).toBe(true)
    expect(result.current.phase).toBe('failed')
  })

  it('캐시 이름이 있으면 응답을 캐시에 넣고, 다시 마운트하면 stale 값을 즉시 보여 준 뒤 재조회로 갱신한다', async () => {
    const cache = new QueryCache()
    const wrapper = ({ children }: { children: ReactNode }) => <QueryCacheContext.Provider value={cache}>{children}</QueryCacheContext.Provider>
    const { calls, fetch } = deferredFetch()
    const first = renderHook(() => useKeyedQuery({ key: 'a', fetch, cache: 'list' }), { wrapper })
    await waitFor(() => expect(calls).toHaveLength(1))
    await act(async () => calls[0]!.resolve('A'))
    expect(cache.get('list', 'a')).toBe('A')
    first.unmount()
    const second = renderHook(() => useKeyedQuery({ key: 'a', fetch, cache: 'list' }), { wrapper })
    expect(second.result.current).toMatchObject({ phase: 'loading', data: 'A', isStale: true, isRefreshing: true })
    await waitFor(() => expect(calls).toHaveLength(2))
    await act(async () => calls[1]!.resolve('A2'))
    expect(second.result.current).toMatchObject({ phase: 'ready', data: 'A2', isStale: false })
    expect(cache.get('list', 'a')).toBe('A2')
  })

  it('캐시 이름이 없으면 저장하지 않고 직전 결과만 유지한다', async () => {
    const cache = new QueryCache()
    const wrapper = ({ children }: { children: ReactNode }) => <QueryCacheContext.Provider value={cache}>{children}</QueryCacheContext.Provider>
    const { calls, fetch } = deferredFetch()
    renderHook(() => useKeyedQuery({ key: 'a', fetch }), { wrapper })
    await waitFor(() => expect(calls).toHaveLength(1))
    await act(async () => calls[0]!.resolve('A'))
    expect(cache.size).toBe(0)
  })
})
