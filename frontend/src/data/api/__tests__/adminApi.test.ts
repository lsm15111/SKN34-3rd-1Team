import { afterEach, describe, expect, it, vi } from 'vitest'

import { AdminRepositoryImpl } from '../../repositories/AdminRepositoryImpl'
import { createMemorySessionTokenStorage } from '../../storage/sessionTokenStorage'
import { listAdminAccountsApi, revokeAccountSessionsApi } from '../adminApi'

afterEach(() => {
  vi.unstubAllGlobals()
})

const adminAccountPage = {
  items: [{
    id: 12,
    email: 'manager@company.co.kr',
    role: 'USER',
    company: { businessNumber: '1248100998', companyName: '삼성전자(주)', businessStatus: '계속사업자' },
    createdAt: '2026-09-06T12:00:00+09:00',
  }],
  page: 0,
  size: 20,
  totalCount: 1,
}

describe('listAdminAccountsApi', () => {
  it('sends the bearer token, paging and email filter, and validates the page', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(adminAccountPage))
    vi.stubGlobal('fetch', fetchMock)

    await expect(listAdminAccountsApi('admin-token', { email: 'manager', page: 2, size: 20 }))
      .resolves.toEqual(adminAccountPage)

    const [requestUrl, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const url = new URL(requestUrl)
    expect(url.pathname).toBe('/api/v1/admin/accounts')
    expect(url.searchParams.get('email')).toBe('manager')
    expect(url.searchParams.get('page')).toBe('2')
    expect(url.searchParams.get('size')).toBe('20')
    expect(init.headers).toEqual({ Accept: 'application/json', Authorization: 'Bearer admin-token' })
  })

  it('omits the email parameter when it is empty and surfaces the problem code', async () => {
    const fetchMock = vi.fn().mockResolvedValue(problemResponse(403, 'ADMIN_REQUIRED'))
    vi.stubGlobal('fetch', fetchMock)

    await expect(listAdminAccountsApi('user-token', { page: 0, size: 20 }))
      .rejects.toMatchObject({ status: 403, code: 'ADMIN_REQUIRED' })
    expect(new URL(String(fetchMock.mock.calls[0]?.[0])).searchParams.has('email')).toBe(false)
  })
})

describe('AdminRepositoryImpl', () => {
  it('maps the page DTO to domain accounts', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(adminAccountPage)))
    const repository = new AdminRepositoryImpl({ sessionTokenStorage: createMemorySessionTokenStorage('admin-token') })

    const page = await repository.listAccounts({ page: 0, size: 20 })

    expect(page.totalCount).toBe(1)
    expect(page.items[0]).toEqual({
      id: 12,
      email: 'manager@company.co.kr',
      role: 'USER',
      company: { businessNumber: '1248100998', companyName: '삼성전자(주)', businessStatus: '계속사업자' },
      createdAt: '2026-09-06T12:00:00+09:00',
    })
  })

  it('posts the revoke request and maps 404 to not-found', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(problemResponse(404, 'ACCOUNT_NOT_FOUND'))
    vi.stubGlobal('fetch', fetchMock)
    const repository = new AdminRepositoryImpl({ sessionTokenStorage: createMemorySessionTokenStorage('admin-token') })

    await expect(repository.revokeAccountSessions(12)).resolves.toBe('revoked')
    await expect(repository.revokeAccountSessions(404)).resolves.toBe('not-found')

    const [requestUrl, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new URL(requestUrl).pathname).toBe('/api/v1/admin/accounts/12/sessions/revoke')
    expect(init.method).toBe('POST')
    await expect(revokeAccountSessionsApi('admin-token', 1)).rejects.toThrow()
  })

  it('fails as unauthenticated without calling the API when no token is stored', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const repository = new AdminRepositoryImpl({ sessionTokenStorage: createMemorySessionTokenStorage() })

    await expect(repository.listAccounts({ page: 0, size: 20 })).rejects.toMatchObject({ status: 401 })
    expect(fetchMock).not.toHaveBeenCalled()
  })
})


const adminRecruitmentPost = {
  id: 12,
  status: 'OPEN',
  title: 'AI 실증 과제 데이터 구축·라벨링 참여기관 구합니다',
  ourRole: 'LEAD',
  wantedRole: 'PARTICIPANT',
  closesOn: '2026-09-20',
  closedEarlyAt: null,
  hiddenAt: null,
  hiddenReason: null,
  createdAt: '2026-09-06T12:00:00+09:00',
  company: { businessNumber: '2208162517', companyName: '데이터브릿지 주식회사', businessStatus: '계속사업자' },
  program: null,
  proposalCount: 2,
}

