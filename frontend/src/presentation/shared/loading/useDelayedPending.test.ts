// @vitest-environment jsdom
import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { useDelayedPending } from './useDelayedPending'

afterEach(() => { vi.useRealTimers() })

describe('useDelayedPending', () => {
  it('지연 시간 안에 끝난 조회는 표시하지 않는다', () => {
    vi.useFakeTimers()
    const { result, rerender } = renderHook(({ pending }) => useDelayedPending(pending, { delayMs: 200, minDurationMs: 400 }), { initialProps: { pending: true } })
    expect(result.current).toBe(false)
    act(() => { vi.advanceTimersByTime(150) })
    rerender({ pending: false })
    act(() => { vi.advanceTimersByTime(1_000) })
    expect(result.current).toBe(false)
  })

  it('지연 뒤에 켜지면 최소 표시 시간을 채운 뒤 꺼진다', () => {
    vi.useFakeTimers()
    const { result, rerender } = renderHook(({ pending }) => useDelayedPending(pending, { delayMs: 200, minDurationMs: 400 }), { initialProps: { pending: true } })
    act(() => { vi.advanceTimersByTime(200) })
    expect(result.current).toBe(true)
    rerender({ pending: false })
    act(() => { vi.advanceTimersByTime(300) })
    expect(result.current).toBe(true)
    act(() => { vi.advanceTimersByTime(100) })
    expect(result.current).toBe(false)
  })
})
