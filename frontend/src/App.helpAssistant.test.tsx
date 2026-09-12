// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from './App'
import { createAppStore } from './app/store'
import { helpAssistantMenu } from './presentation/features/help-assistant/viewmodel/helpAssistantMenu'
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

function openAssistant(path = '/') {
  renderApp(path)
  fireEvent.click(screen.getByRole('button', { name: '도우미 열기' }))
  return screen.getByRole('dialog', { name: 'GovBiz 도우미' })
}

describe('도우미 버튼', () => {
  it('단독 화면에는 두지 않는다', () => {
    renderApp('/login')
    expect(screen.queryByRole('button', { name: '도우미 열기' })).toBeNull()
  })

  it('비로그인 검색 화면에도 보인다', () => {
    renderApp('/')
    expect(screen.getByRole('button', { name: '도우미 열기' })).toBeTruthy()
  })

  it('Esc로 닫으면 버튼으로 포커스가 돌아온다', () => {
    const panel = openAssistant()
    fireEvent.keyDown(panel, { key: 'Escape' })

    expect(screen.queryByRole('dialog')).toBeNull()
    expect(document.activeElement).toBe(screen.getByRole('button', { name: '도우미 열기' }))
  })
})

describe('도우미 첫 화면', () => {
  it('인사와 상담 운영 시간, 메뉴를 함께 보여 준다', () => {
    const panel = openAssistant()

    expect(within(panel).getByText(/무엇을 도와드릴까요/)).toBeTruthy()
    expect(within(panel).getByText('상담 운영 시간')).toBeTruthy()
    expect(within(panel).getByText('월-금 09:30~18:30')).toBeTruthy()
    expect(within(panel).getByText(/GovBiz, /)).toBeTruthy()

    const menu = within(panel).getByRole('navigation', { name: '도우미 메뉴' })
    for (const item of helpAssistantMenu) {
      expect(within(menu).getByRole('button', { name: new RegExp(item.label) })).toBeTruthy()
    }
  })

  it('열면 첫 메뉴에 포커스를 둔다', () => {
    const panel = openAssistant()
    const menu = within(panel).getByRole('navigation', { name: '도우미 메뉴' })

    expect(document.activeElement).toBe(within(menu).getAllByRole('button')[0])
  })
})

describe('메뉴 선택', () => {
  it('고른 메뉴와 정해진 안내, 갈 곳을 이어서 보여 준다', () => {
    const panel = openAssistant()
    const searchItem = helpAssistantMenu[0]

    const menu = within(panel).getByRole('navigation', { name: '도우미 메뉴' })
    fireEvent.click(within(menu).getByRole('button', { name: new RegExp(searchItem.label) }))

    const log = within(panel).getByRole('log')
    expect(within(log).getByText(searchItem.label)).toBeTruthy()
    expect(within(log).getByText(searchItem.reply)).toBeTruthy()
    expect(within(log).getByRole('link', { name: searchItem.action!.label }).getAttribute('href')).toBe('/')
  })

  it('이 단계는 화면만 있으므로 메뉴를 눌러도 요청이 늘지 않는다', () => {
    const panel = openAssistant()
    const menu = within(panel).getByRole('navigation', { name: '도우미 메뉴' })
    const before = vi.mocked(fetch).mock.calls.length

    for (const item of helpAssistantMenu) {
      fireEvent.click(within(menu).getByRole('button', { name: new RegExp(item.label) }))
    }

    expect(vi.mocked(fetch).mock.calls).toHaveLength(before)
  })

  it('로그인해야 여는 화면은 비로그인에서 로그인 뒤 돌아오게 한다', () => {
    const panel = openAssistant()
    const savedItem = helpAssistantMenu.find((item) => item.action?.requiresSignIn)!

    fireEvent.click(within(within(panel).getByRole('navigation', { name: '도우미 메뉴' }))
      .getByRole('button', { name: new RegExp(savedItem.label) }))

    const link = within(panel).getByRole('link', { name: /관심 공고함 열기/ })
    expect(link.getAttribute('href')).toBe(`/login?next=${encodeURIComponent(savedItem.action!.to)}`)
  })

  it('로그인 뒤에는 같은 메뉴가 작업 화면으로 바로 간다', () => {
    renderApp('/app/chat')
    fireEvent.click(screen.getByRole('button', { name: '도우미 열기' }))
    const panel = screen.getByRole('dialog', { name: 'GovBiz 도우미' })
    const savedItem = helpAssistantMenu.find((item) => item.action?.requiresSignIn)!

    fireEvent.click(within(within(panel).getByRole('navigation', { name: '도우미 메뉴' }))
      .getByRole('button', { name: new RegExp(savedItem.label) }))

    expect(within(panel).getByRole('link', { name: savedItem.action!.label }).getAttribute('href'))
      .toBe(savedItem.action!.to)
  })

  it('처음으로를 누르면 인사 화면으로 되돌린다', () => {
    const panel = openAssistant()
    fireEvent.click(within(within(panel).getByRole('navigation', { name: '도우미 메뉴' }))
      .getByRole('button', { name: new RegExp(helpAssistantMenu[0].label) }))

    fireEvent.click(within(panel).getByRole('button', { name: '처음으로' }))

    expect(within(panel).queryByText(helpAssistantMenu[0].reply)).toBeNull()
    expect(within(panel).getByText('상담 운영 시간')).toBeTruthy()
  })
})
