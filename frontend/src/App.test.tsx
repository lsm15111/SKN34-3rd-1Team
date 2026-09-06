// @vitest-environment jsdom

import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from './App'
import { appContainer } from './app/appContainer'
import { createAppStore } from './app/store'
import { supportPrograms } from './data/fixtures/supportPrograms'
import { createMemoryAnonymousUsageStorage } from './data/storage/anonymousUsageStorage'

vi.mock('./presentation/shared/core-api-status/CoreApiConnectionStatus', () => ({
  CoreApiConnectionStatus: () => null,
}))

const readinessViewModelMock = vi.hoisted(() => ({
  useSupportProgramSearchReadinessViewModel: vi.fn(),
}))

vi.mock('./presentation/features/chat/viewmodel/useSupportProgramSearchReadinessViewModel', () => (
  readinessViewModelMock
))

beforeEach(() => {
  readinessViewModelMock.useSupportProgramSearchReadinessViewModel.mockReturnValue(
    createReadinessViewModel(),
  )
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  window.localStorage.clear()
})

const sampleCompany = {
  businessNumber: '1248100998',
  companyName: '삼성전자(주)',
  businessStatus: '계속사업자',
}
const sampleAccount = { email: 'manager@company.co.kr', role: 'USER' as const, company: sampleCompany }
const sampleSession = {
  sessionToken: 'session-token',
  expiresAt: '2026-10-06T12:00:00+09:00',
  account: sampleAccount,
}

