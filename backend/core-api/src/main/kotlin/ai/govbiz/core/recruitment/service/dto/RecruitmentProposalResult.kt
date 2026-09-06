package ai.govbiz.core.recruitment.service.dto

import ai.govbiz.core.account.domain.Company
import ai.govbiz.core.recruitment.domain.ProposalStatus
import ai.govbiz.core.recruitment.domain.RecruitmentPost
import ai.govbiz.core.recruitment.domain.RecruitmentPostStatus
import ai.govbiz.core.recruitment.domain.RecruitmentProposal

/**
 * 상태·제안 기업·대상 모집글을 채운 제안입니다.
 *
 * [contactEmail]은 제안이 `ACCEPTED`일 때만 조회자의 상대 담당자 이메일이며(작성 기업에게는 제안 담당자, 제안 기업에게는
 * 작성 담당자), 그 외에는 null입니다.
 */
data class RecruitmentProposalResult(
    val proposal: RecruitmentProposal,
    val status: ProposalStatus,
    val company: Company,
    val post: RecruitmentPost,
    val postStatus: RecruitmentPostStatus,
    val postCompany: Company,
    val contactEmail: String?,
)
