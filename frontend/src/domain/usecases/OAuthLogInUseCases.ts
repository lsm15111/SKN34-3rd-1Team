import type { Account } from '../entities/Account'
import { isOAuthProvider, type OAuthProvider } from '../entities/OAuthProvider'
import type { AccountRepository } from '../repositories/AccountRepository'

/** 로그인·회원가입 화면이 어떤 소셜 버튼을 그릴지 Core API 설정으로 정합니다. */
export class GetOAuthProvidersUseCase {
  private readonly repository: Pick<AccountRepository, 'getOAuthProviders'>

  constructor(repository: Pick<AccountRepository, 'getOAuthProviders'>) {
    this.repository = repository
  }

  execute(signal?: AbortSignal): Promise<OAuthProvider[]> {
    return this.repository.getOAuthProviders(signal)
  }
}

/**
 * 소셜 로그인은 fetch가 아니라 브라우저 이동으로 시작합니다. 돌아갈 경로는 앱 안 절대 경로만 허용해
 * 로그인 뒤 외부 주소로 새지 않게 합니다.
 */
export class StartOAuthLogInUseCase {
  private readonly repository: Pick<AccountRepository, 'oauthStartUrl'>

  constructor(repository: Pick<AccountRepository, 'oauthStartUrl'>) {
    this.repository = repository
  }

  execute(provider: OAuthProvider, next: string): string {
    if (!isOAuthProvider(provider)) throw new RangeError(`unknown OAuth provider: ${provider}`)
    const safeNext = next.startsWith('/') && !next.startsWith('//') ? next : '/app/chat'
    return this.repository.oauthStartUrl(provider, safeNext)
  }
}

/** 제공처에서 돌아온 콜백 화면이 세션 쿠키로 계정을 읽어 로그인 상태를 만듭니다. 세션이 없으면 null입니다. */
export class CompleteOAuthLogInUseCase {
  private readonly repository: Pick<AccountRepository, 'completeOAuthLogIn'>

  constructor(repository: Pick<AccountRepository, 'completeOAuthLogIn'>) {
    this.repository = repository
  }

  execute(signal?: AbortSignal): Promise<Account | null> {
    return this.repository.completeOAuthLogIn(signal)
  }
}
