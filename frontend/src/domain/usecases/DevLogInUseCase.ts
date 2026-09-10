import type { AccountTier } from '../entities/Account'
import type { AuthSession } from '../entities/AuthSession'
import type { AccountRepository } from '../repositories/AccountRepository'

type DevLogInRepository = Pick<AccountRepository, 'logInAsDeveloper'>

/** 개발 환경에서 비밀번호 없이 관리자·일반 회원·예시 기업 회원 시드 계정으로 로그인합니다. 개발 로그인 버튼에서만 씁니다. */
export class DevLogInUseCase {
  private readonly repository: DevLogInRepository

  constructor(repository: DevLogInRepository) {
    this.repository = repository
  }

  execute(tier: AccountTier, signal?: AbortSignal): Promise<AuthSession> {
    return this.repository.logInAsDeveloper(tier, signal)
  }
}
