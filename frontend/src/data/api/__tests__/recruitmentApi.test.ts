import { afterEach, describe, expect, it, vi } from 'vitest'

import { supportPrograms } from '../../fixtures/supportPrograms'
import { RecruitmentRepositoryImpl } from '../../repositories/RecruitmentRepositoryImpl'
import { createMemorySessionTokenStorage } from '../../storage/sessionTokenStorage'
import { createRecruitmentPostApi, getRecruitmentPostApi, listRecruitmentPostsApi } from '../recruitmentApi'

afterEach(() => {
  vi.unstubAllGlobals()
})

const program = supportPrograms[0]
const postDto = {
  id: 12,
  status: 'OPEN',
  title: 'AI 실증 과제 데이터 구축·라벨링 참여기관 구합니다',
  body: '학습용 민원 문서 정제와 라벨링을 맡아 주실 참여기관을 찾습니다.',
  ourRole: 'LEAD',
  wantedRole: 'PARTICIPANT',
  wantedCompanyCount: 1,
  wantedRegion: '서울·경기·인천',
  requiredCapabilities: ['데이터 구축', '라벨링 운영'],
  closesOn: '2026-09-20',
  closedEarlyAt: null,
  createdAt: '2026-09-06T12:00:00+09:00',
  updatedAt: '2026-09-06T12:00:00+09:00',
  company: { businessNumber: '1248100998', companyName: '데이터브릿지 주식회사', businessStatus: '계속사업자' },
  program: {
    sourceCode: program.sourceCode,
    sourceProgramId: program.id,
    title: program.title,
    organization: program.organization,
    status: 'OPEN',
    applicationPeriod: program.applicationPeriod,
    applicationEndDate: program.applicationEndDate,
    targetDescription: program.targetDescription,
    sourceUrl: program.sourceUrl,
  },
  proposalCount: 0,
  viewer: { isOwner: false },
}
const draft = {
  title: postDto.title,
  body: postDto.body,
  ourRole: 'LEAD' as const,
  wantedRole: 'PARTICIPANT' as const,
  wantedCompanyCount: 1,
  wantedRegion: '서울·경기·인천',
  requiredCapabilities: ['데이터 구축', '라벨링 운영'],
  closesOn: '2026-09-20',
}

describe('recruitmentApi', () => {
  it('lists open posts with the program filter and only attaches a bearer header when a token exists', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ items: [postDto], page: 0, size: 20, totalCount: 1 }))
      .mockResolvedValueOnce(jsonResponse({ items: [], page: 1, size: 20, totalCount: 1 }))
    vi.stubGlobal('fetch', fetchMock)

    const page = await listRecruitmentPostsApi(null, {
      program: { sourceCode: 'BIZINFO', sourceProgramId: program.id },
      page: 0,
      size: 20,
    })
    await listRecruitmentPostsApi('token', { page: 1, size: 20 })

    expect(page.items[0]?.id).toBe(12)
    const firstUrl = new URL(String(fetchMock.mock.calls[0]?.[0]))
    expect(firstUrl.pathname).toBe('/api/v1/recruitment-posts')
    expect(firstUrl.searchParams.get('sourceCode')).toBe('BIZINFO')
    expect(firstUrl.searchParams.get('sourceProgramId')).toBe(program.id)
    const [, firstInit] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(firstInit).toMatchObject({ headers: { Accept: 'application/json' } })
    expect(firstInit.headers).not.toHaveProperty('Authorization')
    expect(new URL(String(fetchMock.mock.calls[1]?.[0])).searchParams.has('sourceCode')).toBe(false)
    expect(fetchMock.mock.calls[1]?.[1]).toMatchObject({ headers: { Authorization: 'Bearer token' } })
  })

  it('posts the create body in the contract shape', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(postDto, 201))
    vi.stubGlobal('fetch', fetchMock)

    await createRecruitmentPostApi('token', { sourceCode: 'BIZINFO', sourceProgramId: program.id }, draft)

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body))).toEqual({ sourceCode: 'BIZINFO', sourceProgramId: program.id, post: draft })
  })

  it('rejects a post whose program link is not a URL', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ ...postDto, program: { ...postDto.program, sourceUrl: 'nope' } })))

    await expect(getRecruitmentPostApi(null, 12)).rejects.toThrow()
  })
})

