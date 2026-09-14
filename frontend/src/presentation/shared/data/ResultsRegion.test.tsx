// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { ResultsRegion } from './ResultsRegion'

afterEach(() => { cleanup(); vi.useRealTimers() })

function renderRegion(phase: 'loading' | 'ready' | 'failed', hasData: boolean, onRetry = vi.fn()) {
  return render(
    <ResultsRegion phase={phase} hasData={hasData} loadingLabel="공고를 불러오고 있어요…" onRetry={onRetry}
      skeleton={<div data-testid="skeleton" />} error={<div role="alert">오류 화면</div>}>
      <ul><li>이전 행</li></ul>
    </ResultsRegion>,
  )
}

describe('ResultsRegion', () => {
  it('데이터가 없으면 스켈레톤과 읽어 주는 안내를 보여 주고, 실패하면 오류 화면으로 바꾼다', () => {
    const { rerender } = renderRegion('loading', false)
    expect(screen.getByTestId('skeleton').parentElement?.getAttribute('aria-hidden')).toBe('true')
    expect(screen.getByRole('status').textContent).toBe('공고를 불러오고 있어요…')
    expect(screen.queryByText('이전 행')).toBeNull()
    rerender(
      <ResultsRegion phase="failed" hasData={false} loadingLabel="공고를 불러오고 있어요…" onRetry={vi.fn()}
        skeleton={<div data-testid="skeleton" />} error={<div role="alert">오류 화면</div>}><ul><li>이전 행</li></ul></ResultsRegion>,
    )
    expect(screen.getByRole('alert').textContent).toBe('오류 화면')
    expect(screen.queryByTestId('skeleton')).toBeNull()
  })

  it('데이터가 있으면 조회 중에도 이전 행을 유지하고, 잠시 뒤 흐림·진행바·갱신 안내를 붙인다', () => {
    vi.useFakeTimers()
    const { container } = renderRegion('loading', true)
    expect(screen.getByText('이전 행')).toBeTruthy()
    expect(container.firstElementChild?.getAttribute('aria-busy')).toBe('true')
    expect(screen.queryByRole('status')).toBeNull()
    act(() => { vi.advanceTimersByTime(200) })
    expect(screen.getByRole('status').textContent).toBe('결과를 갱신하는 중입니다')
    expect(screen.getByText('이전 행').closest('ul')?.parentElement?.className).toContain('opacity-55')
  })

  it('이전 데이터가 있을 때의 실패는 목록을 지우지 않고 인라인 배너로 재시도를 준다', () => {
    const onRetry = vi.fn()
    renderRegion('failed', true, onRetry)
    expect(screen.getByText('이전 행')).toBeTruthy()
    expect(screen.getByRole('alert').textContent).toContain('이전 결과를 보여 드리고 있어요')
    fireEvent.click(screen.getByRole('button', { name: '다시 불러오기' }))
    expect(onRetry).toHaveBeenCalledOnce()
  })
})
