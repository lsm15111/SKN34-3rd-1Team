import type { Account, Company } from '../entities/Account'
import type { AuthSession } from '../entities/AuthSession'

/** 이메일·비밀번호·사업자등록번호만으로 계정을 만드는 명령입니다. */
export type AccountSignUp = {
  email: string
  password: string
  businessNumber: string
}

export type AccountLogIn = {
  email: string
  password: string
}

/** 가입 실패 사유는 화면이 다른 안내를 보여야 하므로 예외가 아닌 결과로 구분합니다. */
export type SignUpResult =
  | { outcome: 'session'; session: AuthSession }
  | { outcome: 'email-taken' }
  | { outcome: 'business-not-found' }
  | { outcome: 'business-lookup-unavailable' }

export type LogInResult =
  | { outcome: 'session'; session: AuthSession }
  | { outcome: 'invalid-credentials' }

export type BusinessLookupResult =
  | { outcome: 'found'; companies: Company[] }
  | { outcome: 'lookup-unavailable' }

/** 계정 기능이 Data Layer의 HTTP·저장소 세부사항과 분리되도록 하는 Domain 포트입니다. */
export interface AccountRepository {
  lookupBusiness(businessNumber: string, signal?: AbortSignal): Promise<BusinessLookupResult>
  signUp(command: AccountSignUp, signal?: AbortSignal): Promise<SignUpResult>
  logIn(command: AccountLogIn, signal?: AbortSignal): Promise<LogInResult>
  logOut(signal?: AbortSignal): Promise<void>
  /** 저장된 세션이 없거나 만료됐으면 null입니다. */
  getCurrentAccount(signal?: AbortSignal): Promise<Account | null>
}
