// @vitest-environment jsdom

import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from '../../../App'
import { appContainer } from '../../../app/appContainer'
import { createAppStore } from '../../../app/store'
import { supportPrograms } from '../../../data/fixtures/supportPrograms'
import type { Account } from '../../../domain/entities/Account'
import type { AskAssistantResult } from '../../../domain/repositories/AssistantRepository'
import { sessionRestored, signedIn, signedOut } from '../auth/state/authSlice'
import { findHelpEntry } from '../help/helpContent'
import { assistantMessages } from './assistantMessages'
import { assistantConversationStorageKey } from './useAssistantViewModel'

vi.mock('../core-api-status/CoreApiConnectionStatus', () => ({
  CoreApiConnectionStatus: () => null,
}))

const memberAccount: Account = { email: 'member@govbiz.local', role: 'USER', tier: 'MEMBER', emailVerified: true, hasPassword: true, company: null }
const otherAccount: Account = { ...memberAccount, email: 'other@govbiz.local' }

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(() => {})))
  vi.stubEnv('VITE_KAKAO_CHANNEL_ID', '')
  vi.stubEnv('VITE_ASSISTANT_AI_ENABLED', 'true')
  window.sessionStorage.clear()
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

function deferred() {
  let resolve: (value: AskAssistantResult) => void = () => {}
  const promise = new Promise<AskAssistantResult>((done) => { resolve = done })
  return { promise, resolve }
}

const answered = (text: string): AskAssistantResult => ({
  outcome: 'answered',
  answer: { intent: 'OUT_OF_SCOPE', answer: text, citations: [], clarificationQuestion: null, searchQuery: null, accountTopic: null, navigation: null, cards: [], actions: [] },
})