describe('Partner recruitment posts', () => {
  const linkedProgram = {
    sourceCode: supportPrograms[0].sourceCode,
    sourceProgramId: supportPrograms[0].id,
    title: supportPrograms[0].title,
    organization: supportPrograms[0].organization,
    status: 'OPEN',
    applicationPeriod: supportPrograms[0].applicationPeriod,
    applicationEndDate: supportPrograms[0].applicationEndDate,
    targetDescription: supportPrograms[0].targetDescription,
    sourceUrl: supportPrograms[0].sourceUrl,
  }
  const samplePost = {
    id: 12,
    status: 'OPEN',
    title: 'AI 실증 과제 데이터 구축·라벨링 참여기관 구합니다',
    body: '학습용 민원 문서 정제와 라벨링을 맡아 주실 참여기관을 찾습니다.',
    ourRole: 'LEAD',
    wantedRole: 'PARTICIPANT',
    wantedCompanyCount: 1,
    wantedRegion: '서울·경기·인천',
    requiredCapabilities: ['데이터 구축', '라벨링 운영'],
    closesOn: '2999-09-20',
    closedEarlyAt: null,
    createdAt: '2026-09-06T12:00:00+09:00',
    updatedAt: '2026-09-06T12:00:00+09:00',
    company: { businessNumber: '2208162517', companyName: '데이터브릿지 주식회사', businessStatus: '계속사업자' },
    program: linkedProgram,
    proposalCount: 0,
    viewer: { isOwner: false, myProposalStatus: null },
  }

  it('모집글 목록에서 상세로 이동하고 연결 공고 링크를 보여 준다', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ items: [samplePost], page: 0, size: 20, totalCount: 1 }))
      .mockResolvedValueOnce(jsonResponse(samplePost))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore(), '/partners')

    expect(await screen.findByRole('heading', { name: samplePost.title, level: 2 })).toBeTruthy()
    expect(screen.getByText('모집 중 1건 · 마감 임박순')).toBeTruthy()
    fireEvent.click(screen.getByRole('link', { name: '자세히 보기' }))

    await screen.findByRole('heading', { name: samplePost.title, level: 1 })
    expect(screen.getByText('데이터브릿지 주식회사')).toBeTruthy()
    expect(screen.getByRole('link', { name: '공고 상세 보기' }).getAttribute('href'))
      .toBe(`/support-programs/detail?sourceCode=BIZINFO&sourceProgramId=${supportPrograms[0].id}`)
    expect(screen.queryByRole('button', { name: '조기 마감' })).toBeNull()
    const [, listInit] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(listInit.headers).not.toHaveProperty('Authorization')
  })

  it('비로그인 사용자가 작성 화면에 들어오면 로그인 뒤 작성 화면으로 돌아온다', async () => {
    const detail = { ...supportPrograms[0], matchedReasons: [], recommendationScore: null }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(sampleSession))
      .mockResolvedValueOnce(jsonResponse(detail))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore(), `/partners/new?sourceCode=BIZINFO&sourceProgramId=${supportPrograms[0].id}`)

    await screen.findByRole('heading', { name: '다시 만나서 반가워요' })
    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'manager@company.co.kr' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'password1' } })
    fireEvent.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByRole('heading', { name: '모집글 작성' })).toBeTruthy()
    expect(await screen.findByText(detail.title)).toBeTruthy()
    expect(new URL(String(fetchMock.mock.calls[1]?.[0])).pathname).toBe('/api/v1/support-programs/detail')
  })

  it('공고를 검색해 선택하고 모집글을 등록하면 상세로 이동한다', async () => {
    window.localStorage.setItem('govbiz.sessionToken', 'stored-token')
    const createdPost = { ...samplePost, id: 30, title: '스마트공장 과제 참여기관 찾습니다', viewer: { isOwner: true, myProposalStatus: null } }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ account: sampleAccount }))
      .mockResolvedValueOnce(jsonResponse({ query: '서울 AI', programs: [supportPrograms[0]] }))
      .mockResolvedValueOnce(jsonResponse(createdPost, 201))
      .mockResolvedValueOnce(jsonResponse(createdPost))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore(), '/partners/new')

    await screen.findByRole('heading', { name: '모집글 작성' })
    fireEvent.change(screen.getByRole('searchbox', { name: '공고 검색' }), { target: { value: '서울 AI' } })
    fireEvent.click(screen.getByRole('button', { name: '공고 검색' }))
    fireEvent.click(await screen.findByRole('button', { name: new RegExp(supportPrograms[0].title) }))
    expect(screen.getByText(new RegExp(`공고 마감 ${supportPrograms[0].applicationEndDate} 이전이어야`))).toBeTruthy()

    fireEvent.change(screen.getByLabelText(/^제목/), { target: { value: '연락은 010-1234-5678' } })
    fireEvent.change(screen.getByLabelText(/^모집 소개/), { target: { value: '제조 현장을 보유한 참여기관을 찾습니다.' } })
    fireEvent.change(screen.getByLabelText(/^모집 마감일/), { target: { value: '2999-09-01' } })
    fireEvent.click(screen.getByRole('button', { name: '모집글 등록' }))
    await screen.findByText(/제목에는 이메일·전화번호를 적지 마세요/)
    expect(fetchMock).toHaveBeenCalledTimes(2)

    fireEvent.change(screen.getByLabelText(/^제목/), { target: { value: createdPost.title } })
    fireEvent.click(screen.getByRole('button', { name: '모집글 등록' }))

    await screen.findByRole('heading', { name: createdPost.title, level: 1 })
    const createCall = fetchMock.mock.calls[2]
    expect(new URL(String(createCall?.[0])).pathname).toBe('/api/v1/recruitment-posts')
    expect(JSON.parse(String(createCall?.[1]?.body))).toMatchObject({
      sourceCode: 'BIZINFO',
      sourceProgramId: supportPrograms[0].id,
      post: { title: createdPost.title, ourRole: 'LEAD', wantedRole: 'PARTICIPANT', wantedCompanyCount: 1, closesOn: '2999-09-01' },
    })
    expect(screen.getByRole('button', { name: '조기 마감' })).toBeTruthy()
  })

  it('작성 기업은 상세에서 조기 마감할 수 있다', async () => {
    window.localStorage.setItem('govbiz.sessionToken', 'stored-token')
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true))
    const ownPost = { ...samplePost, viewer: { isOwner: true, myProposalStatus: null } }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(ownPost))
      .mockResolvedValueOnce(jsonResponse({ account: sampleAccount }))
      .mockResolvedValueOnce(jsonResponse({ ...ownPost, status: 'CLOSED', closedEarlyAt: '2026-09-07T10:00:00+09:00' }))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore(), '/partners/12')

    fireEvent.click(await screen.findByRole('button', { name: '조기 마감' }))

    expect((await screen.findByRole('status')).textContent).toBe('모집을 마감했습니다. 목록에는 더 이상 표시되지 않습니다.')
    expect(screen.getByText('모집 종료')).toBeTruthy()
    expect(new URL(String(fetchMock.mock.calls[2]?.[0])).pathname).toBe('/api/v1/recruitment-posts/12/close')
    expect((screen.getByRole('button', { name: '조기 마감' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('공고 상세에서 모집글 목록과 작성 화면으로 연결한다', async () => {
    const detail = { ...supportPrograms[0], matchedReasons: [], recommendationScore: null }
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(jsonResponse(detail))
      .mockResolvedValueOnce(jsonResponse({ items: [], page: 0, size: 20, totalCount: 0 })))

    renderApp(createAppStore(), `/support-programs/detail?sourceCode=${detail.sourceCode}&sourceProgramId=${detail.id}`)

    await screen.findByRole('heading', { name: detail.title })
    expect(screen.getByRole('link', { name: '이 공고로 모집글 작성' }).getAttribute('href'))
      .toBe(`/partners/new?sourceCode=BIZINFO&sourceProgramId=${detail.id}`)
    fireEvent.click(screen.getByRole('link', { name: '이 공고의 모집글 보기' }))

    expect((await screen.findByRole('status')).textContent).toContain(`선택한 공고(${detail.id})의 모집글만`)
    await screen.findByText('아직 모집 중인 글이 없습니다. 첫 모집글을 올려 보세요.')
  })
})

describe('Partner recruitment proposals', () => {
  const openPost = {
    id: 12,
    status: 'OPEN',
    title: 'AI 실증 과제 데이터 구축·라벨링 참여기관 구합니다',
    body: '학습용 민원 문서 정제와 라벨링을 맡아 주실 참여기관을 찾습니다.',
    ourRole: 'LEAD',
    wantedRole: 'PARTICIPANT',
    wantedCompanyCount: 1,
    wantedRegion: '서울·경기·인천',
    requiredCapabilities: ['데이터 구축', '라벨링 운영'],
    closesOn: '2999-09-20',
    closedEarlyAt: null,
    createdAt: '2026-09-06T12:00:00+09:00',
    updatedAt: '2026-09-06T12:00:00+09:00',
    company: { businessNumber: '2208162517', companyName: '데이터브릿지 주식회사', businessStatus: '계속사업자' },
    program: {
      sourceCode: supportPrograms[0].sourceCode,
      sourceProgramId: supportPrograms[0].id,
      title: supportPrograms[0].title,
      organization: supportPrograms[0].organization,
      status: 'OPEN',
      applicationPeriod: supportPrograms[0].applicationPeriod,
      applicationEndDate: supportPrograms[0].applicationEndDate,
      targetDescription: supportPrograms[0].targetDescription,
      sourceUrl: supportPrograms[0].sourceUrl,
    },
    proposalCount: 0,
    viewer: { isOwner: false, myProposalStatus: null },
  }
  const pendingProposal = {
    id: 5,
    postId: 12,
    status: 'PENDING',
    message: '공공 데이터 라벨링 운영 경험이 있는 참여기관입니다.',
    createdAt: '2026-09-06T13:00:00+09:00',
    decidedAt: null,
    company: sampleCompany,
    post: { id: 12, status: 'OPEN', title: openPost.title, closesOn: '2999-09-20', companyName: '데이터브릿지 주식회사' },
    contactEmail: null,
  }

  it('비로그인 사용자에게는 로그인 유도만 보이고 상세로 돌아올 경로를 남긴다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(jsonResponse(openPost)))

    renderApp(createAppStore(), '/partners/12')

    const link = await screen.findByRole('link', { name: '로그인하고 제안하기' })
    expect(link.getAttribute('href')).toBe('/login')
    expect(screen.queryByLabelText('제안 메시지')).toBeNull()
    fireEvent.click(link)
    expect(await screen.findByRole('heading', { name: '다시 만나서 반가워요' })).toBeTruthy()
  })

  it('다른 기업은 연락처 없는 메시지로 제안을 보내고 대기 중인 제안을 철회할 수 있다', async () => {
    window.localStorage.setItem('govbiz.sessionToken', 'stored-token')
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true))
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(openPost))
      .mockResolvedValueOnce(jsonResponse({ account: sampleAccount }))
      .mockResolvedValueOnce(jsonResponse(pendingProposal, 201))
      .mockResolvedValueOnce(jsonResponse({ ...openPost, proposalCount: 1, viewer: { isOwner: false, myProposalStatus: 'PENDING' } }))
      .mockResolvedValueOnce(jsonResponse({ ...pendingProposal, status: 'WITHDRAWN', decidedAt: '2026-09-06T14:00:00+09:00' }))
      .mockResolvedValueOnce(jsonResponse({ ...openPost, viewer: { isOwner: false, myProposalStatus: 'WITHDRAWN' } }))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore(), '/partners/12')

    const textarea = await screen.findByLabelText(/^제안 메시지/)
    fireEvent.change(textarea, { target: { value: '연락은 010-1234-5678' } })
    fireEvent.click(screen.getByRole('button', { name: '참여 제안 보내기' }))
    await screen.findByText(/메시지에는 이메일·전화번호를 적지 마세요/)
    expect(fetchMock).toHaveBeenCalledTimes(2)

    fireEvent.change(textarea, { target: { value: pendingProposal.message } })
    fireEvent.click(screen.getByRole('button', { name: '참여 제안 보내기' }))

    expect((await screen.findByRole('status')).textContent).toContain('제안을 보냈습니다')
    expect(screen.getByText('대기 중')).toBeTruthy()
    expect(screen.getByText(pendingProposal.message)).toBeTruthy()
    const [sendUrl, sendInit] = fetchMock.mock.calls[2] as [string, RequestInit]
    expect(new URL(sendUrl).pathname).toBe('/api/v1/recruitment-posts/12/proposals')
    expect(JSON.parse(String(sendInit.body))).toEqual({ message: pendingProposal.message })
    await screen.findByText('받은 제안')
    expect(screen.getByText('1건')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: '제안 철회' }))

    expect((await screen.findByRole('status')).textContent).toContain('제안을 철회했습니다')
    expect(screen.getByText('철회함')).toBeTruthy()
    expect(screen.queryByRole('button', { name: '제안 철회' })).toBeNull()
    expect(new URL(String(fetchMock.mock.calls[4]?.[0])).pathname).toBe('/api/v1/recruitment-proposals/5/withdraw')
  })

  it('이미 제안한 글에서는 보낸 제안을 찾아 상태를 보여 주고, 중복 제안 409는 안내로 바꾼다', async () => {
    window.localStorage.setItem('govbiz.sessionToken', 'stored-token')
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ ...openPost, proposalCount: 1, viewer: { isOwner: false, myProposalStatus: 'ACCEPTED' } }))
      .mockResolvedValueOnce(jsonResponse({ account: sampleAccount }))
      .mockResolvedValueOnce(jsonResponse({ items: [{ ...pendingProposal, status: 'ACCEPTED', decidedAt: '2026-09-07T10:00:00+09:00', contactEmail: 'owner@databridge.co.kr' }] }))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore(), '/partners/12')

    expect(await screen.findByText('수락됨')).toBeTruthy()
    expect(await screen.findByRole('link', { name: 'owner@databridge.co.kr' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: '제안 철회' })).toBeNull()
    expect(screen.getByRole('link', { name: '보낸 제안 전체 보기' }).getAttribute('href')).toBe('/partners/mine?tab=sent')
    expect(new URL(String(fetchMock.mock.calls[2]?.[0])).pathname).toBe('/api/v1/recruitment-proposals/sent')
  })

  it('같은 글에 다시 제안하면 서버 409를 안내 문구로 보여 준다', async () => {
    window.localStorage.setItem('govbiz.sessionToken', 'stored-token')
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(openPost))
      .mockResolvedValueOnce(jsonResponse({ account: sampleAccount }))
      .mockResolvedValueOnce(problemResponse(409, 'PROPOSAL_ALREADY_EXISTS'))
      .mockResolvedValueOnce(jsonResponse({ ...openPost, viewer: { isOwner: false, myProposalStatus: 'DECLINED' } }))
      .mockResolvedValueOnce(jsonResponse({ items: [{ ...pendingProposal, status: 'DECLINED', decidedAt: '2026-09-07T10:00:00+09:00' }] }))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore(), '/partners/12')

    fireEvent.change(await screen.findByLabelText(/^제안 메시지/), { target: { value: '다시 제안합니다.' } })
    fireEvent.click(screen.getByRole('button', { name: '참여 제안 보내기' }))

    expect((await screen.findByRole('status')).textContent).toContain('이미 제안을 보냈습니다')
    expect(await screen.findByText('거절됨')).toBeTruthy()
  })

  it('작성 기업은 받은 제안 화면에서 수락하고 상대 담당자 이메일을 본다', async () => {
    window.localStorage.setItem('govbiz.sessionToken', 'stored-token')
    const ownPost = { ...openPost, company: sampleCompany, proposalCount: 2, viewer: { isOwner: true, myProposalStatus: null } }
    const partnerCompany = { businessNumber: '1058144880', companyName: '비전솔루션', businessStatus: '계속사업자' }
    const received = [
      { ...pendingProposal, id: 7, company: partnerCompany, post: { ...pendingProposal.post, companyName: '삼성전자(주)' } },
      { ...pendingProposal, id: 8, status: 'EXPIRED', company: { ...partnerCompany, businessNumber: '2208162517', companyName: '늦은 기업' }, message: '늦은 제안' },
    ]
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(ownPost))
      .mockResolvedValueOnce(jsonResponse({ account: sampleAccount }))
      .mockResolvedValueOnce(jsonResponse(ownPost))
      .mockResolvedValueOnce(jsonResponse({ items: received }))
      .mockResolvedValueOnce(jsonResponse({ ...received[0], status: 'ACCEPTED', decidedAt: '2026-09-07T10:00:00+09:00', contactEmail: 'partner@vision.co.kr' }))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore(), '/partners/12')

    expect(await screen.findByText('이 글에 받은 제안 2건')).toBeTruthy()
    expect(screen.queryByLabelText(/^제안 메시지/)).toBeNull()
    fireEvent.click(screen.getByRole('link', { name: '받은 제안 보기' }))

    await screen.findByText(/^받은 제안 2건\./)
    expect(screen.getByText('비전솔루션')).toBeTruthy()
    expect(screen.getByText('7일 무응답 종료')).toBeTruthy()
    expect(screen.getAllByRole('button', { name: '수락' })).toHaveLength(1)
    fireEvent.click(screen.getByRole('button', { name: '수락' }))

    expect((await screen.findByRole('status')).textContent).toContain('제안을 수락했습니다')
    expect(screen.getByText('수락됨')).toBeTruthy()
    expect(screen.getByRole('link', { name: 'partner@vision.co.kr' }).getAttribute('href')).toBe('mailto:partner@vision.co.kr')
    expect(screen.queryByRole('button', { name: '수락' })).toBeNull()
    expect(new URL(String(fetchMock.mock.calls[4]?.[0])).pathname).toBe('/api/v1/recruitment-proposals/7/accept')
  })

  it('다른 기업의 글에서 받은 제안 화면에 들어가면 작성 기업만 볼 수 있다고 안내한다', async () => {
    window.localStorage.setItem('govbiz.sessionToken', 'stored-token')
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(jsonResponse({ account: sampleAccount }))
      .mockResolvedValueOnce(jsonResponse(openPost))
      .mockResolvedValueOnce(problemResponse(403, 'NOT_POST_OWNER')))

    renderApp(createAppStore(), '/partners/12/proposals')

    expect(await screen.findByRole('heading', { name: '작성 기업만 볼 수 있습니다' })).toBeTruthy()
  })

  it('내 모집글의 보낸 제안 탭에서 대기 중인 제안을 철회한다', async () => {
    window.localStorage.setItem('govbiz.sessionToken', 'stored-token')
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true))
    const accepted = { ...pendingProposal, id: 9, postId: 13, status: 'ACCEPTED', decidedAt: '2026-09-07T10:00:00+09:00', contactEmail: 'owner@databridge.co.kr', post: { ...pendingProposal.post, id: 13, title: '두 번째 모집글' } }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ account: sampleAccount }))
      .mockResolvedValueOnce(jsonResponse({ items: [pendingProposal, accepted] }))
      .mockResolvedValueOnce(jsonResponse({ ...pendingProposal, status: 'WITHDRAWN', decidedAt: '2026-09-07T11:00:00+09:00' }))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore(), '/partners/mine?tab=sent')

    await screen.findByRole('heading', { name: '보낸 제안' })
    expect(screen.getByRole('link', { name: '보낸 제안' }).getAttribute('aria-current')).toBe('page')
    expect(screen.getByRole('link', { name: openPost.title }).getAttribute('href')).toBe('/partners/12')
    expect(screen.getByRole('link', { name: 'owner@databridge.co.kr' })).toBeTruthy()
    expect(screen.getAllByRole('button', { name: '제안 철회' })).toHaveLength(1)
    fireEvent.click(screen.getByRole('button', { name: '제안 철회' }))

    expect((await screen.findByRole('status')).textContent).toContain('제안을 철회했습니다')
    expect(screen.getByText('철회함')).toBeTruthy()
    expect(screen.queryByRole('button', { name: '제안 철회' })).toBeNull()
    expect(new URL(String(fetchMock.mock.calls[1]?.[0])).pathname).toBe('/api/v1/recruitment-proposals/sent')
  })
})

