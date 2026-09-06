import type { AppCradle } from '../../app/di/types'
import type {
  RecruitmentPost,
  RecruitmentPostDraft,
  RecruitmentPostPage,
} from '../../domain/entities/RecruitmentPost'
import type { RecruitmentProposal } from '../../domain/entities/RecruitmentProposal'
import type {
  CloseRecruitmentPostResult,
  DecideProposalResult,
  ListReceivedProposalsResult,
  RecruitmentPostQuery,
  RecruitmentRepository,
  SaveRecruitmentPostResult,
  SendProposalResult,
} from '../../domain/repositories/RecruitmentRepository'
import type { SupportProgramIdentity } from '../../domain/repositories/SupportProgramRepository'
import { AccountApiError } from '../api/accountApi'
import {
  closeRecruitmentPostApi,
  createRecruitmentPostApi,
  decideProposalApi,
  getRecruitmentPostApi,
  listMyRecruitmentPostsApi,
  listReceivedProposalsApi,
  listRecruitmentPostsApi,
  listSentProposalsApi,
  sendProposalApi,
  updateRecruitmentPostApi,
  type ProposalAction,
} from '../api/recruitmentApi'
import { toRecruitmentPost, toRecruitmentPostPage } from '../models/RecruitmentPostDto'
import { toRecruitmentProposal } from '../models/RecruitmentProposalDto'
import type { SessionTokenStorage } from '../storage/sessionTokenStorage'

/** 모집글 DTO를 Domain 값으로 바꾸고 업무 오류 코드를 결과로 변환하는 Repository adapter입니다. */
export class RecruitmentRepositoryImpl implements RecruitmentRepository {
  private readonly sessionTokenStorage: SessionTokenStorage

  constructor({ sessionTokenStorage }: Pick<AppCradle, 'sessionTokenStorage'>) {
    this.sessionTokenStorage = sessionTokenStorage
  }

  async listOpen(query: RecruitmentPostQuery, signal?: AbortSignal): Promise<RecruitmentPostPage> {
    return toRecruitmentPostPage(await listRecruitmentPostsApi(this.sessionTokenStorage.read(), query, signal))
  }

  async get(postId: number, signal?: AbortSignal): Promise<RecruitmentPost | null> {
    try {
      return toRecruitmentPost(await getRecruitmentPostApi(this.sessionTokenStorage.read(), postId, signal))
    } catch (error) {
      if (error instanceof AccountApiError && error.status === 404) return null
      throw error
    }
  }

  async create(
    program: SupportProgramIdentity,
    draft: RecruitmentPostDraft,
    signal?: AbortSignal,
  ): Promise<SaveRecruitmentPostResult> {
    try {
      return { outcome: 'saved', post: toRecruitmentPost(await createRecruitmentPostApi(this.requireToken(), program, draft, signal)) }
    } catch (error) {
      return toSaveFailure(error)
    }
  }

  async update(postId: number, draft: RecruitmentPostDraft, signal?: AbortSignal): Promise<SaveRecruitmentPostResult> {
    try {
      return { outcome: 'saved', post: toRecruitmentPost(await updateRecruitmentPostApi(this.requireToken(), postId, draft, signal)) }
    } catch (error) {
      return toSaveFailure(error)
    }
  }

  async closeEarly(postId: number, signal?: AbortSignal): Promise<CloseRecruitmentPostResult> {
    try {
      return { outcome: 'closed', post: toRecruitmentPost(await closeRecruitmentPostApi(this.requireToken(), postId, signal)) }
    } catch (error) {
      if (error instanceof AccountApiError) {
        if (error.status === 403) return { outcome: 'not-owner' }
        if (error.status === 404) return { outcome: 'not-found' }
        if (error.status === 409) return { outcome: 'not-open' }
      }
      throw error
    }
  }

  async listMine(signal?: AbortSignal): Promise<RecruitmentPost[]> {
    return (await listMyRecruitmentPostsApi(this.requireToken(), signal)).map(toRecruitmentPost)
  }

  async sendProposal(postId: number, message: string, signal?: AbortSignal): Promise<SendProposalResult> {
    try {
      return { outcome: 'sent', proposal: toRecruitmentProposal(await sendProposalApi(this.requireToken(), postId, message, signal)) }
    } catch (error) {
      if (error instanceof AccountApiError) {
        if (error.status === 403 && error.code === 'OWN_POST') return { outcome: 'own-post' }
        if (error.status === 404) return { outcome: 'not-found' }
        if (error.status === 409 && error.code === 'PROPOSAL_ALREADY_EXISTS') return { outcome: 'already-exists' }
        if (error.status === 409) return { outcome: 'not-open' }
        if (error.status === 422 && error.code === 'CONTACT_IN_TEXT') return { outcome: 'contact-in-text' }
      }
      throw error
    }
  }

  async listReceivedProposals(postId: number, signal?: AbortSignal): Promise<ListReceivedProposalsResult> {
    try {
      const proposals = await listReceivedProposalsApi(this.requireToken(), postId, signal)
      return { outcome: 'loaded', proposals: proposals.map(toRecruitmentProposal) }
    } catch (error) {
      if (error instanceof AccountApiError) {
        if (error.status === 403) return { outcome: 'not-owner' }
        if (error.status === 404) return { outcome: 'not-found' }
      }
      throw error
    }
  }

  async listSentProposals(signal?: AbortSignal): Promise<RecruitmentProposal[]> {
    return (await listSentProposalsApi(this.requireToken(), signal)).map(toRecruitmentProposal)
  }

  acceptProposal(proposalId: number, signal?: AbortSignal): Promise<DecideProposalResult> {
    return this.decide(proposalId, 'accept', signal)
  }

  declineProposal(proposalId: number, signal?: AbortSignal): Promise<DecideProposalResult> {
    return this.decide(proposalId, 'decline', signal)
  }

  withdrawProposal(proposalId: number, signal?: AbortSignal): Promise<DecideProposalResult> {
    return this.decide(proposalId, 'withdraw', signal)
  }

  private async decide(proposalId: number, action: ProposalAction, signal?: AbortSignal): Promise<DecideProposalResult> {
    try {
      return { outcome: 'decided', proposal: toRecruitmentProposal(await decideProposalApi(this.requireToken(), proposalId, action, signal)) }
    } catch (error) {
      if (error instanceof AccountApiError) {
        if (error.status === 403) return { outcome: 'not-owner' }
        if (error.status === 404) return { outcome: 'not-found' }
        if (error.status === 409) return { outcome: 'not-pending' }
      }
      throw error
    }
  }

  private requireToken(): string {
    const sessionToken = this.sessionTokenStorage.read()
    if (!sessionToken) throw new AccountApiError(401, 'AUTHENTICATION_REQUIRED')
    return sessionToken
  }
}

function toSaveFailure(error: unknown): SaveRecruitmentPostResult {
  if (error instanceof AccountApiError) {
    if (error.status === 403) return { outcome: 'not-owner' }
    if (error.status === 404) return { outcome: 'not-found' }
    if (error.status === 409) return { outcome: 'not-open' }
    if (error.status === 422) {
      if (error.code === 'SUPPORT_PROGRAM_NOT_OPEN') return { outcome: 'program-not-open' }
      if (error.code === 'RECRUITMENT_CLOSES_ON_INVALID') return { outcome: 'closes-on-invalid' }
      if (error.code === 'CONTACT_IN_TEXT') return { outcome: 'contact-in-text' }
    }
  }
  throw error
}