describe('GovBiz 가이드 대화 안정성', () => {
  it('답을 기다리는 동안 Enter를 다시 눌러도 한 번만 보내고, 쓰던 문장은 입력창에 남긴다', async () => {
    const pending = deferred()
    const ask = vi.spyOn(appContainer.resolve('askAssistantUseCase'), 'execute').mockReturnValue(pending.promise)
    renderApp('/', null)
    const { panel, input, log } = openPanel()

    type(input, '점수가 뭐야?')
    type(input, '한 번 더')
    type(input, '또 보내기')

    expect(ask).toHaveBeenCalledTimes(1)
    expect((input as HTMLTextAreaElement).value).toBe('또 보내기')
    expect(within(log).queryByText('한 번 더')).toBeNull()
    // 기다리는 동안에는 알약도 숨깁니다.
    expect(within(panel).queryByRole('group', { name: '빠른 답변' })).toBeNull()

    await act(async () => { pending.resolve(answered('점수는 관련도예요.')) })
    expect(within(log).getByText('점수는 관련도예요.')).toBeTruthy()
  })

  it('답을 기다리는 중 새 대화를 시작하면 요청을 취소하고, 늦게 온 답을 새 대화에 붙이지 않는다', async () => {
    const pending = deferred()
    let signal: AbortSignal | undefined
    vi.spyOn(appContainer.resolve('askAssistantUseCase'), 'execute').mockImplementation((_question, abortSignal) => {
      signal = abortSignal
      return pending.promise
    })
    renderApp('/', null)
    const { panel, input, log } = openPanel()
    type(input, '오래 걸리는 질문')

    fireEvent.click(within(panel).getByRole('button', { name: assistantMessages.menu }))
    fireEvent.click(within(panel).getByRole('menuitem', { name: assistantMessages.newConversation }))
    expect(signal?.aborted).toBe(true)

    await act(async () => { pending.resolve(answered('늦게 온 답')) })
    expect(within(log).queryByText('늦게 온 답')).toBeNull()
    expect(within(log).queryByText('오래 걸리는 질문')).toBeNull()
    // 새 대화는 곧바로 다시 질문을 받습니다.
    expect(within(panel).getByRole('group', { name: '빠른 답변' })).toBeTruthy()
  })

  it('같은 대화의 질문은 같은 대화 id로 보내고, 새 대화·새로고침 복원에서 id를 바르게 바꾸거나 이어 간다', async () => {
    const ask = vi.spyOn(appContainer.resolve('askAssistantUseCase'), 'execute').mockResolvedValue(answered('첫 답'))
    renderApp('/', null)
    const { panel, input, log } = openPanel()
    type(input, '첫 질문')
    expect(await within(log).findByText('첫 답')).toBeTruthy()
    type(input, '두 번째 질문')
    await within(log).findAllByText('첫 답')
    const first = ask.mock.calls[0]![0].conversationId
    expect(ask.mock.calls[1]![0].conversationId).toBe(first)
    // 대화 본문은 보내지 않습니다.
    expect(JSON.stringify(ask.mock.calls[1]![0])).not.toContain('첫 답')

    fireEvent.click(within(panel).getByRole('button', { name: assistantMessages.menu }))
    fireEvent.click(within(panel).getByRole('menuitem', { name: assistantMessages.newConversation }))
    type(input, '새 대화 질문')
    await within(log).findByText('첫 답')
    const renewed = ask.mock.calls[2]![0].conversationId
    expect(renewed).not.toBe(first)

    cleanup()
    renderApp('/', null)
    const reopened = openPanel()
    type(reopened.input, '새로고침 뒤 질문')
    await within(reopened.log).findAllByText('첫 답')
    expect(ask.mock.calls[3]![0].conversationId).toBe(renewed)
  })

  it('로그아웃하거나 다른 계정으로 바뀌면 이전 계정의 대화를 지우고 다음 질문에 싣지 않는다', async () => {
    vi.spyOn(appContainer.resolve('browseSavedSupportProgramsUseCase'), 'execute').mockResolvedValue([
      { savedAt: '2026-09-12T10:00:00', program: { ...supportPrograms[0]!, title: '내가 담은 비공개 공고', applicationEndDate: null } },
    ])
    const store = renderApp('/app/partners', memberAccount)
    const { panel } = openPanel()
    fireEvent.click(within(panel).getByRole('button', { name: assistantMessages.quickSavedPrograms }))
    expect(await within(panel).findByText('내가 담은 비공개 공고')).toBeTruthy()
    expect(window.sessionStorage.getItem(assistantConversationStorageKey)).toContain('member@govbiz.local')

    act(() => { store.dispatch(signedIn(otherAccount)) })
    expect(screen.queryByText('내가 담은 비공개 공고')).toBeNull()
    expect(window.sessionStorage.getItem(assistantConversationStorageKey)).toBeNull()

    // 새로고침처럼 다시 그려도 다른 계정의 대화는 복원하지 않습니다.
    fireEvent.click(screen.getByRole('button', { name: assistantMessages.closeLauncher }))
    const ask = vi.spyOn(appContainer.resolve('askAssistantUseCase'), 'execute').mockResolvedValue(answered('다른 계정 답'))
    const { input, log } = openPanel()
    type(input, '질문')
    expect(await within(log).findByText('다른 계정 답')).toBeTruthy()
    expect(JSON.stringify(ask.mock.calls[0]![0])).not.toContain('비공개 공고')

    act(() => { store.dispatch(signedOut()) })
    expect(screen.queryByText('다른 계정 답')).toBeNull()
  })

  it('비로그인 대화는 로그인 뒤에도 이어 가고, 세션에 남은 다른 계정 대화는 복원하지 않는다', () => {
    const store = renderApp('/', null)
    const { panel, log } = openPanel()
    const question = findHelpEntry('search-score-meaning')!.question
    fireEvent.click(within(panel).getByRole('button', { name: question }))
    act(() => { store.dispatch(signedIn(memberAccount)) })
    expect(within(log).getByText(question)).toBeTruthy()

    cleanup()
    window.sessionStorage.setItem(assistantConversationStorageKey, JSON.stringify({
      messages: [{ id: 'u-1', role: 'user', text: '남의 대화' }], quickReplies: [], owner: 'other@govbiz.local', startedAt: new Date().toISOString(),
      conversationId: '8f1c2d3e-4b5a-4c6d-8e7f-9a0b1c2d3e4f',
    }))
    renderApp('/app/partners', memberAccount)
    openPanel()
    expect(screen.queryByText('남의 대화')).toBeNull()
    expect(screen.getByText(assistantMessages.greetingAsk)).toBeTruthy()
  })

  it('복원한 대화가 어제 시작됐으면 구분선에 날짜를 적는다', () => {
    const yesterday = new Date(Date.now() - 36 * 60 * 60 * 1000)
    window.sessionStorage.setItem(assistantConversationStorageKey, JSON.stringify({
      messages: [{ id: 'u-1', role: 'user', text: '어제 질문' }], quickReplies: [], owner: null, startedAt: yesterday.toISOString(),
      conversationId: '8f1c2d3e-4b5a-4c6d-8e7f-9a0b1c2d3e4f',
    }))
    renderApp('/', null)
    const { log } = openPanel()
    const seoul = new Date(yesterday.getTime() + 9 * 60 * 60 * 1000)
    expect(within(log).getByText(`${seoul.getUTCMonth() + 1}월 ${seoul.getUTCDate()}일`)).toBeTruthy()
    expect(within(log).getByText('어제 질문')).toBeTruthy()
  })
})

