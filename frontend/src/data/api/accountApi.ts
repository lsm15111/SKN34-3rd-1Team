import type { AccountRole } from '../../domain/entities/Account'
import { isOAuthProvider, type OAuthProvider } from '../../domain/entities/OAuthProvider'
import type { AccountLogIn, AccountSignUp } from '../../domain/repositories/AccountRepository'
import { getCoreApiBaseUrl } from './coreApiConfig'
import {
  accountDtoSchema,
  authSessionResponseDtoSchema,
  currentAccountResponseDtoSchema,
  oauthProvidersResponseDtoSchema,
  type AccountDto,
  type AuthSessionResponseDto,
} from '../models/AccountDto'

const SIGNUP_PATH = '/api/v1/auth/signup'
const LOGIN_PATH = '/api/v1/auth/login'
const DEV_LOGIN_PATH = '/api/v1/auth/dev-login'
const LOGOUT_PATH = '/api/v1/auth/logout'
const CURRENT_ACCOUNT_PATH = '/api/v1/auth/me'
const OAUTH_PATH = '/api/v1/auth/oauth'

/** 계정 endpoint의 HTTP 상태와 ProblemDetail `code`를 Repository가 업무 결과로 바꿀 수 있게 합니다. */
export class AccountApiError extends Error {
  readonly status: number
  readonly code: string | null
  /** 429 응답의 `retryAfterSeconds`. 없거나 정수가 아니면 null입니다. */
  readonly retryAfterSeconds: number | null

  constructor(status: number, code: string | null, retryAfterSeconds: number | null = null) {
    super(`Core API returned HTTP ${status} for the account request.`)
    this.name = 'AccountApiError'
    this.status = status
    this.code = code
    this.retryAfterSeconds = retryAfterSeconds
  }
}

/** 세션은 HttpOnly 쿠키로 오가므로 모든 계정 요청은 쿠키를 함께 보냅니다. Core API의 CORS가 자격 증명을 허용합니다. */
const withSessionCookie: RequestCredentials = 'include'

/** 가입 성공(201)도 로그인과 같은 세션 응답을 돌려주고 세션 쿠키를 함께 내려줍니다. */
export async function signUpApi(
  command: AccountSignUp,
  signal?: AbortSignal,
): Promise<AuthSessionResponseDto> {
  const response = await fetch(`${getCoreApiBaseUrl()}${SIGNUP_PATH}`, {
    method: 'POST',
    headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
    body: JSON.stringify(command),
    credentials: withSessionCookie,
    signal,
  })
  await rejectFailedResponse(response)

  return authSessionResponseDtoSchema.parse(await response.json())
}

export async function logInApi(
  command: AccountLogIn,
  signal?: AbortSignal,
): Promise<AuthSessionResponseDto> {
  const response = await fetch(`${getCoreApiBaseUrl()}${LOGIN_PATH}`, {
    method: 'POST',
    headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
    body: JSON.stringify(command),
    credentials: withSessionCookie,
    signal,
  })
  await rejectFailedResponse(response)

  return authSessionResponseDtoSchema.parse(await response.json())
}

/** Core API가 개발용 로그인을 켰을 때만 존재하는 endpoint입니다. 꺼져 있으면 404입니다. */
export async function devLogInApi(role: AccountRole, signal?: AbortSignal): Promise<AuthSessionResponseDto> {
  const response = await fetch(`${getCoreApiBaseUrl()}${DEV_LOGIN_PATH}`, {
    method: 'POST',
    headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
    body: JSON.stringify({ role }),
    credentials: withSessionCookie,
    signal,
  })
  await rejectFailedResponse(response)

  return authSessionResponseDtoSchema.parse(await response.json())
}

export async function logOutApi(signal?: AbortSignal): Promise<void> {
  const response = await fetch(`${getCoreApiBaseUrl()}${LOGOUT_PATH}`, {
    method: 'POST',
    headers: { Accept: 'application/json' },
    credentials: withSessionCookie,
    signal,
  })
  await rejectFailedResponse(response)
}

export async function getCurrentAccountApi(signal?: AbortSignal): Promise<AccountDto> {
  const response = await fetch(`${getCoreApiBaseUrl()}${CURRENT_ACCOUNT_PATH}`, {
    headers: { Accept: 'application/json' },
    credentials: withSessionCookie,
    signal,
    // 세션 복원 응답은 로그아웃 뒤에도 재사용되면 안 됩니다.
    cache: 'no-store',
  })
  await rejectFailedResponse(response)

  return accountDtoSchema.parse(currentAccountResponseDtoSchema.parse(await response.json()).account)
}

export async function getOAuthProvidersApi(signal?: AbortSignal): Promise<OAuthProvider[]> {
  const response = await fetch(`${getCoreApiBaseUrl()}${OAUTH_PATH}/providers`, {
    headers: { Accept: 'application/json' },
    signal,
  })
  await rejectFailedResponse(response)

  return oauthProvidersResponseDtoSchema.parse(await response.json()).providers.filter(isOAuthProvider)
}

/** fetch가 아니라 `window.location`으로 여는 주소입니다. Core가 제공처 동의 화면으로 302 합니다. */
export function oauthStartUrl(provider: OAuthProvider, next: string): string {
  return `${getCoreApiBaseUrl()}${OAUTH_PATH}/${provider}/start?${new URLSearchParams({ next })}`
}

async function rejectFailedResponse(response: Response): Promise<void> {
  if (response.ok) return

  const problem = await readProblem(response)
  throw new AccountApiError(response.status, problem.code, problem.retryAfterSeconds)
}

/** Core API의 application/problem+json 본문에서 `code`와 `retryAfterSeconds`만 읽습니다. 본문이 없거나 형식이 다르면 null입니다. */
async function readProblem(response: Response): Promise<{ code: string | null; retryAfterSeconds: number | null }> {
  try {
    const payload: unknown = await response.json()
    if (typeof payload !== 'object' || payload === null) return { code: null, retryAfterSeconds: null }
    const record = payload as { code?: unknown; retryAfterSeconds?: unknown }
    return {
      code: typeof record.code === 'string' ? record.code : null,
      retryAfterSeconds: Number.isInteger(record.retryAfterSeconds) ? (record.retryAfterSeconds as number) : null,
    }
  } catch {
    return { code: null, retryAfterSeconds: null }
  }
}
