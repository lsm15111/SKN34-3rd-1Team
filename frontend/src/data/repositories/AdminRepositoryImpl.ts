import type { AppCradle } from '../../app/di/types'
import type { AdminAccountPage } from '../../domain/entities/AdminAccount'
import type { AdminAccountQuery, AdminRepository } from '../../domain/repositories/AdminRepository'
import { AccountApiError } from '../api/accountApi'
import { listAdminAccountsApi, revokeAccountSessionsApi } from '../api/adminApi'
import { toAdminAccountPage } from '../models/AdminDto'
import type { SessionTokenStorage } from '../storage/sessionTokenStorage'

/** 관리자 API를 저장된 세션 토큰으로 호출합니다. 토큰이 없으면 401과 같은 오류를 던집니다. */
export class AdminRepositoryImpl implements AdminRepository {
  private readonly sessionTokenStorage: SessionTokenStorage

  constructor({ sessionTokenStorage }: Pick<AppCradle, 'sessionTokenStorage'>) {
    this.sessionTokenStorage = sessionTokenStorage
  }

  async listAccounts(query: AdminAccountQuery, signal?: AbortSignal): Promise<AdminAccountPage> {
    return toAdminAccountPage(await listAdminAccountsApi(this.requireToken(), query, signal))
  }

  async revokeAccountSessions(accountId: number, signal?: AbortSignal): Promise<'revoked' | 'not-found'> {
    try {
      await revokeAccountSessionsApi(this.requireToken(), accountId, signal)
      return 'revoked'
    } catch (error) {
      if (error instanceof AccountApiError && error.status === 404) return 'not-found'
      throw error
    }
  }

  private requireToken(): string {
    const sessionToken = this.sessionTokenStorage.read()
    if (!sessionToken) throw new AccountApiError(401, 'AUTHENTICATION_REQUIRED')
    return sessionToken
  }
}
