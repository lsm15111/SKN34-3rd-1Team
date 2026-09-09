import type { AppCradle } from '../../app/di/types'
import type { Account, AccountRole } from '../../domain/entities/Account'
import type { AuthSession } from '../../domain/entities/AuthSession'
import type { OAuthProvider } from '../../domain/entities/OAuthProvider'
import type {
  AccountLogIn,
  AccountRepository,
  AccountSignUp,
  LogInResult,
  SignUpResult,
} from '../../domain/repositories/AccountRepository'
import {
  AccountApiError,
  devLogInApi,
  getCurrentAccountApi,
  getOAuthProvidersApi,
  logInApi,
  logOutApi,
  oauthStartUrl,
  signUpApi,
} from '../api/accountApi'
import { toAccount, toAuthSession, type AuthSessionResponseDto } from '../models/AccountDto'
import type { SessionHintStorage } from '../storage/sessionHintStorage'

/**
 * Core API 계정 DTO를 Domain 값으로 바꾸는 Repository adapter입니다. 세션 토큰은 브라우저가 HttpOnly 쿠키로
 * 관리하므로 앱은 "세션이 있을 수 있다"는 힌트만 저장·삭제합니다.
 */
export class AccountRepositoryImpl implements AccountRepository {
  private readonly sessionHintStorage: SessionHintStorage

  constructor({ sessionHintStorage }: Pick<AppCradle, 'sessionHintStorage'>) {
    this.sessionHintStorage = sessionHintStorage
  }

  /** 409(이미 가입된 이메일)·429는 화면이 구분해 안내하는 업무 결과이고, 그 외 실패는 예외로 둡니다. */
  async signUp(command: AccountSignUp, signal?: AbortSignal): Promise<SignUpResult> {
    try {
      return { outcome: 'session', session: this.rememberSession(await signUpApi(command, signal)) }
    } catch (error) {
      if (error instanceof AccountApiError) {
        if (error.status === 409) return { outcome: 'email-taken' }
        if (error.status === 429) return { outcome: 'rate-limited', retryAfterSeconds: error.retryAfterSeconds }
      }
      throw error
    }
  }

  /** 401·403·429는 화면이 구분해 안내하는 업무 결과이고, 그 외 실패는 예외로 둡니다. */
  async logIn(command: AccountLogIn, signal?: AbortSignal): Promise<LogInResult> {
    try {
      return { outcome: 'session', session: this.rememberSession(await logInApi(command, signal)) }
    } catch (error) {
      if (error instanceof AccountApiError) {
        if (error.status === 401) return { outcome: 'invalid-credentials' }
        if (error.status === 403 && error.code === 'ACCOUNT_SUSPENDED') return { outcome: 'suspended' }
        if (error.status === 429) return { outcome: 'rate-limited', retryAfterSeconds: error.retryAfterSeconds }
      }
      throw error
    }
  }

  async logInAsDeveloper(role: AccountRole, signal?: AbortSignal): Promise<AuthSession> {
    return this.rememberSession(await devLogInApi(role, signal))
  }

  /** 서버 삭제가 실패하더라도 힌트는 지워 다음 시작에 복원을 시도하지 않게 합니다. 이미 없는 세션(401)은 성공으로 봅니다. */
  async logOut(signal?: AbortSignal): Promise<void> {
    const hadSession = this.sessionHintStorage.hasSession()
    this.sessionHintStorage.clear()
    if (!hadSession) return

    try {
      await logOutApi(signal)
    } catch (error) {
      if (error instanceof AccountApiError && error.status === 401) return
      throw error
    }
  }

  /** 세션이 끝났거나(401) 정지된 계정(403)이면 힌트를 지우고 비로그인으로 돌아갑니다. */
  async getCurrentAccount(signal?: AbortSignal): Promise<Account | null> {
    if (!this.sessionHintStorage.hasSession()) return null

    try {
      return toAccount(await getCurrentAccountApi(signal))
    } catch (error) {
      if (error instanceof AccountApiError && (error.status === 401 || error.status === 403)) {
        this.sessionHintStorage.clear()
        return null
      }
      throw error
    }
  }

  getOAuthProviders(signal?: AbortSignal): Promise<OAuthProvider[]> {
    return getOAuthProvidersApi(signal)
  }

  oauthStartUrl(provider: OAuthProvider, next: string): string {
    return oauthStartUrl(provider, next)
  }

  /** 세션 쿠키는 Core 콜백이 이미 발급했으므로 힌트만 남기고 계정을 읽습니다. 쿠키가 없으면 힌트를 되돌립니다. */
  async completeOAuthLogIn(signal?: AbortSignal): Promise<Account | null> {
    this.sessionHintStorage.markSignedIn()
    return this.getCurrentAccount(signal)
  }

  private rememberSession(dto: AuthSessionResponseDto): AuthSession {
    this.sessionHintStorage.markSignedIn()
    return toAuthSession(dto)
  }
}
