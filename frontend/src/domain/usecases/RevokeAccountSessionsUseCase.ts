import type { AdminRepository } from '../repositories/AdminRepository'

type SessionRevokeRepository = Pick<AdminRepository, 'revokeAccountSessions'>

/** 운영자가 계정의 모든 세션을 종료해 즉시 로그아웃시킵니다. */
export class RevokeAccountSessionsUseCase {
  private readonly repository: SessionRevokeRepository

  constructor(repository: SessionRevokeRepository) {
    this.repository = repository
  }

  execute(accountId: number, signal?: AbortSignal): Promise<'revoked' | 'not-found'> {
    return this.repository.revokeAccountSessions(accountId, signal)
  }
}
