/** Bizno로 확인해 가입 시 저장한 기업입니다. */
export type Company = {
  businessNumber: string
  companyName: string
  businessStatus: string
}

/** 관리자는 서버에서 SQL로만 지정됩니다. */
export type AccountRole = 'USER' | 'ADMIN'

/** 로그인한 담당자 계정입니다. 비밀번호·토큰은 포함하지 않습니다. */
export type Account = {
  email: string
  role: AccountRole
  company: Company
}
