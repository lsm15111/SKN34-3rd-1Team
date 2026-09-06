import type { AdminAccountPage } from '../entities/AdminAccount'

export type AdminAccountQuery = {
  email?: string
  page: number
  size: number
}

/** 운영자 화면이 Data Layer의 HTTP 세부사항과 분리되도록 하는 Domain 포트입니다. 관리자 세션이 필요합니다. */
export interface AdminRepository {
  listAccounts(query: AdminAccountQuery, signal?: AbortSignal): Promise<AdminAccountPage>
  /** 대상 계정이 없으면 'not-found'입니다. */
  revokeAccountSessions(accountId: number, signal?: AbortSignal): Promise<'revoked' | 'not-found'>
}
