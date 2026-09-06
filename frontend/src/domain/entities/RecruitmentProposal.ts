import type { Company } from './Account'
import type { RecruitmentPostStatus } from './RecruitmentPost'

/** 서버가 읽을 때 계산한 제안 표시 상태입니다. EXPIRED·CLOSED는 결정 없이 대기 중이던 제안에만 붙습니다. */
export type ProposalStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'WITHDRAWN' | 'EXPIRED' | 'CLOSED'

/** 보낸/받은 제안 화면이 대상 모집글을 소개하는 데 필요한 요약입니다. */
export type ProposalPostSummary = {
  id: number
  status: RecruitmentPostStatus
  title: string
  closesOn: string
  companyName: string
}

export type RecruitmentProposal = {
  id: number
  postId: number
  status: ProposalStatus
  message: string
  createdAt: string
  decidedAt: string | null
  /** 제안을 보낸 기업입니다. */
  company: Company
  post: ProposalPostSummary
  /** 수락된 제안에서만 상대 담당자 이메일이며 그 외에는 null입니다. */
  contactEmail: string | null
}

export const proposalStatusLabels: Record<ProposalStatus, string> = {
  PENDING: '대기 중',
  ACCEPTED: '수락됨',
  DECLINED: '거절됨',
  WITHDRAWN: '철회함',
  EXPIRED: '7일 무응답 종료',
  CLOSED: '모집 종료',
}

export const PROPOSAL_MESSAGE_MAX_LENGTH = 500
