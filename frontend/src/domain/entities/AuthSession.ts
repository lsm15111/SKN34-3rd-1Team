import type { Account } from './Account'

/** 가입·로그인 성공 시 Core API가 발급한 세션입니다. 토큰은 브라우저 저장소에만 보관합니다. */
export type AuthSession = {
  sessionToken: string
  expiresAt: string
  account: Account
}