describe('GovBiz 가이드 화면별 메뉴와 권한 안내', () => {
  it('지금 화면의 도움말 질문을 메뉴 맨 앞에 두고, 메뉴를 보고 있을 때 화면을 옮기면 새 화면 기준으로 바꾼다', () => {
    renderApp('/app/saved-programs', memberAccount)
    const { panel } = openPanel()
    const replies = within(panel).getByRole('group', { name: '빠른 답변' })
    expect(within(replies).getAllByRole('button')[0]!.textContent).toBe(findHelpEntry('saved-programs-pipeline')!.question)

    // 사이드바로 신청 문서 화면으로 옮기면 메뉴 첫 질문이 바뀝니다.
    fireEvent.click(screen.getAllByRole('link', { name: /신청 문서 작성/ })[0]!)
    const moved = within(screen.getByRole('dialog', { name: assistantMessages.name })).getByRole('group', { name: '빠른 답변' })
    expect(within(moved).getAllByRole('button')[0]!.textContent).toBe(findHelpEntry('application-preparation-flow')!.question)
  })

  it('비로그인에게 회원 기능 버튼은 로그인 뒤 그 화면으로 돌아오는 링크로 준다', () => {
    renderApp('/', null)
    const { panel, log } = openPanel()
    const entry = findHelpEntry('application-preparation-flow')!
    fireEvent.click(within(panel).getByRole('button', { name: '중복 검토·신청 문서' }))
    fireEvent.click(within(panel).getByRole('button', { name: entry.question }))

    expect(within(log).getByText(assistantMessages.helpNeedsLogin)).toBeTruthy()
    const link = within(log).getByRole('link', { name: assistantMessages.loginAndOpen(entry.action!.label) })
    expect(link.getAttribute('href')).toBe(`/login?next=${encodeURIComponent(entry.action!.to)}`)
  })

  it('기업 미등록 회원에게 기업 전용 기능은 기업 등록 버튼을 먼저 주고, 기업 회원에게는 안내를 붙이지 않는다', () => {
    const entry = findHelpEntry('review-save-vs-run')!
    renderApp('/app/partners', memberAccount)
    let opened = openPanel()
    fireEvent.click(within(opened.panel).getByRole('button', { name: '중복 검토·신청 문서' }))
    fireEvent.click(within(opened.panel).getByRole('button', { name: entry.question }))
    expect(within(opened.log).getByText(assistantMessages.helpNeedsCompany)).toBeTruthy()
    const links = within(opened.log).getAllByRole('link')
    expect(links.map((link) => link.textContent)).toEqual([assistantMessages.registerCompany, entry.action!.label])
    expect(links[0]!.getAttribute('href')).toBe('/app/profile')

    cleanup()
    window.sessionStorage.clear()
    renderApp('/app/partners', { ...memberAccount, tier: 'COMPANY', company: { companyName: '넥스트웨이브', businessNumber: '2148812034' } })
    opened = openPanel()
    fireEvent.click(within(opened.panel).getByRole('button', { name: '중복 검토·신청 문서' }))
    fireEvent.click(within(opened.panel).getByRole('button', { name: entry.question }))
    expect(within(opened.log).queryByText(assistantMessages.helpNeedsCompany)).toBeNull()
    expect(within(opened.log).getAllByRole('link').map((link) => link.textContent)).toEqual([entry.action!.label])
  })

  it('다른 모달에서 누른 Esc는 가이드를 닫지 않고, 런처에서 누르면 닫는다', () => {
    renderApp('/', null)
    openPanel()
    const outside = document.createElement('div')
    outside.setAttribute('role', 'dialog')
    outside.setAttribute('aria-modal', 'true')
    outside.tabIndex = -1
    document.body.appendChild(outside)
    fireEvent.keyDown(outside, { key: 'Escape' })
    expect(screen.getByRole('dialog', { name: assistantMessages.name })).toBeTruthy()
    outside.remove()

    fireEvent.keyDown(screen.getByRole('button', { name: assistantMessages.closeLauncher }), { key: 'Escape' })
    expect(screen.queryByRole('dialog', { name: assistantMessages.name })).toBeNull()
  })
})

