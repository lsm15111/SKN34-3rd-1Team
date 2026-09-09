import type { Account, AccountRole } from '../entities/Account'
import type { AuthSession } from '../entities/AuthSession'
import type { OAuthProvider } from '../entities/OAuthProvider'

export type AccountLogIn = {
  email: string
  password: string
  /** "로그인 상태 유지". 켜면 브라우저를 닫아도 세션 쿠키가 남습니다. */
  rememberMe: boolean
}

export type AccountSignUp = {
  email: string
  password: string
}

/** 가입 실패 사유도 화면이 다른 안내를 보여야 하므로 결과로 구분합니다. 성공하면 서버가 바로 세션을 발급합니다. */
export type SignUpResult =
  | { outcome: 'session'; session: AuthSession }
  | { outcome: 'email-taken' }
  | { outcome: 'rate-limited'; retryAfterSeconds: number | null }

/** 로그인 실패 사유는 화면이 다른 안내를 보여야 하므로 예외가 아닌 결과로 구분합니다. */
export type LogInResult =
  | { outcome: 'session'; session: AuthSession }
  | { outcome: 'invalid-credentials' }
  | { outcome: 'suspended' }
  | { outcome: 'rate-limited'; retryAfterSeconds: number | null }

/** 계정 기능이 Data Layer의 HTTP·저장소 세부사항과 분리되도록 하는 Domain 포트입니다. */
export interface AccountRepository {
  signUp(command: AccountSignUp, signal?: AbortSignal): Promise<SignUpResult>
  logIn(command: AccountLogIn, signal?: AbortSignal): Promise<LogInResult>
  /** 개발 환경 전용. Core API가 개발용 로그인을 켰을 때만 성공하며, 역할별 시드 계정으로 들어갑니다. */
  logInAsDeveloper(role: AccountRole, signal?: AbortSignal): Promise<AuthSession>
  logOut(signal?: AbortSignal): Promise<void>
  /** 저장된 세션이 없거나 만료됐으면 null입니다. */
  getCurrentAccount(signal?: AbortSignal): Promise<Account | null>
  /** Core API에 클라이언트 ID가 설정된 소셜 로그인 제공처입니다. 비어 있으면 버튼을 그리지 않습니다. */
  getOAuthProviders(signal?: AbortSignal): Promise<OAuthProvider[]>
  /** 브라우저를 통째로 보낼 소셜 로그인 시작 주소입니다. 제공처 동의 뒤 Core가 `/oauth/callback`으로 돌려보냅니다. */
  oauthStartUrl(provider: OAuthProvider, next: string): string
  /** 콜백 화면에서 세션 쿠키로 계정을 읽고 "세션 있음" 힌트를 남깁니다. 세션이 없으면 null입니다. */
  completeOAuthLogIn(signal?: AbortSignal): Promise<Account | null>
}
