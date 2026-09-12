// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from './App'
import { createAppStore } from './app/store'
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
  // 작업 화면(/partners 등)은 회원 세션이 있어야 열립니다. 세션 복원 요청은 보내지 않습니다.
  store.dispatch(sessionRestored(
    signedIn ? { email: 'member@govbiz.local', role: 'USER', tier: 'MEMBER', emailVerified: true, hasPassword: true, company: null } : null,
  ))
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[path]}><App /></MemoryRouter>
    </Provider>,
  )
}

describe('공개 요금제', () => {
  it.each(['/pricing', '/app/pricing'])('%s 제목은 전체 접근성 이름과 글자 공간을 유지하며 순서대로 등장한다', (path) => {
    renderApp(path)
    const title = '기업의 다음 단계에 맞는 요금제'
    const heading = screen.getByRole('heading', { level: 1, name: title })
    expect(heading.textContent).toBe(title)
    expect(heading.querySelector('[aria-hidden="true"]')).toBeTruthy()
    const characters = Array.from(heading.querySelectorAll<HTMLElement>('[data-pricing-title-character]'))
    expect(characters.map((node) => node.textContent).join('')).toBe(title.replaceAll(' ', ''))
    expect(characters.every((node) => node.classList.contains('motion-safe:animate-search-intro-type'))).toBe(true)
    expect(characters[0].style.animationDelay).toBe('180ms')
    expect(characters.every((node, index) => index === 0 || parseInt(node.style.animationDelay) > parseInt(characters[index - 1].style.animationDelay))).toBe(true)
    expect(screen.queryByText('GovBiz 요금제', { exact: true })).toBeNull()
  })

  it.each(['/pricing', '/pricing/'])('%s에서 세 요금제를 보여주고 결제 요청은 보내지 않는다', (path) => {
    renderApp(path)

    expect(screen.queryByText('GovBiz 요금제', { exact: true })).toBeNull()
    expect(screen.getByRole('heading', { level: 1, name: '기업의 다음 단계에 맞는 요금제' })).toBeTruthy()
    for (const name of ['무료', '프로', '팀']) {
      expect(screen.getByRole('heading', { name })).toBeTruthy()
    }
    // 준비 중 표시 없이 지금 쓸 수 있는 것으로 안내하고, 요금제 화면 자체는 요청을 보내지 않습니다.
    expect(screen.queryByText(/준비 중/)).toBeNull()
    expect(screen.getAllByRole('link', { name: '무료로 시작하기' })).toHaveLength(3)
    expect(fetch).not.toHaveBeenCalled()

    const navigation = screen.getByRole('navigation', { name: '화면 이동' })
    expect(within(navigation).getByRole('link', { name: '요금제' }).getAttribute('aria-current')).toBe('page')
    expect(within(navigation).getByRole('link', { name: '지원사업 찾기' }).getAttribute('aria-current')).toBeNull()
    expect(screen.getAllByRole('link', { name: '무료로 시작하기' })[0].getAttribute('href')).toBe('/')
    expect(screen.getByRole('link', { name: '지원사업 찾기 시작하기' }).getAttribute('href')).toBe('/')
  })

  it('공개 검색 상단 메뉴에서 요금제로 이동한다', () => {
    renderApp('/')
    fireEvent.click(screen.getByRole('link', { name: '요금제' }))
    expect(screen.getByRole('heading', { level: 1, name: '기업의 다음 단계에 맞는 요금제' })).toBeTruthy()
  })

  it('작업 사이드바에서 요금제를 열면 사이드바 안에 머물고 무료 버튼은 작업 채팅으로 간다', () => {
    renderApp('/app/partners')
    const sidebar = screen.getByRole('complementary', { name: '작업 사이드바' })
    fireEvent.click(within(sidebar).getByRole('link', { name: '요금제' }))

    expect(screen.queryByText('GovBiz 요금제', { exact: true })).toBeNull()
    expect(screen.getByRole('heading', { level: 1, name: '기업의 다음 단계에 맞는 요금제' })).toBeTruthy()
    for (const name of ['무료', '프로', '팀']) {
      expect(screen.getByRole('heading', { name })).toBeTruthy()
    }
    expect(screen.getByRole('complementary', { name: '작업 사이드바' })).toBeTruthy()
    expect(within(sidebar).getByRole('link', { name: '요금제' }).getAttribute('aria-current')).toBe('page')
    expect(fetch).not.toHaveBeenCalled()
    expect(screen.getAllByRole('link', { name: '무료로 시작하기' })[0].getAttribute('href')).toBe('/app/chat')
    fireEvent.click(screen.getAllByRole('link', { name: '무료로 시작하기' })[0])
    expect(screen.getByRole('textbox', { name: '지원사업 검색어' })).toBeTruthy()
    expect(screen.getByRole('complementary', { name: '작업 사이드바' })).toBeTruthy()
  })

  it('로그인 상태로 공개 요금제 주소에 오면 사이드바 안의 요금제로 보낸다', () => {
    renderApp('/pricing', true)
    expect(screen.queryByText('GovBiz 요금제', { exact: true })).toBeNull()
    expect(screen.getByRole('heading', { level: 1, name: '기업의 다음 단계에 맞는 요금제' })).toBeTruthy()
    expect(screen.getByRole('complementary', { name: '작업 사이드바' })).toBeTruthy()
    expect(screen.queryByRole('banner', { name: '앱 헤더' })).toBeNull()
  })
})
