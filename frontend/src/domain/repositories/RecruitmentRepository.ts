import type {
  RecruitmentPost,
  RecruitmentPostDraft,
  RecruitmentPostPage,
} from '../entities/RecruitmentPost'
import type { RecruitmentProposal } from '../entities/RecruitmentProposal'
import type { SupportProgramIdentity } from './SupportProgramRepository'

export type RecruitmentPostQuery = {
  program?: SupportProgramIdentity
  page: number
  size: number
}

/** 등록·수정 실패 사유는 화면이 다른 안내를 보여야 하므로 예외가 아닌 결과로 구분합니다. */
export type SaveRecruitmentPostResult =
  | { outcome: 'saved'; post: RecruitmentPost }
  | { outcome: 'program-not-open' }
  | { outcome: 'closes-on-invalid' }
  | { outcome: 'contact-in-text' }
  | { outcome: 'not-owner' }
  | { outcome: 'not-open' }
  | { outcome: 'not-found' }

export type CloseRecruitmentPostResult =
  | { outcome: 'closed'; post: RecruitmentPost }
  | { outcome: 'not-owner' }
  | { outcome: 'not-open' }
  | { outcome: 'not-found' }

export type SendProposalResult =
  | { outcome: 'sent'; proposal: RecruitmentProposal }
  | { outcome: 'own-post' }
  | { outcome: 'already-exists' }
  | { outcome: 'not-open' }
  | { outcome: 'contact-in-text' }
  | { outcome: 'not-found' }

export type ListReceivedProposalsResult =
  | { outcome: 'loaded'; proposals: RecruitmentProposal[] }
  | { outcome: 'not-owner' }
  | { outcome: 'not-found' }

/** 수락·거절·철회는 대기 중인 제안에만 되므로 이미 결정·만료된 경우를 결과로 구분합니다. */
export type DecideProposalResult =
  | { outcome: 'decided'; proposal: RecruitmentProposal }
  | { outcome: 'not-owner' }
  | { outcome: 'not-pending' }
  | { outcome: 'not-found' }

/** 파트너 모집 화면이 Data Layer의 HTTP 세부사항과 분리되도록 하는 Domain 포트입니다. */
export interface RecruitmentRepository {
  listOpen(query: RecruitmentPostQuery, signal?: AbortSignal): Promise<RecruitmentPostPage>
  /** 없거나 숨김·종료되어 볼 수 없는 글이면 null입니다. */
  get(postId: number, signal?: AbortSignal): Promise<RecruitmentPost | null>
  create(
    program: SupportProgramIdentity,
    draft: RecruitmentPostDraft,
    signal?: AbortSignal,
  ): Promise<SaveRecruitmentPostResult>
  update(postId: number, draft: RecruitmentPostDraft, signal?: AbortSignal): Promise<SaveRecruitmentPostResult>
  closeEarly(postId: number, signal?: AbortSignal): Promise<CloseRecruitmentPostResult>
  listMine(signal?: AbortSignal): Promise<RecruitmentPost[]>
  sendProposal(postId: number, message: string, signal?: AbortSignal): Promise<SendProposalResult>
  listReceivedProposals(postId: number, signal?: AbortSignal): Promise<ListReceivedProposalsResult>
  listSentProposals(signal?: AbortSignal): Promise<RecruitmentProposal[]>
  acceptProposal(proposalId: number, signal?: AbortSignal): Promise<DecideProposalResult>
  declineProposal(proposalId: number, signal?: AbortSignal): Promise<DecideProposalResult>
  withdrawProposal(proposalId: number, signal?: AbortSignal): Promise<DecideProposalResult>
}