describe('RecruitmentRepositoryImpl', () => {
  it('maps a 404 detail to null and a closed program to a null program summary', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(problemResponse(404, 'RECRUITMENT_POST_NOT_FOUND'))
      .mockResolvedValueOnce(jsonResponse({ ...postDto, status: 'CLOSED', program: null })))
    const repository = new RecruitmentRepositoryImpl({ sessionTokenStorage: createMemorySessionTokenStorage() })

    await expect(repository.get(404)).resolves.toBeNull()
    const closed = await repository.get(12)
    expect(closed?.status).toBe('CLOSED')
    expect(closed?.program).toBeNull()
  })

  it.each([
    [422, 'SUPPORT_PROGRAM_NOT_OPEN', 'program-not-open'],
    [422, 'RECRUITMENT_CLOSES_ON_INVALID', 'closes-on-invalid'],
    [422, 'CONTACT_IN_TEXT', 'contact-in-text'],
    [403, 'NOT_POST_OWNER', 'not-owner'],
    [409, 'RECRUITMENT_POST_NOT_OPEN', 'not-open'],
    [404, 'RECRUITMENT_POST_NOT_FOUND', 'not-found'],
  ])('maps HTTP %s %s to the %s save outcome', async (status, code, outcome) => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => Promise.resolve(problemResponse(status, code))))
    const repository = new RecruitmentRepositoryImpl({ sessionTokenStorage: createMemorySessionTokenStorage('token') })

    await expect(repository.create({ sourceCode: 'BIZINFO', sourceProgramId: program.id }, draft)).resolves.toEqual({ outcome })
    await expect(repository.update(12, draft)).resolves.toEqual({ outcome })
  })

  it('returns the saved post and closes early with the mapped outcomes', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(jsonResponse({ ...postDto, viewer: { isOwner: true } }, 201))
      .mockResolvedValueOnce(jsonResponse({ ...postDto, status: 'CLOSED', closedEarlyAt: '2026-09-07T10:00:00+09:00' }))
      .mockResolvedValueOnce(problemResponse(409, 'RECRUITMENT_POST_NOT_OPEN')))
    const repository = new RecruitmentRepositoryImpl({ sessionTokenStorage: createMemorySessionTokenStorage('token') })

    const created = await repository.create({ sourceCode: 'BIZINFO', sourceProgramId: program.id }, draft)
    expect(created.outcome === 'saved' && created.post.viewer.isOwner).toBe(true)
    const closed = await repository.closeEarly(12)
    expect(closed.outcome === 'closed' && closed.post.status).toBe('CLOSED')
    await expect(repository.closeEarly(12)).resolves.toEqual({ outcome: 'not-open' })
  })

  it('refuses to write without a stored token and rethrows unknown failures', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(problemResponse(500, null)))
    vi.stubGlobal('fetch', fetchMock)
    const anonymous = new RecruitmentRepositoryImpl({ sessionTokenStorage: createMemorySessionTokenStorage() })
    const signedIn = new RecruitmentRepositoryImpl({ sessionTokenStorage: createMemorySessionTokenStorage('token') })

    await expect(anonymous.listMine()).rejects.toMatchObject({ status: 401 })
    expect(fetchMock).not.toHaveBeenCalled()
    await expect(signedIn.update(12, draft)).rejects.toMatchObject({ status: 500 })
  })
})

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function problemResponse(status: number, code: string | null) {
  return new Response(JSON.stringify({ status, code }), { status, headers: { 'Content-Type': 'application/problem+json' } })
}