describe('GovBiz 가이드 실시간 출력', () => {
  it('답을 기다리는 동안 진행 상황과 조각을 보여 주고, 확정 답이 오면 그 답으로 바꾼다', async () => {
    const pending = deferred()
    vi.spyOn(appContainer.resolve('askAssistantUseCase'), 'execute').mockImplementation((_question, _signal, progress) => {
      progress?.onStatus?.('READING')
      progress?.onText?.('관심 공고 2건 중 ')
      progress?.onText?.('가장 빠른 마감은 9월 30일입니다.')
      return pending.promise
    })
    renderApp('/app/chat', memberAccount)
    const { panel, input, log } = openPanel()

    type(input, '관심 공고 마감 언제야?')

    expect(within(log).getByText('관심 공고 2건 중 가장 빠른 마감은 9월 30일입니다.')).toBeTruthy()
    expect(within(panel).getByText(assistantMessages.streamReading)).toBeTruthy()

    await act(async () => { pending.resolve(answered('관심 공고 2건입니다. 9월 30일이 가장 빠릅니다.')) })

    // 조각은 검증 전이라 남기지 않고, 확정 답 하나만 대화에 남습니다.
    expect(within(log).queryByText('관심 공고 2건 중 가장 빠른 마감은 9월 30일입니다.')).toBeNull()
    expect(within(log).getByText('관심 공고 2건입니다. 9월 30일이 가장 빠릅니다.')).toBeTruthy()
    expect(within(panel).queryByText(assistantMessages.streamReading)).toBeNull()
  })

  it('새 대화를 시작하면 흘러오던 조각도 함께 사라진다', async () => {
    const pending = deferred()
    vi.spyOn(appContainer.resolve('askAssistantUseCase'), 'execute').mockImplementation((_question, _signal, progress) => {
      progress?.onText?.('쓰다 만 문장')
      return pending.promise
    })
    renderApp('/app/chat', memberAccount)
    const { panel, input, log } = openPanel()
    type(input, '관심 공고 마감 언제야?')
    expect(within(log).getByText('쓰다 만 문장')).toBeTruthy()

    fireEvent.click(within(panel).getByRole('button', { name: assistantMessages.menu }))
    fireEvent.click(within(panel).getByRole('menuitem', { name: assistantMessages.newConversation }))

    expect(within(log).queryByText('쓰다 만 문장')).toBeNull()
    await act(async () => { pending.resolve(answered('늦게 온 답')) })
    expect(within(log).queryByText('늦게 온 답')).toBeNull()
  })
})

