package ai.govbiz.core.recruitment.domain

import java.time.LocalDateTime

/**
 * 제안 표시 상태를 읽을 때 계산합니다.
 *
 * 결정이 난 제안은 저장값 그대로이고, 결정 없이 PENDING인 제안은 보낸 지 [DEFAULT_EXPIRY_DAYS]일이 지나면 EXPIRED,
 * 모집글이 더 이상 모집 중이 아니면 CLOSED로 봅니다.
 */
object ProposalStatusResolver {

    const val DEFAULT_EXPIRY_DAYS = 7L

    fun resolve(
        proposal: RecruitmentProposal,
        postStatus: RecruitmentPostStatus,
        now: LocalDateTime,
        expiryDays: Long = DEFAULT_EXPIRY_DAYS,
    ): ProposalStatus =
        when {
            proposal.decision != ProposalDecision.PENDING -> ProposalStatus.valueOf(proposal.decision.name)
            now.isAfter(proposal.createdAt.plusDays(expiryDays)) -> ProposalStatus.EXPIRED
            postStatus != RecruitmentPostStatus.OPEN -> ProposalStatus.CLOSED
            else -> ProposalStatus.PENDING
        }
}