describe('Account sign-up and login', () => {
  it('회원가입은 기업 확인 뒤 가입 요청을 보내고 홈 헤더에 회사명을 표시한다', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ businesses: [sampleCompany] }))
      .mockResolvedValueOnce(jsonResponse(sampleSession, 201))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore(), '/signup')

    expect(screen.getByRole('heading', { name: '간편 회원가입' })).toBeTruthy()
    fireEvent.change(screen.getByLabelText(/사업자등록번호/), { target: { value: '124-81-00998' } })
    fireEvent.click(screen.getByRole('button', { name: '기업 정보 확인' }))

    await screen.findByText('삼성전자(주)')
    expect(new URL(String(fetchMock.mock.calls[0]?.[0])).searchParams.get('businessNumber')).toBe('1248100998')

    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: ' Manager@Company.co.kr ' } })
    fireEvent.change(passwordInput('signup-password'), { target: { value: 'password1' } })
    fireEvent.change(screen.getByLabelText('비밀번호 확인'), { target: { value: 'password1' } })
    fireEvent.click(screen.getByLabelText(/서비스 이용약관/))
    fireEvent.click(screen.getByRole('button', { name: '가입하고 계속하기' }))

    await screen.findByRole('heading', { name: 'GovBiz에게 물어보세요' })
    expect(JSON.parse(String(fetchMock.mock.calls[1]?.[1]?.body))).toEqual({
      email: 'manager@company.co.kr',
      password: 'password1',
      businessNumber: '1248100998',
    })
    expect(screen.getByText('삼성전자(주)')).toBeTruthy()
    expect(screen.getByRole('button', { name: '로그아웃' })).toBeTruthy()
    expect(screen.queryByRole('link', { name: '로그인' })).toBeNull()
    expect(window.localStorage.getItem('govbiz.sessionToken')).toBe('session-token')
  })

  it('기업 확인 전 가입 제출과 미등록 사업자를 각각 안내한다', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse({ businesses: [] }))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore(), '/signup')

    fireEvent.change(screen.getByLabelText(/사업자등록번호/), { target: { value: '1234567890' } })
    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'new@company.co.kr' } })
    fireEvent.change(passwordInput('signup-password'), { target: { value: 'password1' } })
    fireEvent.change(screen.getByLabelText('비밀번호 확인'), { target: { value: 'password1' } })
    fireEvent.click(screen.getByLabelText(/서비스 이용약관/))
    fireEvent.click(screen.getByRole('button', { name: '가입하고 계속하기' }))

    expect((await screen.findByRole('alert')).textContent).toBe('가입 전에 사업자등록번호로 기업 정보를 확인해 주세요.')
    expect(fetchMock).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: '기업 정보 확인' }))
    expect((await screen.findByRole('status')).textContent).toBe('국세청에 등록된 사업자를 찾지 못했습니다. 번호를 다시 확인해 주세요.')
    expect(fetchMock).toHaveBeenCalledOnce()
  })

  it('가입 폼은 비밀번호 규칙과 확인 불일치를 API 호출 없이 안내한다', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore(), '/signup')

    fireEvent.change(passwordInput('signup-password'), { target: { value: 'onlyletters' } })
    fireEvent.change(screen.getByLabelText('비밀번호 확인'), { target: { value: 'different1' } })
    fireEvent.click(screen.getByRole('button', { name: '가입하고 계속하기' }))

    await screen.findByText('비밀번호는 8~72자이며 영문과 숫자를 모두 포함해야 합니다.')
    expect(screen.getByText('서비스 이용약관과 개인정보 처리방침에 동의해 주세요.')).toBeTruthy()

    // 필드 규칙을 통과한 뒤에야 비밀번호 확인 일치 검사가 실행됩니다.
    fireEvent.change(passwordInput('signup-password'), { target: { value: 'password1' } })
    fireEvent.click(screen.getByLabelText(/서비스 이용약관/))
    fireEvent.click(screen.getByRole('button', { name: '가입하고 계속하기' }))

    await screen.findByText('비밀번호가 일치하지 않습니다.')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('로그인 실패는 이메일·비밀번호를 구분하지 않는 안내를 표시한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ status: 401, code: 'INVALID_CREDENTIALS' }),
      { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
    )))

    renderApp(createAppStore(), '/login')

    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'manager@company.co.kr' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'wrong-pass1' } })
    fireEvent.click(screen.getByRole('button', { name: '로그인' }))

    expect((await screen.findByRole('alert')).textContent).toBe('이메일 또는 비밀번호를 확인해 주세요.')
    expect(screen.getByRole('heading', { name: '다시 만나서 반가워요' })).toBeTruthy()
    expect(window.localStorage.getItem('govbiz.sessionToken')).toBeNull()
  })

  it('저장된 토큰으로 세션을 복원하고 로그아웃하면 토큰을 지운다', async () => {
    window.localStorage.setItem('govbiz.sessionToken', 'stored-token')
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ account: sampleAccount }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore())

    await screen.findByRole('button', { name: '로그아웃' })
    expect(screen.getByText('삼성전자(주)')).toBeTruthy()
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      headers: { Authorization: 'Bearer stored-token' },
    })

    fireEvent.click(screen.getByRole('button', { name: '로그아웃' }))

    await screen.findByRole('link', { name: '로그인' })
    expect(new URL(String(fetchMock.mock.calls[1]?.[0])).pathname).toBe('/api/v1/auth/logout')
    expect(window.localStorage.getItem('govbiz.sessionToken')).toBeNull()
  })

  it('비로그인 무료 검색을 모두 쓰면 검색 대신 가입 안내 모달을 띄운다', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const usageStorage = createMemoryAnonymousUsageStorage(3)

    renderApp(createAppStore({ anonymousUsageStorage: usageStorage }))

    await screen.findByText('무료 검색 0회 남음')
    const chatInput = screen.getByRole('textbox', { name: '지원사업 검색어' })
    fireEvent.change(chatInput, { target: { value: '서울 AI' } })
    fireEvent.submit(chatInput.closest('form')!)

    const dialog = await screen.findByRole('dialog', { name: '무료 검색 3회를 모두 사용했어요' })
    expect(fetchMock).not.toHaveBeenCalled()
    expect((chatInput as HTMLTextAreaElement).value).toBe('서울 AI')

    fireEvent.keyDown(document, { key: 'Escape' })
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(document.activeElement).toBe(chatInput)

    fireEvent.submit(chatInput.closest('form')!)
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('link', { name: '간편 회원가입' }))
    expect(screen.getByRole('heading', { name: '간편 회원가입' })).toBeTruthy()
    expect(usageStorage.read()).toBe(3)
    void dialog
  })

  it('비로그인 검색은 횟수를 저장하고 로그인하면 제한을 해제한다', async () => {
    const usageStorage = createMemoryAnonymousUsageStorage(2)
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ query: '서울 AI', programs: [supportPrograms[0]] }))
      .mockResolvedValueOnce(jsonResponse(sampleSession))
      .mockResolvedValueOnce(jsonResponse({ query: '수출', programs: [supportPrograms[3]] }))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore({ anonymousUsageStorage: usageStorage }))

    await screen.findByText('무료 검색 1회 남음')
    const chatInput = screen.getByRole('textbox', { name: '지원사업 검색어' })
    fireEvent.change(chatInput, { target: { value: '서울 AI' } })
    fireEvent.submit(chatInput.closest('form')!)

    await screen.findByText('무료 검색 0회 남음')
    expect(usageStorage.read()).toBe(3)

    fireEvent.click(screen.getByRole('link', { name: '로그인' }))
    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'manager@company.co.kr' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'password1' } })
    fireEvent.click(screen.getByRole('button', { name: '로그인' }))

    await screen.findByRole('button', { name: '로그아웃' })
    expect(screen.queryByText(/무료 검색/)).toBeNull()
    expect(usageStorage.read()).toBe(0)

    const signedInInput = screen.getByRole('textbox', { name: '지원사업 검색어' })
    fireEvent.change(signedInInput, { target: { value: '수출' } })
    fireEvent.submit(signedInInput.closest('form')!)

    await screen.findByText('현재 접수 중인 관련 공고 1건을 찾았습니다. 공고를 선택하면 자세한 조건과 원문을 확인할 수 있어요.')
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })

  it('관리자는 운영 콘솔에서 회원을 검색하고 세션을 종료할 수 있다', async () => {
    window.localStorage.setItem('govbiz.sessionToken', 'admin-token')
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true))
    const adminAccount = { ...sampleAccount, email: 'admin@govbiz.test', role: 'ADMIN' as const }
    const listPage = {
      items: [{ id: 12, ...sampleAccount, createdAt: '2026-09-06T12:00:00+09:00' }],
      page: 0,
      size: 20,
      totalCount: 1,
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ account: adminAccount }))
      .mockResolvedValueOnce(jsonResponse(listPage))
      .mockResolvedValueOnce(jsonResponse({ ...listPage, items: [], totalCount: 0 }))
      .mockResolvedValueOnce(jsonResponse(listPage))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore())

    fireEvent.click(await screen.findByRole('link', { name: /운영 콘솔/ }))

    expect(await screen.findByRole('heading', { name: '회원·기업' })).toBeTruthy()
    await screen.findByText('manager@company.co.kr')
    expect(screen.getByText('삼성전자(주)')).toBeTruthy()
    expect(screen.getByText('124-81-00998')).toBeTruthy()
    expect(fetchMock.mock.calls[1]?.[1]).toMatchObject({ headers: { Authorization: 'Bearer admin-token' } })

    fireEvent.change(screen.getByRole('searchbox', { name: '이메일 검색' }), { target: { value: 'nobody' } })
    fireEvent.submit(screen.getByRole('search'))
    await screen.findByText('조건에 맞는 회원이 없습니다.')
    expect(new URL(String(fetchMock.mock.calls[2]?.[0])).searchParams.get('email')).toBe('nobody')

    fireEvent.change(screen.getByRole('searchbox', { name: '이메일 검색' }), { target: { value: '' } })
    fireEvent.submit(screen.getByRole('search'))
    fireEvent.click(await screen.findByRole('button', { name: '세션 종료' }))

    expect((await screen.findByRole('status')).textContent).toBe('manager@company.co.kr 계정의 모든 세션을 종료했습니다.')
    const revokeCall = fetchMock.mock.calls.find((call) => String(call[0]).endsWith('/sessions/revoke'))
    expect(new URL(String(revokeCall?.[0])).pathname).toBe('/api/v1/admin/accounts/12/sessions/revoke')
  })

  it('일반 회원은 운영 콘솔 대신 홈으로 이동한다', async () => {
    window.localStorage.setItem('govbiz.sessionToken', 'user-token')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ account: sampleAccount })))

    renderApp(createAppStore(), '/admin/accounts')

    await screen.findByRole('heading', { name: 'GovBiz에게 물어보세요' })
    expect(screen.queryByRole('link', { name: /운영 콘솔/ })).toBeNull()
  })

  it('로그인한 상태에서는 로그인·회원가입 화면 대신 홈으로 보낸다', async () => {
    window.localStorage.setItem('govbiz.sessionToken', 'stored-token')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ account: sampleAccount })))

    renderApp(createAppStore(), '/login')

    await screen.findByRole('heading', { name: 'GovBiz에게 물어보세요' })
  })
})

