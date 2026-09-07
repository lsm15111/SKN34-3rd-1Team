package ai.govbiz.core.account.admin.dto

import ai.govbiz.core.account.controller.dto.CompanyResponse
import ai.govbiz.core.recruitment.controller.dto.LinkedProgramResponse
import ai.govbiz.core.recruitment.controller.dto.formatSeoulDateTime
import ai.govbiz.core.recruitment.domain.RecruitmentPostStatus
import ai.govbiz.core.recruitment.domain.RecruitmentRole
import ai.govbiz.core.recruitment.repository.RecruitmentPostRepository
import ai.govbiz.core.recruitment.service.dto.RecruitmentPostPageResult
import ai.govbiz.core.recruitment.service.dto.RecruitmentPostResult
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** 숨김·강제 마감 사유입니다. 숨김 사유는 모집글에 남고 마감 사유는 로그에만 남습니다. */
data class RecruitmentPostActionRequest(
    @field:NotBlank
    @field:Size(max = RecruitmentPostRepository.MAX_HIDDEN_REASON_LENGTH)
    @field:Pattern(regexp = "(?s)^(?!\\s)(?!.*\\s$)(?!.*[\\p{C}]).+$")
    val reason: String,
)

/** 운영자 목록·조치 응답입니다. 공개 모집글 응답과 달리 숨김 시각·사유를 포함하고 조회자 관계는 없습니다. */
data class AdminRecruitmentPostResponse(
    val id: Long,
    val status: RecruitmentPostStatus,
    val title: String,
    val ourRole: RecruitmentRole,
    val wantedRole: RecruitmentRole,
    val closesOn: String,
    val closedEarlyAt: String?,
    val hiddenAt: String?,
    val hiddenReason: String?,
    val createdAt: String,
    val company: CompanyResponse,
    val program: LinkedProgramResponse?,
    val proposalCount: Int,
) {
    companion object {
        fun from(result: RecruitmentPostResult): AdminRecruitmentPostResponse {
            val post = result.post
            return AdminRecruitmentPostResponse(
                id = post.id,
                status = result.status,
                title = post.draft.title,
                ourRole = post.draft.ourRole,
                wantedRole = post.draft.wantedRole,
                closesOn = post.draft.closesOn.toString(),
                closedEarlyAt = post.closedEarlyAt?.let(::formatSeoulDateTime),
                hiddenAt = post.hiddenAt?.let(::formatSeoulDateTime),
                hiddenReason = post.hiddenReason,
                createdAt = formatSeoulDateTime(post.createdAt),
                company = CompanyResponse.from(result.company),
                program = result.program?.let(LinkedProgramResponse::from),
                proposalCount = result.proposalCount,
            )
        }
    }
}

data class AdminRecruitmentPostPageResponse(
    val items: List<AdminRecruitmentPostResponse>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
) {
    companion object {
        fun from(result: RecruitmentPostPageResult): AdminRecruitmentPostPageResponse =
            AdminRecruitmentPostPageResponse(
                items = java.util.List.copyOf(result.posts.map(AdminRecruitmentPostResponse::from)),
                page = result.page,
                size = result.size,
                totalCount = result.totalCount,
            )
    }
}
