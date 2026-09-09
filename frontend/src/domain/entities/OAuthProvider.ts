/** 소셜 로그인 제공처입니다. 값은 Core API 경로 세그먼트와 같습니다. */
export const oauthProviders = ['google', 'kakao'] as const

export type OAuthProvider = typeof oauthProviders[number]

export const oauthProviderLabels: Record<OAuthProvider, string> = {
  google: 'Google',
  kakao: '카카오',
}

export function isOAuthProvider(value: string): value is OAuthProvider {
  return (oauthProviders as readonly string[]).includes(value)
}

/** Core API 콜백이 `?error=`로 알려 주는 실패 이유입니다. 모르는 값은 `FAILED`로 다룹니다. */
export const oauthLoginErrorCodes = [
  'PROVIDER_NOT_CONFIGURED',
  'PROVIDER_DENIED',
  'STATE_MISMATCH',
  'EMAIL_REQUIRED',
  'EMAIL_NOT_VERIFIED',
  'PROVIDER_UNAVAILABLE',
  'ACCOUNT_SUSPENDED',
  'RATE_LIMITED',
  'FAILED',
] as const

export type OAuthLoginErrorCode = typeof oauthLoginErrorCodes[number]

export function toOAuthLoginErrorCode(value: string | null): OAuthLoginErrorCode | null {
  if (value === null) return null
  return (oauthLoginErrorCodes as readonly string[]).includes(value) ? (value as OAuthLoginErrorCode) : 'FAILED'
}
