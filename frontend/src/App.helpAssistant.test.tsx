// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from './App'
import { createAppStore } from './app/store'
import { pricingPlans } from './presentation/shared/pricing/pricingPlans'
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
  it('인사와 요금제 확인 메뉴만 보여 준다', () => {
    const panel = openAssistant()

    expect(within(panel).getByText(/무엇을 도와드릴까요/)).toBeTruthy()
    expect(within(panel).getByText(/GovBiz, /)).toBeTruthy()

    const menu = within(panel).getByRole('navigation', { name: '도우미 메뉴' })
    expect(within(menu).getAllByRole('button')).toHaveLength(1)
    expect(within(menu).getByRole('button', { name: /요금제 확인/ })).toBeTruthy()
  })

  it('안내 문구와 다른 메뉴는 두지 않는다', () => {
    const panel = openAssistant()

    expect(within(panel).queryByText(/자유 질문은 다음 단계/)).toBeNull()
    expect(within(panel).queryByText(/메뉴를 골라 안내를 받으세요/)).toBeNull()
    expect(within(panel).queryByText(/상담 운영 시간/)).toBeNull()
    for (const removed of ['지원사업 찾기', '공고 원문에 질문하기', '관심 공고함 보기', '담당자 문의하기']) {
      expect(within(panel).queryByRole('button', { name: new RegExp(removed) })).toBeNull()
    }
  })

  it('열면 메뉴에 포커스를 둔다', () => {
    const panel = openAssistant()
    const menu = within(panel).getByRole('navigation', { name: '도우미 메뉴' })

    expect(document.activeElement).toBe(within(menu).getAllByRole('button')[0])
  })
})

describe('요금제 확인', () => {
  it('요금제 화면과 같은 이름·가격·기능을 보여 준다', () => {
    const panel = openAssistant()
    const menu = within(panel).getByRole('navigation', { name: '도우미 메뉴' })

    fireEvent.click(within(menu).getByRole('button', { name: /요금제 확인/ }))

    const log = within(panel).getByRole('log')
    for (const plan of pricingPlans) {
      const card = within(log).getByRole('article', { name: plan.name })
      expect(within(card).getByText(plan.label)).toBeTruthy()
      expect(within(card).getByText(plan.price)).toBeTruthy()
      expect(within(card).getByText(plan.status)).toBeTruthy()
      for (const feature of plan.features) expect(within(card).getByText(feature)).toBeTruthy()
    }
  })

  it('요금제 화면으로 가는 링크를 함께 준다', () => {
    const panel = openAssistant()
    fireEvent.click(within(within(panel).getByRole('navigation', { name: '도우미 메뉴' }))
      .getByRole('button', { name: /요금제 확인/ }))

    expect(within(panel).getByRole('link', { name: '요금제 자세히 보기' }).getAttribute('href')).toBe('/pricing')
  })

  it('로그인 뒤에는 사이드바 안 요금제로 간다', () => {
    renderApp('/app/chat')
    fireEvent.click(screen.getByRole('button', { name: '도우미 열기' }))
    const panel = screen.getByRole('dialog', { name: 'GovBiz 도우미' })

    fireEvent.click(within(within(panel).getByRole('navigation', { name: '도우미 메뉴' }))
      .getByRole('button', { name: /요금제 확인/ }))

    expect(within(panel).getByRole('link', { name: '요금제 자세히 보기' }).getAttribute('href')).toBe('/app/pricing')
  })

  it('이 단계는 화면만 있으므로 메뉴를 눌러도 요청이 늘지 않는다', () => {
    const panel = openAssistant()
    const menu = within(panel).getByRole('navigation', { name: '도우미 메뉴' })
    const before = vi.mocked(fetch).mock.calls.length

    fireEvent.click(within(menu).getByRole('button', { name: /요금제 확인/ }))

    expect(vi.mocked(fetch).mock.calls).toHaveLength(before)
  })
})
