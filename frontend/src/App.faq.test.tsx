// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from './App'
import { createAppStore } from './app/store'
import { helpEntries } from './presentation/shared/help/helpContent'
import { sessionRestored } from './presentation/shared/auth/state/authSlice'

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(() => {})))
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

function renderApp(path: string, signedIn = path.startsWith('/app')) {
  const store = createAppStore()
  store.dispatch(sessionRestored(
    signedIn ? { email: 'member@govbiz.local', role: 'USER', tier: 'MEMBER', emailVerified: true, hasPassword: true, company: null } : null,
  ))
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[path]}><App /></MemoryRouter>
    </Provider>,
  )
}

describe('자주 묻는 질문', () => {
  it('도움말 항목을 그대로 질문 목록으로 보여 준다', () => {
    renderApp('/faq')

    expect(screen.getByRole('heading', { level: 1, name: '자주 묻는 질문' })).toBeTruthy()
    for (const entry of helpEntries) {
      expect(screen.getByText(entry.question)).toBeTruthy()
    }
    expect(screen.getByRole('status').textContent).toBe(`모두 ${helpEntries.length}건입니다.`)
  })

  it('질문을 펼치면 결론과 한계, 갈 곳을 함께 보여 준다', () => {
    renderApp('/faq')
    const entry = helpEntries.find((candidate) => candidate.action)!

    fireEvent.click(screen.getByText(entry.question))

    expect(screen.getByText(entry.summary)).toBeTruthy()
    expect(screen.getByText(entry.limitation)).toBeTruthy()
    expect(screen.getAllByRole('link', { name: entry.action!.label }).length).toBeGreaterThan(0)
  })

  it('검색은 네트워크 없이 화면 안에서 걸러 준다', () => {
    renderApp('/faq')
    const searched = helpEntries[1]

    fireEvent.change(screen.getByLabelText('질문 검색'), { target: { value: searched.question } })

    expect(screen.getByText(searched.question)).toBeTruthy()
    expect(screen.getByRole('status').textContent).toContain('1건을 찾았습니다.')
    expect(fetch).not.toHaveBeenCalled()
  })

  it('찾는 답이 없으면 도움말로 안내한다', () => {
    renderApp('/faq')

    fireEvent.change(screen.getByLabelText('질문 검색'), { target: { value: '이런 질문은 없습니다' } })

    expect(screen.getByText(/찾는 질문이 없습니다/)).toBeTruthy()
  })

  it('공개 헤더에서 자주 묻는 질문으로 이동한다', () => {
    renderApp('/')
    const navigation = screen.getByRole('navigation', { name: '화면 이동' })

    fireEvent.click(within(navigation).getByRole('link', { name: '자주 묻는 질문' }))

    expect(screen.getByRole('heading', { level: 1, name: '자주 묻는 질문' })).toBeTruthy()
  })

  it('로그인 뒤에는 사이드바 안에서 같은 질문을 본다', () => {
    renderApp('/app/faq')

    expect(screen.getByRole('complementary', { name: '작업 사이드바' })).toBeTruthy()
    expect(screen.getByText(helpEntries[0].question)).toBeTruthy()
  })
})
