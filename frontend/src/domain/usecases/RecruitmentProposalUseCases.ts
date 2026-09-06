import type { RecruitmentProposal } from '../entities/RecruitmentProposal'
import type {
  DecideProposalResult,
  ListReceivedProposalsResult,
  RecruitmentRepository,
  SendProposalResult,
} from '../repositories/RecruitmentRepository'

/** 앞뒤 공백을 정리한 메시지로 제안을 보냅니다. 길이·연락처 규칙은 폼과 서버가 검사합니다. */
export class SendProposalUseCase {
  private readonly repository: Pick<RecruitmentRepository, 'sendProposal'>

  constructor(repository: Pick<RecruitmentRepository, 'sendProposal'>) {
    this.repository = repository
  }

  execute(postId: number, message: string, signal?: AbortSignal): Promise<SendProposalResult> {
    return this.repository.sendProposal(postId, message.trim(), signal)
  }
}

/** 작성 기업이 자기 모집글에 받은 제안을 봅니다. */
export class ListReceivedProposalsUseCase {
  private readonly repository: Pick<RecruitmentRepository, 'listReceivedProposals'>

  constructor(repository: Pick<RecruitmentRepository, 'listReceivedProposals'>) {
    this.repository = repository
  }

  execute(postId: number, signal?: AbortSignal): Promise<ListReceivedProposalsResult> {
    return this.repository.listReceivedProposals(postId, signal)
  }
}

export class ListSentProposalsUseCase {
  private readonly repository: Pick<RecruitmentRepository, 'listSentProposals'>

  constructor(repository: Pick<RecruitmentRepository, 'listSentProposals'>) {
    this.repository = repository
  }

  execute(signal?: AbortSignal): Promise<RecruitmentProposal[]> {
    return this.repository.listSentProposals(signal)
  }
}

export type ProposalDecision = 'accept' | 'decline'

/** 작성 기업의 수락·거절입니다. 대기 중인 제안에만 적용됩니다. */
export class DecideProposalUseCase {
  private readonly repository: Pick<RecruitmentRepository, 'acceptProposal' | 'declineProposal'>

  constructor(repository: Pick<RecruitmentRepository, 'acceptProposal' | 'declineProposal'>) {
    this.repository = repository
  }

  execute(proposalId: number, decision: ProposalDecision, signal?: AbortSignal): Promise<DecideProposalResult> {
    return decision === 'accept'
      ? this.repository.acceptProposal(proposalId, signal)
      : this.repository.declineProposal(proposalId, signal)
  }
}

/** 제안 기업의 철회입니다. 대기 중인 제안에만 적용됩니다. */
export class WithdrawProposalUseCase {
  private readonly repository: Pick<RecruitmentRepository, 'withdrawProposal'>

  constructor(repository: Pick<RecruitmentRepository, 'withdrawProposal'>) {
    this.repository = repository
  }

  execute(proposalId: number, signal?: AbortSignal): Promise<DecideProposalResult> {
    return this.repository.withdrawProposal(proposalId, signal)
  }
}