describe('App navigation', () => {
  it('두 예제의 상태 수명과 Redux의 production DI·HTTP 흐름을 비교한다', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      const request = JSON.parse(String(init?.body)) as {
        item: { category: string | null; name: string; note: string | null }
      }

      return new Response(JSON.stringify({
        item: request.item,
        phase: 'READY_FOR_PROCESSING',
        processing: { status: 'NOT_STARTED' },
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    })
    vi.stubGlobal('fetch', fetchMock)
    const appStore = createAppStore()

    expect(Object.keys(appStore.getState())).toEqual(['chat', 'auth', 'usage', 'sampleItem'])

    renderApp(appStore)

    expect(screen.getByRole('heading', { name: 'GovBiz에게 물어보세요' })).toBeTruthy()
    const chatInput = screen.getByPlaceholderText('예: 서울에서 AI 창업지원 사업을 찾아줘')
    fireEvent.change(chatInput, { target: { value: '서울 AI 지원사업' } })

    fireEvent.click(screen.getByRole('link', { name: /상태관리 비교 예제/ }))

    expect(screen.getByRole('heading', { name: '재사용 가능한 수직 슬라이스' })).toBeTruthy()

    fireEvent.change(screen.getByRole('textbox', { name: '이름' }), {
      target: { value: 'Hook에서만 유지되는 입력' },
    })

    fireEvent.click(screen.getByRole('link', { name: 'Redux Toolkit 버전' }))
    expect(screen.getByRole('heading', { name: 'Redux 기반 수직 슬라이스' })).toBeTruthy()
    fireEvent.change(screen.getByRole('textbox', { name: '이름' }), {
      target: { value: 'Redux에 유지되는 입력' },
    })

    fireEvent.click(screen.getByRole('link', { name: 'React Hook 버전' }))
    expect(screen.getByRole('heading', { name: '재사용 가능한 수직 슬라이스' })).toBeTruthy()
    expect((screen.getByRole('textbox', { name: '이름' }) as HTMLInputElement).value).toBe('')

    fireEvent.click(screen.getByRole('link', { name: 'Redux Toolkit 버전' }))
    expect((screen.getByRole('textbox', { name: '이름' }) as HTMLInputElement).value).toBe(
      'Redux에 유지되는 입력',
    )

    await waitFor(() => {
      expect((screen.getByRole('button', { name: '준비 상태 확인' }) as HTMLButtonElement).disabled)
        .toBe(false)
    })
    fireEvent.click(screen.getByRole('button', { name: '준비 상태 확인' }))

    await screen.findByText('✓ Redux Store에 요청 성공 저장')
    expect(fetchMock).toHaveBeenCalledOnce()
    expect(String(fetchMock.mock.calls[0]?.[0])).toMatch(/\/api\/v1\/sample-items\/prepare$/)
    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toEqual({
      item: {
        category: null,
        name: 'Redux에 유지되는 입력',
        note: null,
      },
    })

    fireEvent.click(screen.getByRole('link', { name: 'React Hook 버전' }))
    fireEvent.click(screen.getByRole('link', { name: 'Redux Toolkit 버전' }))
    expect(screen.getByText('✓ Redux Store에 요청 성공 저장')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'Redux 상태 초기화' }))
    expect((screen.getByRole('textbox', { name: '이름' }) as HTMLInputElement).value).toBe('')
    expect(screen.queryByText('✓ Redux Store에 요청 성공 저장')).toBeNull()
    expect((screen.getByRole('button', { name: '준비 상태 확인' }) as HTMLButtonElement).disabled)
      .toBe(true)

    fireEvent.click(screen.getByRole('link', { name: /지원사업 채팅으로 돌아가기/ }))

    expect(screen.getByRole('heading', { name: 'GovBiz에게 물어보세요' })).toBeTruthy()
    expect(
      (screen.getByPlaceholderText('예: 서울에서 AI 창업지원 사업을 찾아줘') as HTMLTextAreaElement)
        .value,
    ).toBe('서울 AI 지원사업')
  })

  it.each([
    ['/examples/sample-item/hook', '재사용 가능한 수직 슬라이스'],
    ['/examples/sample-item/redux', 'Redux 기반 수직 슬라이스'],
  ])('%s URL로 직접 진입한다', (path, heading) => {
    renderApp(createAppStore(), path)

    expect(screen.getByRole('heading', { name: heading })).toBeTruthy()
  })

  it('검색 결과의 상세 조건 보기는 URL 기반 API 조회 화면으로 연결한다', async () => {
    const detail = { ...supportPrograms[0], matchedReasons: [], recommendationScore: null }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        query: '서울 AI',
        programs: [supportPrograms[0]],
      }))
      .mockResolvedValueOnce(jsonResponse(detail))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore())

    const chatInput = screen.getByPlaceholderText('예: 서울에서 AI 창업지원 사업을 찾아줘')
    fireEvent.change(chatInput, { target: { value: '서울 AI' } })
    fireEvent.submit(chatInput.closest('form')!)

    const detailLink = await screen.findByRole('link', { name: '상세 조건 보기' })
    fireEvent.click(detailLink)

    await screen.findByRole('heading', { name: supportPrograms[0].title })
    expect(screen.getByText('서울 소재 창업 7년 이내 중소기업')).toBeTruthy()
    expect(screen.getByText('접수 중')).toBeTruthy()
    expect(screen.queryByText('이 공고를 추천한 이유')).toBeNull()

    const detailRequestUrl = new URL(String(fetchMock.mock.calls[1]?.[0]))
    expect(detailRequestUrl.pathname).toBe('/api/v1/support-programs/detail')
    expect(detailRequestUrl.searchParams.get('sourceCode')).toBe(supportPrograms[0].sourceCode)
    expect(detailRequestUrl.searchParams.get('sourceProgramId')).toBe(supportPrograms[0].id)

    const sourceLink = screen.getByRole('link', { name: /GovBiz 샘플 데이터 원문 보기/ })
    expect(sourceLink.getAttribute('href')).toBe(supportPrograms[0].sourceUrl)
    expect(sourceLink.getAttribute('target')).toBe('_blank')
    expect(sourceLink.getAttribute('rel')).toBe('noreferrer')

    fireEvent.click(screen.getByRole('link', { name: '← 검색 결과로 돌아가기' }))
    expect(screen.getByRole('heading', { name: 'GovBiz에게 물어보세요' })).toBeTruthy()
  })

  it('상세 공고에서는 사용자가 질문을 제출한 뒤에만 원문 근거 답변과 링크를 표시한다', async () => {
    const detail = { ...supportPrograms[0], matchedReasons: [], recommendationScore: null }
    const evidenceAnswer = {
      answer: '서울 소재 창업 7년 이내 중소기업이 신청 대상입니다.',
      answerStatus: 'ANSWERED',
      citations: [{
        excerpt: `${'공고 안내입니다. '.repeat(70)}\n지원 대상은 서울 소재 창업 7년 이내 중소기업입니다.`,
        sourceUrl: detail.sourceUrl,
        chunkOrder: 0,
      }],
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(detail))
      .mockResolvedValueOnce(jsonResponse(evidenceAnswer))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(
      createAppStore(),
      `/support-programs/detail?sourceCode=${detail.sourceCode}&sourceProgramId=${detail.id}`,
    )

    await screen.findByRole('heading', { name: detail.title })
    expect(fetchMock).toHaveBeenCalledOnce()

    const question = screen.getByRole('textbox', { name: '공고 원문에 질문하기' })
    expect(screen.getByRole('button', { name: '질문하고 근거 받기' })).toBeTruthy()
    fireEvent.change(question, { target: { value: '신청 대상은 누구인가요?' } })
    fireEvent.submit(question.closest('form')!)

    await screen.findByText(evidenceAnswer.answer)
    const requestUrl = new URL(String(fetchMock.mock.calls[1]?.[0]))
    expect(requestUrl.pathname).toBe('/api/v1/support-programs/detail/answers')
    expect(fetchMock.mock.calls[1]?.[1]).toMatchObject({
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
    })
    expect(JSON.parse(String(fetchMock.mock.calls[1]?.[1]?.body))).toEqual({
      sourceCode: detail.sourceCode,
      sourceProgramId: detail.id,
      question: '신청 대상은 누구인가요?',
    })

    const citationLink = screen.getByRole('link', { name: '근거 1 원문 보기 ↗' })
    expect(citationLink.getAttribute('href')).toBe(detail.sourceUrl)
    expect(citationLink.getAttribute('target')).toBe('_blank')
    expect(citationLink.getAttribute('rel')).toBe('noreferrer')
    expect(citationLink.closest('li')?.querySelector('blockquote')?.textContent)
      .toBe(evidenceAnswer.citations[0].excerpt)
  })

  it.each([
    [{
      answer: '원문 근거가 부족합니다.',
      answerStatus: 'INSUFFICIENT_EVIDENCE',
      citations: [],
    }, '공고 원문에서 이 질문에 답할 만큼 충분한 근거를 찾지 못했습니다. 원문 공고를 확인해 주세요.'],
    [new Response('', { status: 422 }), '이 제공처 공고는 아직 원문 근거 답변을 지원하지 않습니다. 원문 공고에서 확인해 주세요.'],
    [new Response('', { status: 503 }), '원문 근거 답변을 지금 준비하지 못했습니다. 잠시 후 다시 시도해 주세요.'],
  ])('원문 답변의 응답 상태에 안전한 안내를 표시한다', async (answerResponse, expectedMessage) => {
    const detail = { ...supportPrograms[0], matchedReasons: [], recommendationScore: null }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(detail))
      .mockResolvedValueOnce(answerResponse instanceof Response ? answerResponse : jsonResponse(answerResponse))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(
      createAppStore(),
      `/support-programs/detail?sourceCode=${detail.sourceCode}&sourceProgramId=${detail.id}`,
    )

    const question = await screen.findByRole('textbox', { name: '공고 원문에 질문하기' })
    fireEvent.change(question, { target: { value: '신청 대상은 누구인가요?' } })
    fireEvent.submit(question.closest('form')!)

    expect(await screen.findByText(expectedMessage)).toBeTruthy()
  })

  it('제공처가 다른 동일 원본 ID 공고를 각각 표시하고 올바른 상세 식별자로 조회한다', async () => {
    const sharedProgramId = 'SHARED-PROGRAM-ID'
    const bizInfoProgram = {
      ...supportPrograms[0],
      id: sharedProgramId,
      title: '기업마당 동일 원본 ID 공고',
    }
    const otherProgram = {
      ...supportPrograms[1],
      sourceCode: 'OTHER',
      id: sharedProgramId,
      title: '기타 제공처 동일 원본 ID 공고',
      sourceName: '테스트 제공처',
      sourceUrl: 'https://support-programs.other.test/programs/shared',
    }
    // 아직 연동하지 않은 제공처는 HTTP allowlist에 추가하지 않고 Domain 경계에서 대역을 제공합니다.
    const repository = appContainer.resolve('supportProgramRepository')
    vi.spyOn(repository, 'search').mockResolvedValue([bizInfoProgram, otherProgram])
    const getDetail = vi.spyOn(repository, 'getDetail')
      .mockResolvedValueOnce({
        ...bizInfoProgram,
        matchedReasons: [],
        recommendationScore: null,
      })
      .mockResolvedValueOnce({
        ...otherProgram,
        matchedReasons: [],
        recommendationScore: null,
      })

    renderApp(createAppStore())

    const chatInput = screen.getByPlaceholderText('예: 서울에서 AI 창업지원 사업을 찾아줘')
    fireEvent.change(chatInput, { target: { value: '동일 ID' } })
    fireEvent.submit(chatInput.closest('form')!)

    await screen.findByRole('heading', { name: bizInfoProgram.title, level: 2 })
    await screen.findByRole('heading', { name: otherProgram.title, level: 2 })

    const bizInfoCard = getProgramCard(bizInfoProgram.title)
    const otherCard = getProgramCard(otherProgram.title)
    expect(within(bizInfoCard).getByRole('link', { name: '원문 보기 ↗' }).getAttribute('href'))
      .toBe(bizInfoProgram.sourceUrl)
    expect(within(otherCard).getByRole('link', { name: '원문 보기 ↗' }).getAttribute('href'))
      .toBe(otherProgram.sourceUrl)

    fireEvent.click(within(bizInfoCard).getByRole('link', { name: '상세 조건 보기' }))
    await screen.findByRole('heading', { name: bizInfoProgram.title, level: 1 })
    expect(getDetail).toHaveBeenNthCalledWith(1, {
      sourceCode: bizInfoProgram.sourceCode,
      sourceProgramId: sharedProgramId,
    }, expect.any(AbortSignal))

    fireEvent.click(screen.getByRole('link', { name: '← 검색 결과로 돌아가기' }))
    await screen.findByRole('heading', { name: otherProgram.title, level: 2 })

    fireEvent.click(within(getProgramCard(otherProgram.title)).getByRole('link', { name: '상세 조건 보기' }))
    await screen.findByRole('heading', { name: otherProgram.title, level: 1 })
    expect(getDetail).toHaveBeenNthCalledWith(2, {
      sourceCode: otherProgram.sourceCode,
      sourceProgramId: sharedProgramId,
    }, expect.any(AbortSignal))
  })

  it('한글 조합 중 Enter는 검색을 전송하지 않고 조합이 끝난 뒤 전송한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({
      query: '서울 AI',
      programs: [supportPrograms[0]],
    }))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore())

    const chatInput = screen.getByPlaceholderText('예: 서울에서 AI 창업지원 사업을 찾아줘')
    fireEvent.change(chatInput, { target: { value: '서울 AI' } })
    fireEvent.compositionStart(chatInput)
    fireEvent.keyDown(chatInput, { isComposing: true, key: 'Enter' })

    expect(fetchMock).not.toHaveBeenCalled()
    expect((chatInput as HTMLTextAreaElement).value).toBe('서울 AI')

    fireEvent.compositionEnd(chatInput)
    fireEvent.keyDown(chatInput, { key: 'Enter' })

    await waitFor(() => expect(fetchMock).toHaveBeenCalledOnce())
  })

  it('Safari가 한글 조합 완료 직후 보내는 Enter도 검색을 전송하지 않는다', () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore())

    const chatInput = screen.getByPlaceholderText('예: 서울에서 AI 창업지원 사업을 찾아줘')
    fireEvent.change(chatInput, { target: { value: '서울 AI' } })
    fireEvent.compositionStart(chatInput)
    fireEvent.compositionEnd(chatInput)
    fireEvent.keyDown(chatInput, { key: 'Enter', keyCode: 229 })

    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('500자를 넘는 검색어는 API를 호출하지 않고 이유를 안내한다', () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore())

    const overlongQuery = '가'.repeat(501)
    const chatInput = screen.getByPlaceholderText('예: 서울에서 AI 창업지원 사업을 찾아줘')
    fireEvent.change(chatInput, { target: { value: overlongQuery } })
    fireEvent.submit(chatInput.closest('form')!)

    expect(fetchMock).not.toHaveBeenCalled()
    expect((chatInput as HTMLTextAreaElement).value).toBe(overlongQuery)
    expect(screen.getByRole('alert').textContent).toBe(
      '검색어는 500자 이하로 입력해 주세요. 현재 501자입니다.',
    )
  })

  it('검색 실패 시 검색어를 복구하고 다시 검색할 수 있다', async () => {
    const fetchMock = vi.fn()
      .mockRejectedValueOnce(new Error('temporary network failure'))
      .mockResolvedValueOnce(jsonResponse({
        query: '서울 AI',
        programs: [supportPrograms[0]],
      }))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore())

    const chatInput = screen.getByRole('textbox', { name: '지원사업 검색어' })
    fireEvent.change(chatInput, { target: { value: '서울 AI' } })
    fireEvent.submit(chatInput.closest('form')!)

    await screen.findByRole('alert')
    expect((chatInput as HTMLTextAreaElement).value).toBe('서울 AI')

    fireEvent.click(screen.getByRole('button', { name: '다시 검색' }))

    await screen.findByText('현재 접수 중인 관련 공고 1건을 찾았습니다. 공고를 선택하면 자세한 조건과 원문을 확인할 수 있어요.')
    expect(screen.getByRole('status').textContent).toBe('지원사업 검색 결과 1건을 표시했습니다.')
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('초기 공고 데이터 준비 중에는 검색을 막고 준비 완료를 안내한다', () => {
    readinessViewModelMock.useSupportProgramSearchReadinessViewModel.mockReturnValue(
      createReadinessViewModel({
        canSearch: false,
        data: {
          searchState: 'PREPARING',
          programCount: 0,
          indexReady: false,
          lastSuccessfulSyncAt: null,
          lastFailedSyncAt: null,
        },
      }),
    )
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore())

    expect(screen.getByText('초기 공고 데이터를 준비하고 있습니다.')).toBeTruthy()
    expect(screen.getByText('준비가 완료되면 자동으로 검색할 수 있습니다.')).toBeTruthy()
    const searchInput = screen.getByRole('textbox', { name: '지원사업 검색어' })
    expect((searchInput as HTMLTextAreaElement).disabled).toBe(true)
    expect((screen.getByRole('button', { name: '검색 전송' }) as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getAllByRole('button', { name: '서울 AI 창업지원 사업 찾아줘' })[0] as HTMLButtonElement).disabled)
      .toBe(true)
    fireEvent.submit(searchInput.closest('form')!)
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('공고 상태를 처음 확인하는 동안에는 준비 중과 구분된 안내를 표시한다', () => {
    readinessViewModelMock.useSupportProgramSearchReadinessViewModel.mockReturnValue(
      createReadinessViewModel({
        canSearch: false,
        data: undefined,
        isInitialLoading: true,
      }),
    )

    renderApp(createAppStore())

    expect(screen.getByText('공고 데이터 상태를 확인하고 있습니다.')).toBeTruthy()
    expect((screen.getByRole('textbox', { name: '지원사업 검색어' }) as HTMLTextAreaElement).disabled)
      .toBe(true)
  })

  it('최신 동기화가 실패해도 이전 공고 검색은 유지하고 동기화 시각을 보여 준다', async () => {
    readinessViewModelMock.useSupportProgramSearchReadinessViewModel.mockReturnValue(
      createReadinessViewModel({
        data: {
          searchState: 'SEARCHABLE_WITH_SYNC_FAILURE',
          programCount: 12,
          indexReady: true,
          lastSuccessfulSyncAt: '2026-09-05T09:00:00+09:00',
          lastFailedSyncAt: '2026-09-05T10:00:00+09:00',
        },
      }),
    )
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({
      query: '서울 AI',
      programs: [supportPrograms[0]],
    }))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore())

    expect(screen.getByText('이전 공고 데이터로 검색할 수 있습니다.')).toBeTruthy()
    expect(screen.getByText('최신 공고 동기화에 실패했지만, 이전에 저장된 공고는 계속 검색할 수 있습니다.'))
      .toBeTruthy()
    expect(screen.getByText('마지막 성공 동기화')).toBeTruthy()
    expect(screen.getByText('마지막 실패 동기화')).toBeTruthy()
    expect(screen.getAllByText('12건').length).toBeGreaterThan(0)

    const searchInput = screen.getByRole('textbox', { name: '지원사업 검색어' })
    expect((searchInput as HTMLTextAreaElement).disabled).toBe(false)
    fireEvent.change(searchInput, { target: { value: '서울 AI' } })
    fireEvent.submit(searchInput.closest('form')!)
    await screen.findByRole('heading', { name: supportPrograms[0].title, level: 2 })
    expect(fetchMock).toHaveBeenCalledOnce()
  })

  it('검색 불가 상태는 검색을 막고 상태 확인을 다시 요청할 수 있다', () => {
    const refetch = vi.fn()
    readinessViewModelMock.useSupportProgramSearchReadinessViewModel.mockReturnValue(
      createReadinessViewModel({
        canSearch: false,
        data: {
          searchState: 'UNAVAILABLE',
          programCount: 0,
          indexReady: false,
          lastSuccessfulSyncAt: null,
          lastFailedSyncAt: '2026-09-05T10:00:00+09:00',
        },
        refetch,
      }),
    )

    renderApp(createAppStore())

    expect(screen.getByRole('alert').textContent).toContain('현재 공고 데이터를 검색할 수 없습니다.')
    expect((screen.getByRole('textbox', { name: '지원사업 검색어' }) as HTMLTextAreaElement).disabled)
      .toBe(true)
    fireEvent.click(screen.getByRole('button', { name: '상태 다시 확인' }))
    expect(refetch).toHaveBeenCalledOnce()
  })

  it('검색 실패 뒤 공고 상태가 검색 불가로 바뀌면 다시 검색 버튼을 숨긴다', async () => {
    let currentReadiness = createReadinessViewModel()
    readinessViewModelMock.useSupportProgramSearchReadinessViewModel.mockImplementation(
      () => currentReadiness,
    )
    const fetchMock = vi.fn(() => {
      currentReadiness = createReadinessViewModel({
        canSearch: false,
        data: {
          searchState: 'UNAVAILABLE',
          programCount: 0,
          indexReady: false,
          lastSuccessfulSyncAt: null,
          lastFailedSyncAt: '2026-09-05T10:00:00+09:00',
        },
      })
      return Promise.reject(new Error('temporary search failure'))
    })
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore())

    const searchInput = screen.getByRole('textbox', { name: '지원사업 검색어' })
    fireEvent.change(searchInput, { target: { value: '서울 AI' } })
    fireEvent.submit(searchInput.closest('form')!)

    await screen.findByText('지원사업을 검색하지 못했습니다. 잠시 후 다시 시도해 주세요.')
    expect(screen.queryByRole('button', { name: '다시 검색' })).toBeNull()
    expect((screen.getByRole('textbox', { name: '지원사업 검색어' }) as HTMLTextAreaElement).disabled)
      .toBe(true)
    expect(fetchMock).toHaveBeenCalledOnce()
  })

  it('검색 가능한 상태에서 빈 검색 결과는 공고 없음으로 안내한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({
      query: '존재하지 않는 조건',
      programs: [],
    }))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore())

    const searchInput = screen.getByRole('textbox', { name: '지원사업 검색어' })
    fireEvent.change(searchInput, { target: { value: '존재하지 않는 조건' } })
    fireEvent.submit(searchInput.closest('form')!)

    await screen.findByText('현재 일치하는 공고를 찾지 못했습니다. 지역이나 분야를 바꿔 다시 검색해 보세요.')
    expect(screen.getByText('공고 검색이 가능합니다.')).toBeTruthy()
  })

  it('진행 중인 검색은 취소할 수 있고 검색어를 유지한다', async () => {
    let requestSignal: AbortSignal | undefined
    const fetchMock = vi.fn((_input: RequestInfo | URL, init?: RequestInit) => new Promise<Response>(
      (_resolve, reject) => {
        requestSignal = init?.signal ?? undefined
        requestSignal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')), {
          once: true,
        })
      },
    ))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore())

    const chatInput = screen.getByRole('textbox', { name: '지원사업 검색어' })
    fireEvent.change(chatInput, { target: { value: '수출' } })
    fireEvent.submit(chatInput.closest('form')!)
    await waitFor(() => expect(fetchMock).toHaveBeenCalledOnce())

    fireEvent.click(screen.getByRole('button', { name: '취소' }))

    expect(requestSignal?.aborted).toBe(true)
    expect((chatInput as HTMLTextAreaElement).value).toBe('수출')
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('모바일 메뉴는 포커스를 사이드바에 가두고 닫을 때 메뉴 버튼으로 돌려준다', () => {
    installMobileMediaQuery()
    renderApp(createAppStore())

    const sidebar = screen.getByLabelText('지원사업 검색 메뉴')
    expect(sidebar.className).toContain('max-chat:invisible')
    expect(sidebar.className).toContain('max-chat:pointer-events-none')
    expect(screen.getByText('추천 질문')).toBeTruthy()
    expect(screen.getByRole('status')).toBeTruthy()
    expect(screen.getByRole('button', { name: '검색 전송' })).toBeTruthy()

    const menuButton = screen.getByRole('button', { name: '메뉴 열기' })
    expect(menuButton.getAttribute('aria-expanded')).toBe('false')
    menuButton.focus()
    fireEvent.click(menuButton)
    expect(menuButton.getAttribute('aria-expanded')).toBe('true')
    expect(sidebar.getAttribute('role')).toBe('dialog')
    expect(sidebar.getAttribute('aria-modal')).toBe('true')
    expect(menuButton.closest('section')?.hasAttribute('inert')).toBe(true)

    const sidebarCloseButton = within(sidebar).getByRole('button', { name: '메뉴 닫기' })
    const sidebarFocusableElements = Array.from(
      sidebar.querySelectorAll<HTMLElement>('button, a[href]'),
    )
    const firstSidebarElement = sidebarFocusableElements[0]
    const lastSidebarElement = sidebarFocusableElements.at(-1)
    expect(document.activeElement).toBe(sidebarCloseButton)

    screen.getByRole('textbox', { name: '지원사업 검색어' }).focus()
    fireEvent.keyDown(document, { key: 'Tab' })
    expect(document.activeElement).toBe(firstSidebarElement)

    lastSidebarElement?.focus()
    fireEvent.keyDown(document, { key: 'Tab' })
    expect(document.activeElement).toBe(firstSidebarElement)

    firstSidebarElement?.focus()
    fireEvent.keyDown(document, { key: 'Tab', shiftKey: true })
    expect(document.activeElement).toBe(lastSidebarElement)

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(menuButton.getAttribute('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(menuButton)
    expect(menuButton.closest('section')?.hasAttribute('inert')).toBe(false)

    fireEvent.click(menuButton)
    fireEvent.click(screen.getByRole('button', { name: '메뉴 닫기' }))
    expect(menuButton.getAttribute('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(menuButton)

    fireEvent.click(menuButton)
    const backdrop = document.querySelector('main > div[aria-hidden="true"]')
    expect(backdrop).toBeTruthy()
    fireEvent.click(backdrop!)
    expect(menuButton.getAttribute('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(menuButton)
  })

  it('모바일 메뉴를 연 뒤 데스크톱으로 전환하면 drawer 상태와 포커스를 정리한다', () => {
    const mobileMediaQuery = installMobileMediaQuery()
    renderApp(createAppStore())

    const menuButton = screen.getByRole('button', { name: '메뉴 열기' })
    const sidebar = screen.getByLabelText('지원사업 검색 메뉴')
    const workspace = menuButton.closest('section')
    const primarySidebarAction = within(sidebar).getByRole('button', { name: /새 대화 시작/ })

    fireEvent.click(menuButton)
    expect(sidebar.getAttribute('role')).toBe('dialog')
    expect(workspace?.hasAttribute('inert')).toBe(true)

    act(() => mobileMediaQuery.moveToDesktop())

    expect(menuButton.getAttribute('aria-expanded')).toBe('false')
    expect(sidebar.getAttribute('role')).toBeNull()
    expect(sidebar.getAttribute('aria-modal')).toBeNull()
    expect(workspace?.hasAttribute('inert')).toBe(false)
    expect(document.activeElement).toBe(primarySidebarAction)
  })

  it('새로고침 또는 공유 URL의 직접 진입도 Core API에서 상세 정보를 다시 조회한다', async () => {
    const detail = { ...supportPrograms[0], matchedReasons: [], recommendationScore: null }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(detail))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(
      createAppStore(),
      `/support-programs/detail?sourceCode=${encodeURIComponent(detail.sourceCode)}&sourceProgramId=${encodeURIComponent(detail.id)}`,
    )

    expect(screen.getByRole('heading', { name: '공고 정보를 불러오는 중입니다' })).toBeTruthy()
    await screen.findByRole('heading', { name: detail.title })
    expect(fetchMock).toHaveBeenCalledOnce()
  })

  it('존재하지 않거나 비활성화된 공고는 404 안내를 보여 준다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 404 })))

    renderApp(createAppStore(), '/support-programs/detail?sourceCode=BIZINFO&sourceProgramId=unknown-program')

    await screen.findByRole('heading', { name: '공고 정보를 찾을 수 없습니다' })
    expect(screen.getByText(/존재하지 않거나 더 이상 제공되지 않는 공고입니다/)).toBeTruthy()
    expect(screen.getByRole('link', { name: '← 검색 결과로 돌아가기' })).toBeTruthy()
  })

  it.each([
    '/support-programs/detail',
    '/support-programs/detail?sourceCode=BIZINFO',
    '/support-programs/detail?sourceProgramId=missing-source-code',
    '/support-programs/detail?sourceCode=%20&sourceProgramId=blank-source-code',
    '/support-programs/detail?sourceCode=BIZINFO&sourceProgramId=%20',
  ])('식별자가 누락되거나 공백인 URL(%s)은 API를 호출하지 않는다', (path) => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    renderApp(createAppStore(), path)

    expect(screen.getByRole('heading', { name: '공고 정보를 찾을 수 없습니다' })).toBeTruthy()
    expect(screen.getByText('공고 주소가 올바르지 않습니다. 검색 결과에서 공고를 다시 선택해 주세요.'))
      .toBeTruthy()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('상세 조회 API가 실패하면 안전한 오류 안내를 보여 준다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 503 })))

    renderApp(
      createAppStore(),
      '/support-programs/detail?sourceCode=BIZINFO&sourceProgramId=temporarily-unavailable',
    )

    await screen.findByRole('heading', { name: '공고 정보를 불러오지 못했습니다' })
    expect(screen.getByText(/잠시 후 다시 시도해 주세요/)).toBeTruthy()
  })

  it('퍼센트와 슬래시가 포함된 원본 공고 ID도 URL 인코딩 후 상세 조회한다', async () => {
    const program = {
      ...supportPrograms[0],
      id: 'fixture%20/program?',
      matchedReasons: [],
      recommendationScore: null,
    }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(program))
    vi.stubGlobal('fetch', fetchMock)

    renderApp(
      createAppStore(),
      `/support-programs/detail?sourceCode=${encodeURIComponent(program.sourceCode)}&sourceProgramId=${encodeURIComponent(program.id)}`,
    )

    await screen.findByRole('heading', { name: program.title })
    const detailRequestUrl = new URL(String(fetchMock.mock.calls[0]?.[0]))
    expect(detailRequestUrl.searchParams.get('sourceProgramId')).toBe(program.id)
  })
})

function renderApp(
  appStore: ReturnType<typeof createAppStore>,
  initialEntry = '/',
) {
  return render(
    <Provider store={appStore}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <App />
      </MemoryRouter>
    </Provider>,
  )
}

function passwordInput(id: string): HTMLInputElement {
  const input = document.getElementById(id)
  if (!(input instanceof HTMLInputElement)) throw new Error(`비밀번호 입력이 없습니다: ${id}`)
  return input
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function getProgramCard(title: string): HTMLElement {
  const card = screen.getByRole('heading', { name: title, level: 2 }).closest('article')
  if (!card) throw new Error(`지원사업 카드가 없습니다: ${title}`)
  return card
}

function createReadinessViewModel(overrides: Record<string, unknown> = {}) {
  return {
    data: {
      searchState: 'SEARCHABLE' as const,
      programCount: 12,
      indexReady: true,
      lastSuccessfulSyncAt: '2026-09-05T09:00:00+09:00',
      lastFailedSyncAt: null,
    },
    isError: false,
    isInitialLoading: false,
    isRefreshing: false,
    canSearch: true,
    refetch: vi.fn(),
    ...overrides,
  }
}

function installMobileMediaQuery() {
  let listener: ((event: MediaQueryListEvent) => void) | undefined

  vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({
    addEventListener(_type: string, eventListener: EventListenerOrEventListenerObject | null) {
      if (typeof eventListener === 'function') {
        listener = eventListener as (event: MediaQueryListEvent) => void
      }
    },
    removeEventListener() {
      listener = undefined
    },
  }))

  return {
    moveToDesktop() {
      listener?.({ matches: false } as MediaQueryListEvent)
    },
  }
}

function problemResponse(status: number, code: string) {
  return new Response(JSON.stringify({ status, code }), { status, headers: { 'Content-Type': 'application/problem+json' } })
}
