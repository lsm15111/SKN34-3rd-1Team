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
