import type {
  AccountLogIn,
  AccountSignUp,
} from '../../domain/repositories/AccountRepository'
import { getCoreApiBaseUrl } from './coreApiConfig'
import {
  accountDtoSchema,
  authSessionResponseDtoSchema,
  businessLookupResponseDtoSchema,
  currentAccountResponseDtoSchema,
  type AccountDto,
  type AuthSessionResponseDto,
  type CompanyDto,
} from '../models/AccountDto'

const BUSINESS_LOOKUP_PATH = '/api/v1/auth/businesses/lookup'
const SIGNUP_PATH = '/api/v1/auth/signup'
const LOGIN_PATH = '/api/v1/auth/login'
const LOGOUT_PATH = '/api/v1/auth/logout'
const CURRENT_ACCOUNT_PATH = '/api/v1/auth/me'

/** 계정 endpoint의 HTTP 상태와 ProblemDetail `code`를 Repository가 업무 결과로 바꿀 수 있게 합니다. */
export class AccountApiError extends Error {
  readonly status: number
  readonly code: string | null

  constructor(status: number, code: string | null) {
    super(`Core API returned HTTP ${status} for the account request.`)
    this.name = 'AccountApiError'
    this.status = status
    this.code = code
  }
}

/** 회원가입 전 사업자등록번호(숫자 10자리)로 국세청 등록 기업을 확인합니다. */
export async function lookupBusinessApi(
  businessNumber: string,
  signal?: AbortSignal,
): Promise<CompanyDto[]> {
  const searchParams = new URLSearchParams({ businessNumber })
  const response = await fetch(
    `${getCoreApiBaseUrl()}${BUSINESS_LOOKUP_PATH}?${searchParams.toString()}`,
    { headers: { Accept: 'application/json' }, signal },
  )
  await rejectFailedResponse(response)

  return businessLookupResponseDtoSchema.parse(await response.json()).businesses
}

export async function signUpApi(
  command: AccountSignUp,
  signal?: AbortSignal,
): Promise<AuthSessionResponseDto> {
  const response = await fetch(`${getCoreApiBaseUrl()}${SIGNUP_PATH}`, {
    method: 'POST',
    headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
    body: JSON.stringify(command),
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
    signal,
  })
  await rejectFailedResponse(response)

  return authSessionResponseDtoSchema.parse(await response.json())
}

export async function logOutApi(sessionToken: string, signal?: AbortSignal): Promise<void> {
  const response = await fetch(`${getCoreApiBaseUrl()}${LOGOUT_PATH}`, {
    method: 'POST',
    headers: { Accept: 'application/json', Authorization: `Bearer ${sessionToken}` },
    signal,
  })
  await rejectFailedResponse(response)
}

export async function getCurrentAccountApi(
  sessionToken: string,
  signal?: AbortSignal,
): Promise<AccountDto> {
  const response = await fetch(`${getCoreApiBaseUrl()}${CURRENT_ACCOUNT_PATH}`, {
    headers: { Accept: 'application/json', Authorization: `Bearer ${sessionToken}` },
    signal,
    // 세션 복원 응답은 로그아웃 뒤에도 재사용되면 안 됩니다.
    cache: 'no-store',
  })
  await rejectFailedResponse(response)

  return accountDtoSchema.parse(currentAccountResponseDtoSchema.parse(await response.json()).account)
}

async function rejectFailedResponse(response: Response): Promise<void> {
  if (response.ok) return

  throw new AccountApiError(response.status, await readProblemCode(response))
}

/** Core API의 application/problem+json 본문에서 `code`만 읽습니다. 본문이 없거나 형식이 다르면 null입니다. */
async function readProblemCode(response: Response): Promise<string | null> {
  try {
    const payload: unknown = await response.json()
    if (typeof payload === 'object' && payload !== null && 'code' in payload) {
      const code = (payload as { code: unknown }).code
      return typeof code === 'string' ? code : null
    }
  } catch {
    return null
  }
  return null
}
