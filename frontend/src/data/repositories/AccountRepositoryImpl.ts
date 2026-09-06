import type { AppCradle } from '../../app/di/types'
import type { Account } from '../../domain/entities/Account'
import type {
  AccountLogIn,
  AccountRepository,
  AccountSignUp,
  BusinessLookupResult,
  LogInResult,
  SignUpResult,
} from '../../domain/repositories/AccountRepository'
import {
  AccountApiError,
  getCurrentAccountApi,
  logInApi,
  logOutApi,
  lookupBusinessApi,
  signUpApi,
} from '../api/accountApi'
import { toAccount, toAuthSession, toCompany } from '../models/AccountDto'
import type { SessionTokenStorage } from '../storage/sessionTokenStorage'

/** Core API 계정 DTO를 Domain 값으로 바꾸고, 세션 토큰의 저장·삭제를 함께 책임지는 Repository adapter입니다. */
export class AccountRepositoryImpl implements AccountRepository {
  private readonly sessionTokenStorage: SessionTokenStorage

  constructor({ sessionTokenStorage }: Pick<AppCradle, 'sessionTokenStorage'>) {
    this.sessionTokenStorage = sessionTokenStorage
  }

  async lookupBusiness(businessNumber: string, signal?: AbortSignal): Promise<BusinessLookupResult> {
    try {
      const companies = await lookupBusinessApi(businessNumber, signal)
      return { outcome: 'found', companies: companies.map(toCompany) }
    } catch (error) {
      if (isBusinessLookupUnavailable(error)) return { outcome: 'lookup-unavailable' }
      throw error
    }
  }

  async signUp(command: AccountSignUp, signal?: AbortSignal): Promise<SignUpResult> {
    try {
      const session = toAuthSession(await signUpApi(command, signal))
      this.sessionTokenStorage.write(session.sessionToken)
      return { outcome: 'session', session }
    } catch (error) {
      if (error instanceof AccountApiError) {
        if (error.status === 409) return { outcome: 'email-taken' }
        if (error.status === 422) return { outcome: 'business-not-found' }
        if (isBusinessLookupUnavailable(error)) return { outcome: 'business-lookup-unavailable' }
      }
      throw error
    }
  }

  async logIn(command: AccountLogIn, signal?: AbortSignal): Promise<LogInResult> {
    try {
      const session = toAuthSession(await logInApi(command, signal))
      this.sessionTokenStorage.write(session.sessionToken)
      return { outcome: 'session', session }
    } catch (error) {
      if (error instanceof AccountApiError && error.status === 401) {
        return { outcome: 'invalid-credentials' }
      }
      throw error
    }
  }

  /** 서버 삭제가 실패하더라도 브라우저의 토큰은 지워 로그아웃 상태를 보장합니다. */
  async logOut(signal?: AbortSignal): Promise<void> {
    const sessionToken = this.sessionTokenStorage.read()
    this.sessionTokenStorage.clear()
    if (!sessionToken) return

    try {
      await logOutApi(sessionToken, signal)
    } catch (error) {
      if (error instanceof AccountApiError && error.status === 401) return
      throw error
    }
  }

  async getCurrentAccount(signal?: AbortSignal): Promise<Account | null> {
    const sessionToken = this.sessionTokenStorage.read()
    if (!sessionToken) return null

    try {
      return toAccount(await getCurrentAccountApi(sessionToken, signal))
    } catch (error) {
      if (error instanceof AccountApiError && error.status === 401) {
        this.sessionTokenStorage.clear()
        return null
      }
      throw error
    }
  }
}

/** Bizno 미설정·장애(503/502/504, BIZNO_* 코드)는 사용자가 나중에 다시 시도할 수 있는 상태입니다. */
function isBusinessLookupUnavailable(error: unknown): boolean {
  return error instanceof AccountApiError
    && [502, 503, 504].includes(error.status)
    && (error.code === null || error.code.startsWith('BIZNO_'))
}
