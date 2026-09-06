import type { Company } from './Account'
import type { SupportProgramStatus } from './SupportProgram'

/** 컨소시엄에서 맡는 역할입니다. 작성 기업은 주관·참여만, 수요처는 찾는 역할로만 씁니다. */
export type RecruitmentRole = 'LEAD' | 'PARTICIPANT' | 'DEMAND'
export type RecruitmentAuthorRole = Exclude<RecruitmentRole, 'DEMAND'>

/** 서버가 읽을 때 계산한 표시 상태입니다. */
export type RecruitmentPostStatus = 'OPEN' | 'CLOSED' | 'HIDDEN'

/** 모집글 화면이 보여 주는 연결 공고 요약입니다. */
export type LinkedProgram = {
  sourceCode: string
  sourceProgramId: string
  title: string
  organization: string
  status: SupportProgramStatus
  applicationPeriod: string
  applicationEndDate: string | null
  targetDescription: string
  sourceUrl: string
}

/** 작성·수정 폼이 보내는 모집 조건입니다. 연결 공고는 등록 뒤 바꾸지 않습니다. */
export type RecruitmentPostDraft = {
  title: string
  body: string
  ourRole: RecruitmentAuthorRole
  wantedRole: RecruitmentRole
  wantedCompanyCount: number
  wantedRegion: string
  requiredCapabilities: string[]
  closesOn: string
}

export type RecruitmentPost = RecruitmentPostDraft & {
  id: number
  status: RecruitmentPostStatus
  closedEarlyAt: string | null
  createdAt: string
  updatedAt: string
  company: Company
  /** 연결 공고가 더 이상 공개되지 않으면 null이며 그때 status는 CLOSED입니다. */
  program: LinkedProgram | null
  proposalCount: number
  viewer: { isOwner: boolean }
}

export type RecruitmentPostPage = {
  items: RecruitmentPost[]
  page: number
  size: number
  totalCount: number
}

export const recruitmentRoleLabels: Record<RecruitmentRole, string> = {
  LEAD: '주관기관',
  PARTICIPANT: '참여기관',
  DEMAND: '수요처',
}

export const recruitmentPostStatusLabels: Record<RecruitmentPostStatus, string> = {
  OPEN: '모집 중',
  CLOSED: '모집 종료',
  HIDDEN: '운영자 숨김',
}
