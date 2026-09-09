import { describe, expect, it, vi } from 'vitest'

import { CompleteOAuthLogInUseCase, GetOAuthProvidersUseCase, StartOAuthLogInUseCase } from './OAuthLogInUseCases'

describe('OAuth log-in use cases', () => {
  it('reads the configured providers and builds start urls only for in-app return paths', async () => {
    const getOAuthProviders = vi.fn().mockResolvedValue(['google'])
    await expect(new GetOAuthProvidersUseCase({ getOAuthProviders }).execute()).resolves.toEqual(['google'])

    const oauthStartUrl = vi.fn((provider: string, next: string) => `http://api.test/api/v1/auth/oauth/${provider}/start?next=${encodeURIComponent(next)}`)
    const start = new StartOAuthLogInUseCase({ oauthStartUrl })
    expect(start.execute('kakao', '/app/partners')).toBe('http://api.test/api/v1/auth/oauth/kakao/start?next=%2Fapp%2Fpartners')
    expect(start.execute('google', 'https://evil.example')).toContain('next=%2Fapp%2Fchat')
    expect(start.execute('google', '//evil.example')).toContain('next=%2Fapp%2Fchat')
    expect(() => start.execute('naver' as never, '/app/chat')).toThrow(RangeError)
  })

  it('completes the login through the repository and passes the abort signal', async () => {
    const completeOAuthLogIn = vi.fn().mockResolvedValue(null)
    const controller = new AbortController()
    await expect(new CompleteOAuthLogInUseCase({ completeOAuthLogIn }).execute(controller.signal)).resolves.toBeNull()
    expect(completeOAuthLogIn).toHaveBeenCalledWith(controller.signal)
  })
})
