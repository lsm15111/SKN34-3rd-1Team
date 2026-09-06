package ai.govbiz.core.recruitment.service

import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.account.repository.AccountRepository
import ai.govbiz.core.recruitment.domain.ContactPatternPolicy
import ai.govbiz.core.recruitment.domain.ProposalDecision
import ai.govbiz.core.recruitment.domain.ProposalStatus
import ai.govbiz.core.recruitment.domain.ProposalStatusResolver
import ai.govbiz.core.recruitment.domain.RecruitmentPostStatus
import ai.govbiz.core.recruitment.domain.RecruitmentPostStatusResolver
import ai.govbiz.core.recruitment.repository.RecruitmentPostRepository
import ai.govbiz.core.recruitment.repository.RecruitmentProposalRepository
import ai.govbiz.core.recruitment.repository.StoredRecruitmentPost
import ai.govbiz.core.recruitment.repository.StoredRecruitmentProposal
import ai.govbiz.core.recruitment.service.dto.RecruitmentProposalResult
import ai.govbiz.core.recruitment.service.exception.ContactInTextException
import ai.govbiz.core.recruitment.service.exception.NotPostOwnerException
import ai.govbiz.core.recruitment.service.exception.NotProposalOwnerException
import ai.govbiz.core.recruitment.service.exception.OwnPostProposalException
import ai.govbiz.core.recruitment.service.exception.ProposalAlreadyExistsException
import ai.govbiz.core.recruitment.service.exception.ProposalNotFoundException
import ai.govbiz.core.recruitment.service.exception.ProposalNotPendingException
import ai.govbiz.core.recruitment.service.exception.RecruitmentPostNotFoundException
import ai.govbiz.core.recruitment.service.exception.RecruitmentPostNotOpenException
import ai.govbiz.core.supportprogram.repository.SupportProgramRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service

/**
 * 모집글에 보내는 참여 제안의 보내기·받은/보낸 목록·수락·거절·철회입니다.
 *
 * 담당자 연락처는 제안이 수락됐을 때만 상대에게 공개하며, 본문의 연락처 문구는 모집글과 같은 규칙으로 거부합니다.
 * 제안 상태는 저장하지 않고 조회 시 계산합니다.
 */
