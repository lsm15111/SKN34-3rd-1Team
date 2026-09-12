// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'

import { appPaths, publicPaths } from '../../../shared/routes/appPaths'
import { helpEntriesForRoute } from '../../../shared/help/helpContent'
import { HelpLauncher } from './HelpLauncher'

function renderLauncher(pathname: string) {
  return render(<MemoryRouter initialEntries={[pathname]}><HelpLauncher /></MemoryRouter>)
}

function openPanel(pathname: string) {
  renderLauncher(pathname)
  fireEvent.click(screen.getByRole('button', { name: '도움말 열기' }))
  return screen.getByRole('dialog', { name: 'GovBiz 도움말' })
}

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

function stubAnswer(payload: unknown, status = 200) {
  const fetchMock = vi.fn(() => Promise.resolve(
    new Response(JSON.stringify(payload), { status, headers: { 'Content-Type': 'application/json' } }),
  ))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function askFreeQuestion(panel: HTMLElement, question: string) {
  const input = within(panel).getByRole('textbox', { name: '도움말 질문' })
  fireEvent.change(input, { target: { value: question } })
  fireEvent.keyDown(input, { key: 'Enter' })
}

describe('도움말 런처', () => {
  it('단독 화면에는 두지 않는다', () => {
    renderLauncher(publicPaths.login)
    expect(screen.queryByRole('button', { name: '도움말 열기' })).toBeNull()
  })

  it('비로그인 검색 화면에도 보인다', () => {
    renderLauncher(publicPaths.landing)
    expect(screen.getByRole('button', { name: '도움말 열기' })).toBeTruthy()
  })

  it('패널의 닫기 하나로 닫는다', () => {
    const panel = openPanel(appPaths.chat)
    expect(screen.getAllByRole('button', { name: '도움말 닫기' })).toHaveLength(1)
    fireEvent.click(within(panel).getByRole('button', { name: '도움말 닫기' }))
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('Esc로 닫으면 런처로 포커스가 돌아온다', () => {
    const panel = openPanel(appPaths.chat)
    fireEvent.keyDown(panel, { key: 'Escape' })
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(document.activeElement).toBe(screen.getByRole('button', { name: '도움말 열기' }))
  })
})

describe('첫 화면', () => {
  it('범위를 먼저 밝히고 이 화면의 추천 질문을 보여 준다', () => {
    const panel = openPanel(appPaths.chat)
    expect(within(panel).getByText('화면 사용법을 안내합니다.')).toBeTruthy()
    const suggestions = within(panel).getByRole('region', { name: '이 화면에서 자주 묻는 질문' })
    const questions = within(suggestions).getAllByRole('button').map((button) => button.textContent)
    expect(questions).toHaveLength(3)
    for (const entry of helpEntriesForRoute(appPaths.chat)) {
      expect(questions.some((question) => question?.startsWith(entry.question))).toBe(true)
    }
  })

  it('열면 입력에 포커스를 둔다', () => {
    openPanel(appPaths.chat)
    expect(document.activeElement).toBe(screen.getByRole('textbox', { name: '도움말 질문' }))
  })
})

describe('항목 답변', () => {
  it('추천 질문을 누르면 결론·근거·갈 곳을 보여 준다', () => {
    const panel = openPanel(appPaths.chat)
    fireEvent.click(within(panel).getByRole('button', { name: /점수는 무슨 뜻인가요/ }))

    const log = within(panel).getByRole('log')
    expect(within(log).getByText('점수는 무슨 뜻인가요?')).toBeTruthy()
    expect(within(log).getByText(/점수는 검색어와 공고의 관련도입니다/)).toBeTruthy()
    expect(within(log).getByRole('button', { name: /점수는 무엇을 뜻하나요/ })).toBeTruthy()
    expect(within(log).getByRole('link', { name: '검색 화면 열기' })).toBeTruthy()
  })

  it('AI가 만든 답이 아니므로 AI 생성 표시를 붙이지 않는다', () => {
    const panel = openPanel(appPaths.chat)
    fireEvent.click(within(panel).getByRole('button', { name: /점수는 무슨 뜻인가요/ }))
    expect(within(panel).queryByText(/AI 생성/)).toBeNull()
  })

  it('근거 항목을 누르면 요약과 갱신일을 먼저 보여 준다', () => {
    const panel = openPanel(appPaths.chat)
    fireEvent.click(within(panel).getByRole('button', { name: /점수는 무슨 뜻인가요/ }))
    fireEvent.click(within(panel).getByRole('button', { name: /점수는 무엇을 뜻하나요/ }))

    const note = within(panel).getByRole('note')
    expect(within(note).getByText('2026-09-12')).toBeTruthy()
  })

  it('이어서 물어보기로 다음 항목을 묻는다', () => {
    const panel = openPanel(appPaths.chat)
    fireEvent.click(within(panel).getByRole('button', { name: /점수는 무슨 뜻인가요/ }))
    const next = within(panel).getByRole('region', { name: '이어서 물어보기' })
    fireEvent.click(within(next).getByRole('button', { name: /확인 필요는 왜 뜨나요/ }))

    expect(within(panel).getByText(/공고의 공식 요약만으로 지역·대상 조건을 판단할 수 없을 때/)).toBeTruthy()
  })

  it('공개 화면에서는 갈 곳을 공개 경로로 바꾼다', () => {
    const panel = openPanel(publicPaths.landing)
    fireEvent.click(within(panel).getByRole('button', { name: /점수는 무슨 뜻인가요/ }))
    expect(within(panel).getByRole('link', { name: '검색 화면 열기' }).getAttribute('href')).toBe(publicPaths.landing)
  })
})

describe('직접 친 질문', () => {
  it('같은 뜻의 항목이 있으면 그 항목으로 답한다', () => {
    const panel = openPanel(appPaths.chat)
    const input = within(panel).getByRole('textbox', { name: '도움말 질문' })
    fireEvent.change(input, { target: { value: '점수는 무슨 뜻인가요?' } })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(within(panel).getByText(/점수는 검색어와 공고의 관련도입니다/)).toBeTruthy()
  })

  it('항목에 없는 질문만 AI에 보낸다', () => {
    const fetchMock = stubAnswer({ answer: '', answerStatus: 'NOT_IN_HELP', citationEntryIds: [] })
    const panel = openPanel(appPaths.chat)

    askFreeQuestion(panel, '점수는 무슨 뜻인가요?')

    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('한글 조합 중 Enter는 전송하지 않는다', () => {
    const panel = openPanel(appPaths.chat)
    const input = within(panel).getByRole('textbox', { name: '도움말 질문' })
    fireEvent.change(input, { target: { value: '점수는 무슨 뜻인가요?' } })
    fireEvent.keyDown(input, { key: 'Enter', isComposing: true })

    expect(within(panel).queryByRole('log')?.textContent ?? '').toBe('')
  })

  it('빈 입력으로는 보낼 수 없다', () => {
    const panel = openPanel(appPaths.chat)
    expect(within(panel).getByRole('button', { name: '질문 보내기' }).hasAttribute('disabled')).toBe(true)
  })
})

describe('자유 질문 답변', () => {
  it('기다리는 동안 점 세 개 대신 준비 중 상태와 스켈레톤을 보여 준다', async () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(() => {})))
    const panel = openPanel(appPaths.chat)

    askFreeQuestion(panel, '모집글에 첨부를 올릴 수 있나요?')

    expect(await within(panel).findByRole('status')).toHaveProperty('textContent', '답변을 준비하고 있습니다')
    expect(within(panel).queryByText('⋯')).toBeNull()
    expect(within(panel).getByRole('button', { name: '답변 중지' })).toBeTruthy()
  })

  it('근거가 있으면 AI 생성 표시와 인용 항목을 함께 보여 준다', async () => {
    stubAnswer({
      answer: '모집글은 기업 정보를 등록한 계정만 쓸 수 있습니다.',
      answerStatus: 'ANSWERED',
      citationEntryIds: ['recruitment-company-only'],
    })
    const panel = openPanel(appPaths.chat)

    askFreeQuestion(panel, '모집글은 누가 쓸 수 있나요?')

    expect(await within(panel).findByText(/기업 정보를 등록한 계정만/)).toBeTruthy()
    expect(within(panel).getByText('◈ AI 생성')).toBeTruthy()
    expect(within(panel).getByRole('button', { name: /파트너 모집글은 기업 정보를 등록해야 씁니다/ })).toBeTruthy()
  })

  it('공고 내용을 물으면 원문 질문으로 안내한다', async () => {
    stubAnswer({ answer: '', answerStatus: 'OUT_OF_SCOPE_PROGRAM', citationEntryIds: [] })
    const panel = openPanel(`${appPaths.supportProgramDetail}?sourceCode=BIZINFO&sourceProgramId=PBLN-1`)

    askFreeQuestion(panel, '이 공고 지원 대상이 누구야?')

    expect(await within(panel).findByText(/공고 내용은 도움말에서 답하지 않습니다/)).toBeTruthy()
    expect(within(panel).getByRole('button', { name: '이 공고에 질문하기' })).toBeTruthy()
  })

  it('공고를 보고 있으면 같은 패널에서 공고 원문 근거로 이어서 답한다', async () => {
    const fetchMock = vi.fn((url: string) => Promise.resolve(new Response(
      JSON.stringify(url.includes('/api/v1/help/answers')
        ? { answer: '', answerStatus: 'OUT_OF_SCOPE_PROGRAM', citationEntryIds: [] }
        : {
          answer: '중소기업이 신청할 수 있습니다.',
          answerStatus: 'ANSWERED',
          citations: [{ excerpt: '신청 대상은 중소기업입니다.', sourceUrl: 'https://www.bizinfo.go.kr/web/lay1/bbs/S1T122C128/AS/74/view.do?pblancId=PBLN-1', chunkOrder: 0 }],
        }),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    )))
    vi.stubGlobal('fetch', fetchMock)
    const panel = openPanel(`${appPaths.supportProgramDetail}?sourceCode=BIZINFO&sourceProgramId=PBLN-1`)

    askFreeQuestion(panel, '이 공고 지원 대상이 누구야?')
    fireEvent.click(await within(panel).findByRole('button', { name: '이 공고에 질문하기' }))

    expect(await within(panel).findByText('중소기업이 신청할 수 있습니다.')).toBeTruthy()
    expect(within(panel).getByText('◈ AI 생성 · 공고 원문 근거')).toBeTruthy()
    expect(within(panel).getByRole('link', { name: /근거 1 원문 보기/ })).toBeTruthy()
  })

  it('제도 상식은 근거가 없어 답하지 않는다', async () => {
    stubAnswer({ answer: '', answerStatus: 'OUT_OF_SCOPE_GENERAL', citationEntryIds: [] })
    const panel = openPanel(appPaths.chat)

    askFreeQuestion(panel, '업력은 보통 어떻게 계산해?')

    expect(await within(panel).findByText(/지원사업 제도 일반은 확인해 드릴 수 없습니다/)).toBeTruthy()
  })

  it('보내지 않은 항목을 인용한 답변은 그리지 않는다', async () => {
    stubAnswer({ answer: '지어낸 답', answerStatus: 'ANSWERED', citationEntryIds: ['made-up-entry'] })
    const panel = openPanel(appPaths.chat)

    askFreeQuestion(panel, '모집글은 누가 쓸 수 있나요?')

    expect(await within(panel).findByRole('alert')).toBeTruthy()
    expect(within(panel).queryByText('지어낸 답')).toBeNull()
  })

  it('실패를 답으로 숨기지 않고 질문을 남긴다', async () => {
    stubAnswer({ detail: 'unavailable' }, 503)
    const panel = openPanel(appPaths.chat)

    askFreeQuestion(panel, '모집글은 누가 쓸 수 있나요?')

    expect(await within(panel).findByText('답변을 받지 못했습니다')).toBeTruthy()
    expect(within(panel).getByText('모집글은 누가 쓸 수 있나요?')).toBeTruthy()
    expect(within(panel).getByRole('button', { name: '다시 시도' })).toBeTruthy()
  })

  it('중지하면 답변을 기다리지 않고 중지했다고 알린다', async () => {
    vi.stubGlobal('fetch', vi.fn((_url: string, init?: RequestInit) => new Promise<Response>((_resolve, reject) => {
      init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
    })))
    const panel = openPanel(appPaths.chat)

    askFreeQuestion(panel, '모집글은 누가 쓸 수 있나요?')
    fireEvent.click(await within(panel).findByRole('button', { name: '답변 중지' }))

    await waitFor(() => expect(within(panel).getByText('중지했습니다')).toBeTruthy())
  })
})
