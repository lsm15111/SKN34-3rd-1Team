import type { Company } from './Account'
import type { LinkedProgram, RecruitmentPostStatus, RecruitmentRole } from './RecruitmentPost'

/** 운영자가 보는 모집글 한 건입니다. 공개 모집글과 달리 숨김 시각·사유가 있고 조회자 관계는 없습니다. */
export type AdminRecruitmentPost = {
  id: number
  status: RecruitmentPostStatus
  title: string
  ourRole: Exclude<RecruitmentRole, 'DEMAND'>
  wantedRole: RecruitmentRole
  closesOn: string
  closedEarlyAt: string | null
  hiddenAt: string | null
  hiddenReason: string | null
  createdAt: string
  company: Company
  program: LinkedProgram | null
  proposalCount: number
}

export type AdminRecruitmentPostPage = {
  items: AdminRecruitmentPost[]
  page: number
  size: number
  totalCount: number
}
