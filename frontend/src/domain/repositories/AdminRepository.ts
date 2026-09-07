import type { AdminAccountPage } from '../entities/AdminAccount'
import type { AdminRecruitmentPost, AdminRecruitmentPostPage } from '../entities/AdminRecruitmentPost'
import type { RecruitmentPostStatus } from '../entities/RecruitmentPost'

export type AdminAccountQuery = {
  email?: string
  page: number
  size: number
}

export type AdminRecruitmentPostQuery = {
  status?: RecruitmentPostStatus
  page: number
  size: number
}

/** 숨김·해제·강제 마감은 저장 상태에 따라 거부되므로 사유를 결과로 구분합니다. */
export type ModerateRecruitmentPostResult =
  | { outcome: 'done'; post: AdminRecruitmentPost }
  | { outcome: 'already-hidden' }
  | { outcome: 'not-hidden' }
  | { outcome: 'not-open' }
  | { outcome: 'not-found' }

/** 운영자 화면이 Data Layer의 HTTP 세부사항과 분리되도록 하는 Domain 포트입니다. 관리자 세션이 필요합니다. */
export interface AdminRepository {
  listAccounts(query: AdminAccountQuery, signal?: AbortSignal): Promise<AdminAccountPage>
  /** 대상 계정이 없으면 'not-found'입니다. */
  revokeAccountSessions(accountId: number, signal?: AbortSignal): Promise<'revoked' | 'not-found'>
  listRecruitmentPosts(query: AdminRecruitmentPostQuery, signal?: AbortSignal): Promise<AdminRecruitmentPostPage>
  hideRecruitmentPost(postId: number, reason: string, signal?: AbortSignal): Promise<ModerateRecruitmentPostResult>
  unhideRecruitmentPost(postId: number, signal?: AbortSignal): Promise<ModerateRecruitmentPostResult>
  closeRecruitmentPost(postId: number, reason: string, signal?: AbortSignal): Promise<ModerateRecruitmentPostResult>
}
