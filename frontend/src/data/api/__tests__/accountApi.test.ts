import { afterEach, describe, expect, it, vi } from 'vitest'

import { AccountRepositoryImpl } from '../../repositories/AccountRepositoryImpl'
import { createMemorySessionTokenStorage } from '../../storage/sessionTokenStorage'
import {
  AccountApiError,
  getCurrentAccountApi,
  logInApi,
  logOutApi,
  lookupBusinessApi,
  signUpApi,
} from '../accountApi'

afterEach(() => {
  vi.unstubAllGlobals()
})

const company = { businessNumber: '1248100998', companyName: '삼성전자(주)', businessStatus: '계속사업자' }
const account = { email: 'manager@company.co.kr', company }
const sessionResponse = { sessionToken: 'session-token', expiresAt: '2026-10-06T12:00:00+09:00', account }

describe('lookupBusinessApi', () => {
  it('sends the normalized business number and returns the businesses array', async () => {
    const controller = new AbortController()
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ businesses: [company] }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(lookupBusinessApi('1248100998', controller.signal)).resolves.toEqual([company])

    const [requestUrl, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const url = new URL(requestUrl)
    expect(url.pathname).toBe('/api/v1/auth/businesses/lookup')
    expect(url.searchParams.get('businessNumber')).toBe('1248100998')
    expect(init.signal).toBe(controller.signal)
  })

  it('exposes the problem code of a failed lookup', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(problemResponse(503, 'BIZNO_NOT_CONFIGURED')))

    await expect(lookupBusinessApi('1248100998')).rejects.toMatchObject({
      name: 'AccountApiError',
      status: 503,
      code: 'BIZNO_NOT_CONFIGURED',
    })
  })
})

describe('signUpApi and logInApi', () => {
  it('posts the sign-up command as JSON and validates the session response', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(sessionResponse, 201))
    vi.stubGlobal('fetch', fetchMock)

    await expect(signUpApi({
      email: 'manager@company.co.kr',
      password: 'password1',
      businessNumber: '1248100998',
    })).resolves.toEqual(sessionResponse)

    const [requestUrl, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new URL(requestUrl).pathname).toBe('/api/v1/auth/signup')
    expect(init.method).toBe('POST')
    expect(init.headers).toEqual({ Accept: 'application/json', 'Content-Type': 'application/json' })
    expect(JSON.parse(String(init.body))).toEqual({
      email: 'manager@company.co.kr',
      password: 'password1',
      businessNumber: '1248100998',
    })
  })

  it('rejects a session response without a token', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ ...sessionResponse, sessionToken: '' })))

    await expect(logInApi({ email: 'manager@company.co.kr', password: 'password1' })).rejects.toThrow()
  })

  it('returns the status even when the failure body is not JSON', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('gateway', { status: 502 })))

    await expect(logInApi({ email: 'manager@company.co.kr', password: 'password1' }))
      .rejects.toMatchObject({ status: 502, code: null })
  })
})

describe('logOutApi and getCurrentAccountApi', () => {
  it('sends the bearer token and unwraps the current account', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ account }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(getCurrentAccountApi('session-token')).resolves.toEqual(account)
    await expect(logOutApi('session-token')).resolves.toBeUndefined()

    const [, meInit] = fetchMock.mock.calls[0] as [string, RequestInit]
    const [logoutUrl, logoutInit] = fetchMock.mock.calls[1] as [string, RequestInit]
    expect(meInit.headers).toEqual({ Accept: 'application/json', Authorization: 'Bearer session-token' })
    expect(meInit.cache).toBe('no-store')
    expect(new URL(logoutUrl).pathname).toBe('/api/v1/auth/logout')
    expect(logoutInit.method).toBe('POST')
  })
})

describe('AccountRepositoryImpl', () => {
  it('stores the session token after a successful sign-up', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(sessionResponse, 201)))
    const storage = createMemorySessionTokenStorage()
    const repository = new AccountRepositoryImpl({ sessionTokenStorage: storage })

    const result = await repository.signUp({
      email: 'manager@company.co.kr',
      password: 'password1',
      businessNumber: '1248100998',
    })

    expect(result).toEqual({ outcome: 'session', session: sessionResponse })
    expect(storage.read()).toBe('session-token')
  })

  it.each([
    [409, 'EMAIL_ALREADY_REGISTERED', 'email-taken'],
    [422, 'BUSINESS_NOT_FOUND', 'business-not-found'],
    [503, 'BIZNO_UNAVAILABLE', 'business-lookup-unavailable'],
    [504, 'BIZNO_TIMEOUT', 'business-lookup-unavailable'],
  ])('maps HTTP %s %s to the %s sign-up outcome', async (status, code, outcome) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(problemResponse(status, code)))
    const storage = createMemorySessionTokenStorage()
    const repository = new AccountRepositoryImpl({ sessionTokenStorage: storage })

    await expect(repository.signUp({
      email: 'manager@company.co.kr',
      password: 'password1',
      businessNumber: '1248100998',
    })).resolves.toEqual({ outcome })
    expect(storage.read()).toBeNull()
  })

  it('maps a 401 login to invalid credentials and rethrows other failures', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(problemResponse(401, 'INVALID_CREDENTIALS'))
      .mockResolvedValueOnce(problemResponse(500, null)))
    const repository = new AccountRepositoryImpl({ sessionTokenStorage: createMemorySessionTokenStorage() })

    await expect(repository.logIn({ email: 'manager@company.co.kr', password: 'wrong' }))
      .resolves.toEqual({ outcome: 'invalid-credentials' })
    await expect(repository.logIn({ email: 'manager@company.co.kr', password: 'wrong' }))
      .rejects.toBeInstanceOf(AccountApiError)
  })

  it('restores the account with the stored token and clears it when the session is gone', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(jsonResponse({ account }))
      .mockResolvedValueOnce(problemResponse(401, 'AUTHENTICATION_REQUIRED')))
    const storage = createMemorySessionTokenStorage('stored-token')
    const repository = new AccountRepositoryImpl({ sessionTokenStorage: storage })

    await expect(repository.getCurrentAccount()).resolves.toEqual(account)
    await expect(repository.getCurrentAccount()).resolves.toBeNull()
    expect(storage.read()).toBeNull()
  })

  it('does not call the API without a stored token', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const repository = new AccountRepositoryImpl({ sessionTokenStorage: createMemorySessionTokenStorage() })

    await expect(repository.getCurrentAccount()).resolves.toBeNull()
    await expect(repository.logOut()).resolves.toBeUndefined()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('clears the token on logout even if the server session is already gone', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(problemResponse(401, 'AUTHENTICATION_REQUIRED')))
    const storage = createMemorySessionTokenStorage('stored-token')
    const repository = new AccountRepositoryImpl({ sessionTokenStorage: storage })

    await expect(repository.logOut()).resolves.toBeUndefined()
    expect(storage.read()).toBeNull()
  })

  it('maps a Bizno outage during lookup to the unavailable outcome', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(jsonResponse({ businesses: [company] }))
      .mockResolvedValueOnce(problemResponse(502, 'BIZNO_UPSTREAM_ERROR')))
    const repository = new AccountRepositoryImpl({ sessionTokenStorage: createMemorySessionTokenStorage() })

    await expect(repository.lookupBusiness('1248100998'))
      .resolves.toEqual({ outcome: 'found', companies: [company] })
    await expect(repository.lookupBusiness('1248100998'))
      .resolves.toEqual({ outcome: 'lookup-unavailable' })
  })
})

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function problemResponse(status: number, code: string | null) {
  return new Response(JSON.stringify({ status, code }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}
