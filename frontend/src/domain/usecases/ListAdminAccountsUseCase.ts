import type { AdminAccountPage } from '../entities/AdminAccount'
import type { AdminAccountQuery, AdminRepository } from '../repositories/AdminRepository'

type AdminAccountListRepository = Pick<AdminRepository, 'listAccounts'>

/** 이메일 검색어를 정리해 회원 목록 한 페이지를 조회합니다. */
export class ListAdminAccountsUseCase {
  private readonly repository: AdminAccountListRepository

  constructor(repository: AdminAccountListRepository) {
    this.repository = repository
  }

  execute(query: AdminAccountQuery, signal?: AbortSignal): Promise<AdminAccountPage> {
    const email = query.email?.trim()
    return this.repository.listAccounts({
      email: email ? email : undefined,
      page: Math.max(0, query.page),
      size: query.size,
    }, signal)
  }
}
