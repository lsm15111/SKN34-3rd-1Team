import type { AppCradle } from '../../app/di/types'
import type { AdminAccountPage } from '../../domain/entities/AdminAccount'
import type { AdminRecruitmentPostPage } from '../../domain/entities/AdminRecruitmentPost'
import type {
  AdminAccountQuery,
  AdminRecruitmentPostQuery,
  AdminRepository,
  ModerateRecruitmentPostResult,
} from '../../domain/repositories/AdminRepository'
import { AccountApiError } from '../api/accountApi'
import {
  listAdminAccountsApi,
  listAdminRecruitmentPostsApi,
  moderateRecruitmentPostApi,
  revokeAccountSessionsApi,
  type AdminPostModeration,
} from '../api/adminApi'
import { toAdminAccountPage, toAdminRecruitmentPost, toAdminRecruitmentPostPage } from '../models/AdminDto'
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

  async listRecruitmentPosts(query: AdminRecruitmentPostQuery, signal?: AbortSignal): Promise<AdminRecruitmentPostPage> {
    return toAdminRecruitmentPostPage(await listAdminRecruitmentPostsApi(this.requireToken(), query, signal))
  }

  hideRecruitmentPost(postId: number, reason: string, signal?: AbortSignal): Promise<ModerateRecruitmentPostResult> {
    return this.moderate(postId, 'hide', reason, signal)
  }

  unhideRecruitmentPost(postId: number, signal?: AbortSignal): Promise<ModerateRecruitmentPostResult> {
    return this.moderate(postId, 'unhide', null, signal)
  }

  closeRecruitmentPost(postId: number, reason: string, signal?: AbortSignal): Promise<ModerateRecruitmentPostResult> {
    return this.moderate(postId, 'close', reason, signal)
  }

  private async moderate(
    postId: number,
    action: AdminPostModeration,
    reason: string | null,
    signal?: AbortSignal,
  ): Promise<ModerateRecruitmentPostResult> {
    try {
      return { outcome: 'done', post: toAdminRecruitmentPost(await moderateRecruitmentPostApi(this.requireToken(), postId, action, reason, signal)) }
    } catch (error) {
      if (error instanceof AccountApiError) {
        if (error.status === 404) return { outcome: 'not-found' }
        if (error.status === 409 && error.code === 'RECRUITMENT_POST_ALREADY_HIDDEN') return { outcome: 'already-hidden' }
        if (error.status === 409 && error.code === 'RECRUITMENT_POST_NOT_HIDDEN') return { outcome: 'not-hidden' }
        if (error.status === 409) return { outcome: 'not-open' }
      }
      throw error
    }
  }

  private requireToken(): string {
    const sessionToken = this.sessionTokenStorage.read()
    if (!sessionToken) throw new AccountApiError(401, 'AUTHENTICATION_REQUIRED')
    return sessionToken
  }
}
