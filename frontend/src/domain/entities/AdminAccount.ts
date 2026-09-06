import type { AccountRole, Company } from './Account'

/** 운영자가 보는 회원 한 명입니다. 세션·비밀번호 정보는 없습니다. */
export type AdminAccount = {
  id: number
  email: string
  role: AccountRole
  company: Company
  createdAt: string
}

export type AdminAccountPage = {
  items: AdminAccount[]
  page: number
  size: number
  totalCount: number
}
