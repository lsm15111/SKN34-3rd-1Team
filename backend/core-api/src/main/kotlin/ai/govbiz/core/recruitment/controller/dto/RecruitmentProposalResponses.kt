package ai.govbiz.core.recruitment.controller.dto

import ai.govbiz.core.account.controller.dto.CompanyResponse
import ai.govbiz.core.recruitment.domain.ProposalStatus
import ai.govbiz.core.recruitment.domain.RecruitmentPostStatus
import ai.govbiz.core.recruitment.service.dto.RecruitmentProposalResult

/**
 * 받은/보낸 제안 목록과 결정 응답이 공유하는 제안 한 건입니다.
 *
 * `company`는 제안 기업, `post`는 대상 모집글 요약이며 `contactEmail`은 수락된 제안에서만 상대 담당자 이메일입니다.
 */
data class RecruitmentProposalResponse(
    val id: Long,
    val postId: Long,
    val status: ProposalStatus,
    val message: String,
    val createdAt: String,
    val decidedAt: String?,
    val company: CompanyResponse,
    val post: ProposalPostSummaryResponse,
    val contactEmail: String?,
) {
    companion object {
        fun from(result: RecruitmentProposalResult): RecruitmentProposalResponse =
            RecruitmentProposalResponse(
                id = result.proposal.id,
                postId = result.proposal.postId,
                status = result.status,
                message = result.proposal.message,
                createdAt = formatSeoulDateTime(result.proposal.createdAt),
                decidedAt = result.proposal.decidedAt?.let(::formatSeoulDateTime),
                company = CompanyResponse.from(result.company),
                post = ProposalPostSummaryResponse(
                    id = result.post.id,
                    status = result.postStatus,
                    title = result.post.draft.title,
                    closesOn = result.post.draft.closesOn.toString(),
                    companyName = result.postCompany.companyName,
                ),
                contactEmail = result.contactEmail,
            )
    }
}

/** 보낸 제안 화면이 대상 글을 소개하는 데 필요한 요약입니다. 전체는 모집글 상세 API로 봅니다. */
data class ProposalPostSummaryResponse(
    val id: Long,
    val status: RecruitmentPostStatus,
    val title: String,
    val closesOn: String,
    val companyName: String,
)

data class RecruitmentProposalListResponse(
    val items: List<RecruitmentProposalResponse>,
) {
    companion object {
        fun from(results: List<RecruitmentProposalResult>): RecruitmentProposalListResponse =
            RecruitmentProposalListResponse(java.util.List.copyOf(results.map(RecruitmentProposalResponse::from)))
    }
}
