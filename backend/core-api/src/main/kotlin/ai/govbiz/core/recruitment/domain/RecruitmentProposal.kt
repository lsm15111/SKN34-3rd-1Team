package ai.govbiz.core.recruitment.domain

import java.time.LocalDateTime

/** DB에 저장하는 제안 결정값입니다. 보낸 직후는 PENDING이며 작성 기업의 수락·거절, 제안 기업의 철회로 한 번만 바뀝니다. */
enum class ProposalDecision {
    PENDING,
    ACCEPTED,
    DECLINED,
    WITHDRAWN,
}

/** 저장하지 않고 읽을 때 계산하는 제안 표시 상태입니다. EXPIRED·CLOSED는 결정 없이 PENDING인 제안에만 붙습니다. */
enum class ProposalStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    WITHDRAWN,
    EXPIRED,
    CLOSED,
}

/** 한 기업이 모집글 하나에 보낸 참여 제안입니다. 표시 상태는 [ProposalStatusResolver]가 계산합니다. */
data class RecruitmentProposal(
    val id: Long,
    val postId: Long,
    val companyId: Long,
    val proposerAccountId: Long,
    val message: String,
    val decision: ProposalDecision,
    val decidedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
) {
    init {
        requireProposalMessage(message)
        require((decision == ProposalDecision.PENDING) == (decidedAt == null)) {
            "decidedAt must be present exactly when a decision was made"
        }
    }

    fun isSentBy(companyId: Long): Boolean = this.companyId == companyId

    companion object {
        const val MAX_MESSAGE_LENGTH = 500

        fun requireProposalMessage(message: String) {
            require(message.isNotBlank() && message.length <= MAX_MESSAGE_LENGTH) {
                "message must be 1..$MAX_MESSAGE_LENGTH characters"
            }
        }
    }
}