describe('GovBiz 가이드 실행 확인 버튼', () => {
  const saveAction = {
    kind: 'SAVE_PROGRAM' as const, label: '관심 공고함에 담기', confirm: "'예비창업패키지'을(를) 관심 공고함에 담을까요?",
    sourceCode: 'KSTARTUP', sourceProgramId: '174520',
  }
  const withSaveAction: AskAssistantResult = {
    outcome: 'answered',
    answer: {
      intent: 'SEARCH', answer: '예비창업패키지가 모집 중입니다.', citations: [], clarificationQuestion: null,
      searchQuery: '창업 지원금', accountTopic: null, navigation: { label: '검색 화면에서 찾기', to: '/app/chat' },
      cards: [], actions: [saveAction],
    },
  }

  it('버튼을 눌렀을 때만 기존 담기 API를 부르고, 한 번 실행하면 다시 누를 수 없다', async () => {
    vi.spyOn(appContainer.resolve('askAssistantUseCase'), 'execute').mockResolvedValue(withSaveAction)
    const save = vi.spyOn(appContainer.resolve('saveSupportProgramUseCase'), 'execute')
      .mockResolvedValue({ outcome: 'saved', saved: { savedAt: '2026-09-19T10:00:00', program: supportPrograms[0]! } })
    renderApp('/app/chat', memberAccount)
    const { panel, input, log } = openPanel()

    type(input, '창업 지원금 찾아줘')
    expect(await within(log).findByText(saveAction.confirm)).toBeTruthy()
    // 답만 받은 시점에는 아직 아무것도 실행하지 않습니다.
    expect(save).not.toHaveBeenCalled()

    const button = within(panel).getByRole('button', { name: saveAction.label })
    await act(async () => { fireEvent.click(button) })

    expect(save).toHaveBeenCalledTimes(1)
    expect(save.mock.calls[0]![0]).toEqual({ sourceCode: 'KSTARTUP', sourceProgramId: '174520' })
    expect(within(log).getByText(assistantMessages.actionSaved)).toBeTruthy()
    const used = within(panel).getByRole('button', { name: assistantMessages.actionDone })
    expect((used as HTMLButtonElement).disabled).toBe(true)
  })

  it.each([
    ['요청이 실패하면', () => vi.spyOn(appContainer.resolve('saveSupportProgramUseCase'), 'execute').mockRejectedValue(new Error('500'))],
    // 담기 API는 공고가 사라져도 오류가 아니라 not-found를 돌려줍니다. 이때도 담았다고 말하면 안 됩니다.
    ['공고가 사라졌으면', () => vi.spyOn(appContainer.resolve('saveSupportProgramUseCase'), 'execute').mockResolvedValue({ outcome: 'not-found' })],
  ])('%s 성공한 것처럼 말하지 않고 화면에서 직접 하도록 안내한다', async (_label, stub) => {
    vi.spyOn(appContainer.resolve('askAssistantUseCase'), 'execute').mockResolvedValue(withSaveAction)
    stub()
    renderApp('/app/chat', memberAccount)
    const { panel, log } = openPanel()

    type(within(panel).getByRole('textbox', { name: assistantMessages.placeholder }), '창업 지원금 찾아줘')
    await within(log).findByText(saveAction.confirm)
    await act(async () => { fireEvent.click(within(panel).getByRole('button', { name: saveAction.label })) })

    expect(within(log).getByText(assistantMessages.actionFailed)).toBeTruthy()
    expect(within(log).queryByText(assistantMessages.actionSaved)).toBeNull()
  })
})

function openPanel() {
  fireEvent.click(screen.getByRole('button', { name: assistantMessages.openLauncher }))
  const panel = screen.getByRole('dialog', { name: assistantMessages.name })
  return {
    panel,
    input: within(panel).getByRole('textbox', { name: assistantMessages.placeholder }),
    log: within(panel).getByRole('log', { name: '대화' }),
  }
}

function type(input: HTMLElement, text: string) {
  fireEvent.change(input, { target: { value: text } })
  fireEvent.keyDown(input, { key: 'Enter' })
}

function renderApp(initialEntry: string, account: Account | null) {
  const store = createAppStore()
  store.dispatch(sessionRestored(account))
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <App />
      </MemoryRouter>
    </Provider>,
  )
  return store
}