@Service
class RecruitmentProposalService(
    private val proposalRepository: RecruitmentProposalRepository,
    private val postRepository: RecruitmentPostRepository,
    private val supportProgramRepository: SupportProgramRepository,
    private val accountRepository: AccountRepository,
    @param:Qualifier("seoulClock") private val clock: Clock,
) {

    /** 모집 중인 남의 글에만, 기업당 한 번만 보낼 수 있습니다. */
    fun send(proposer: Account, postId: Long, message: String): RecruitmentProposalResult {
        if (ContactPatternPolicy.containsContact(message)) throw ContactInTextException()
        val stored = postRepository.findById(postId) ?: throw RecruitmentPostNotFoundException()
        if (stored.post.isOwnedBy(proposer.company.id)) throw OwnPostProposalException()
        val postStatus = statusOf(stored)
        if (postStatus != RecruitmentPostStatus.OPEN) throw RecruitmentPostNotOpenException()

        val proposal = try {
            proposalRepository.create(postId, proposer.company.id, proposer.id, message)
        } catch (exception: DuplicateKeyException) {
            throw ProposalAlreadyExistsException()
        }
        return proposal.toResult(stored, postStatus, viewer = proposer)
    }

    /** 작성 기업만 자기 글의 받은 제안을 봅니다. */
    fun listReceived(owner: Account, postId: Long): List<RecruitmentProposalResult> {
        val stored = postRepository.findById(postId) ?: throw RecruitmentPostNotFoundException()
        if (!stored.post.isOwnedBy(owner.company.id)) throw NotPostOwnerException()
        val postStatus = statusOf(stored)
        return java.util.List.copyOf(
            proposalRepository.findByPostId(postId).map { it.toResult(stored, postStatus, viewer = owner) },
        )
    }

    /** 내 기업이 보낸 제안 전체입니다. 대상 글이 종료·숨김돼도 이력은 남습니다. */
    fun listSent(proposer: Account): List<RecruitmentProposalResult> =
        java.util.List.copyOf(
            proposalRepository.findByCompanyId(proposer.company.id).map { proposal ->
                val stored = requireNotNull(postRepository.findById(proposal.proposal.postId)) {
                    "recruitment post ${proposal.proposal.postId} must exist for proposal ${proposal.proposal.id}"
                }
                proposal.toResult(stored, statusOf(stored), viewer = proposer)
            },
        )

    fun accept(owner: Account, proposalId: Long): RecruitmentProposalResult =
        decideAsOwner(owner, proposalId, ProposalDecision.ACCEPTED)

    fun decline(owner: Account, proposalId: Long): RecruitmentProposalResult =
        decideAsOwner(owner, proposalId, ProposalDecision.DECLINED)

    /** 제안 기업만, 아직 PENDING인 제안만 철회합니다. */
    fun withdraw(proposer: Account, proposalId: Long): RecruitmentProposalResult {
        val (proposal, stored) = requireProposal(proposalId)
        if (!proposal.proposal.isSentBy(proposer.company.id)) throw NotProposalOwnerException()
        return decide(proposal, stored, ProposalDecision.WITHDRAWN, viewer = proposer)
    }

    private fun decideAsOwner(owner: Account, proposalId: Long, decision: ProposalDecision): RecruitmentProposalResult {
        val (proposal, stored) = requireProposal(proposalId)
        if (!stored.post.isOwnedBy(owner.company.id)) throw NotPostOwnerException()
        return decide(proposal, stored, decision, viewer = owner)
    }

    private fun decide(
        proposal: StoredRecruitmentProposal,
        stored: StoredRecruitmentPost,
        decision: ProposalDecision,
        viewer: Account,
    ): RecruitmentProposalResult {
        val postStatus = statusOf(stored)
        if (ProposalStatusResolver.resolve(proposal.proposal, postStatus, now()) != ProposalStatus.PENDING) {
            throw ProposalNotPendingException()
        }
        if (!proposalRepository.decide(proposal.proposal.id, decision)) throw ProposalNotPendingException()
        return requireNotNull(proposalRepository.findById(proposal.proposal.id)).toResult(stored, postStatus, viewer)
    }

    private fun requireProposal(proposalId: Long): Pair<StoredRecruitmentProposal, StoredRecruitmentPost> {
        val proposal = proposalRepository.findById(proposalId) ?: throw ProposalNotFoundException()
        val stored = postRepository.findById(proposal.proposal.postId) ?: throw ProposalNotFoundException()
        return proposal to stored
    }

    private fun statusOf(stored: StoredRecruitmentPost): RecruitmentPostStatus {
        val program = supportProgramRepository
            .findPresentBySourceAndProgramId(stored.post.sourceCode, stored.post.sourceProgramId)
            ?.program
        return RecruitmentPostStatusResolver.resolve(stored.post, program, LocalDate.now(clock))
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock)

    private fun StoredRecruitmentProposal.toResult(
        stored: StoredRecruitmentPost,
        postStatus: RecruitmentPostStatus,
        viewer: Account,
    ): RecruitmentProposalResult {
        val status = ProposalStatusResolver.resolve(proposal, postStatus, now())
        return RecruitmentProposalResult(
            proposal = proposal,
            status = status,
            company = company,
            post = stored.post,
            postStatus = postStatus,
            postCompany = stored.company,
            contactEmail = if (status == ProposalStatus.ACCEPTED) counterpartEmail(stored, viewer) else null,
        )
    }

    /** 작성 기업에게는 제안 담당자, 제안 기업에게는 작성 담당자의 이메일입니다. */
    private fun StoredRecruitmentProposal.counterpartEmail(stored: StoredRecruitmentPost, viewer: Account): String? =
        when {
            stored.post.isOwnedBy(viewer.company.id) -> proposerEmail
            proposal.isSentBy(viewer.company.id) -> accountRepository.findById(stored.post.authorAccountId)?.email
            else -> null
        }
}