describe('admin recruitment post api and repository', () => {
  it('lists posts with the status filter and validates the page', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ items: [adminRecruitmentPost], page: 0, size: 20, totalCount: 1 }))
    vi.stubGlobal('fetch', fetchMock)
    const repository = new AdminRepositoryImpl({ sessionTokenStorage: createMemorySessionTokenStorage('admin-token') })

    const page = await repository.listRecruitmentPosts({ status: 'HIDDEN', page: 1, size: 20 })

    expect(page.items[0]?.company.companyName).toBe('데이터브릿지 주식회사')
    expect(page.items[0]?.program).toBeNull()
    const url = new URL(String(fetchMock.mock.calls[0]?.[0]))
    expect(url.pathname).toBe('/api/v1/admin/recruitment-posts')
    expect(url.searchParams.get('status')).toBe('HIDDEN')
    expect(url.searchParams.get('page')).toBe('1')
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ headers: { Authorization: 'Bearer admin-token' } })
  })

  it('sends hide and close with a reason body, unhide without one, and maps state conflicts', async () => {
    const hidden = { ...adminRecruitmentPost, status: 'HIDDEN', hiddenAt: '2026-09-07T10:00:00+09:00', hiddenReason: '연락처 노출' }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(hidden))
      .mockResolvedValueOnce(jsonResponse(adminRecruitmentPost))
      .mockResolvedValueOnce(jsonResponse({ ...adminRecruitmentPost, status: 'CLOSED', closedEarlyAt: '2026-09-07T11:00:00+09:00' }))
      .mockResolvedValueOnce(problemResponse(409, 'RECRUITMENT_POST_ALREADY_HIDDEN'))
      .mockResolvedValueOnce(problemResponse(409, 'RECRUITMENT_POST_NOT_HIDDEN'))
      .mockResolvedValueOnce(problemResponse(409, 'RECRUITMENT_POST_NOT_OPEN'))
      .mockResolvedValueOnce(problemResponse(404, 'RECRUITMENT_POST_NOT_FOUND'))
    vi.stubGlobal('fetch', fetchMock)
    const repository = new AdminRepositoryImpl({ sessionTokenStorage: createMemorySessionTokenStorage('admin-token') })

    const hiddenResult = await repository.hideRecruitmentPost(12, '연락처 노출')
    expect(hiddenResult.outcome === 'done' && hiddenResult.post.hiddenReason).toBe('연락처 노출')
    const unhidden = await repository.unhideRecruitmentPost(12)
    expect(unhidden.outcome === 'done' && unhidden.post.status).toBe('OPEN')
    const closed = await repository.closeRecruitmentPost(12, '공고와 무관')
    expect(closed.outcome === 'done' && closed.post.status).toBe('CLOSED')
    await expect(repository.hideRecruitmentPost(12, '다시')).resolves.toEqual({ outcome: 'already-hidden' })
    await expect(repository.unhideRecruitmentPost(12)).resolves.toEqual({ outcome: 'not-hidden' })
    await expect(repository.closeRecruitmentPost(12, '다시')).resolves.toEqual({ outcome: 'not-open' })
    await expect(repository.unhideRecruitmentPost(404)).resolves.toEqual({ outcome: 'not-found' })

    const calls = fetchMock.mock.calls.slice(0, 3) as [string, RequestInit][]
    expect(calls.map(([url]) => new URL(url).pathname)).toEqual([
      '/api/v1/admin/recruitment-posts/12/hide',
      '/api/v1/admin/recruitment-posts/12/unhide',
      '/api/v1/admin/recruitment-posts/12/close',
    ])
    expect(JSON.parse(String(calls[0]?.[1].body))).toEqual({ reason: '연락처 노출' })
    expect(calls[1]?.[1].body).toBeUndefined()
    expect(calls[1]?.[1].headers).not.toHaveProperty('Content-Type')
    expect(JSON.parse(String(calls[2]?.[1].body))).toEqual({ reason: '공고와 무관' })
  })
})

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function problemResponse(status: number, code: string) {
  return new Response(JSON.stringify({ status, code }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}
