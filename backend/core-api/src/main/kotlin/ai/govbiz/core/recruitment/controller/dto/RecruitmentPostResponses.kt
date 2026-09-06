package ai.govbiz.core.recruitment.controller.dto

import ai.govbiz.core.account.controller.dto.CompanyResponse
import ai.govbiz.core.recruitment.domain.RecruitmentPostStatus
import ai.govbiz.core.recruitment.domain.RecruitmentRole
import ai.govbiz.core.recruitment.service.dto.RecruitmentPostPageResult
import ai.govbiz.core.recruitment.service.dto.RecruitmentPostResult
import ai.govbiz.core.supportprogram.domain.SupportProgram
import ai.govbiz.core.supportprogram.domain.SupportProgramStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class RecruitmentPostResponse(
    val id: Long,
    val status: RecruitmentPostStatus,
    val title: String,
    val body: String,
    val ourRole: RecruitmentRole,
    val wantedRole: RecruitmentRole,
    val wantedCompanyCount: Int,
    val wantedRegion: String,
    val requiredCapabilities: List<String>,
    val closesOn: String,
    val closedEarlyAt: String?,
    val createdAt: String,
    val updatedAt: String,
    val company: CompanyResponse,
    val program: LinkedProgramResponse?,
    val proposalCount: Int,
    val viewer: RecruitmentPostViewerResponse,
) {
    companion object {
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

        fun from(result: RecruitmentPostResult): RecruitmentPostResponse {
            val post = result.post
            val draft = post.draft
            return RecruitmentPostResponse(
                id = post.id,
                status = result.status,
                title = draft.title,
                body = draft.body,
                ourRole = draft.ourRole,
                wantedRole = draft.wantedRole,
                wantedCompanyCount = draft.wantedCompanyCount,
                wantedRegion = draft.wantedRegion,
                requiredCapabilities = draft.requiredCapabilities,
                closesOn = draft.closesOn.toString(),
                closedEarlyAt = post.closedEarlyAt?.let(::formatSeoul),
                createdAt = formatSeoul(post.createdAt),
                updatedAt = formatSeoul(post.updatedAt),
                company = CompanyResponse.from(result.company),
                program = result.program?.let(LinkedProgramResponse::from),
                proposalCount = 0,
                viewer = RecruitmentPostViewerResponse(isOwner = result.isOwner),
            )
        }

        private fun formatSeoul(value: java.time.LocalDateTime): String =
            value.atZone(SEOUL).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}

/** 모집글 화면이 연결 공고를 소개하는 데 필요한 요약만 담습니다. 전체 상세는 공고 상세 API로 봅니다. */
data class LinkedProgramResponse(
    val sourceCode: String,
    val sourceProgramId: String,
    val title: String,
    val organization: String,
    val status: SupportProgramStatus,
    val applicationPeriod: String,
    val applicationEndDate: String?,
    val targetDescription: String,
    val sourceUrl: String,
) {
    companion object {
        fun from(program: SupportProgram): LinkedProgramResponse =
            LinkedProgramResponse(
                sourceCode = program.sourceCode,
                sourceProgramId = program.id,
                title = program.title,
                organization = program.organization,
                status = program.status,
                applicationPeriod = program.applicationPeriod,
                applicationEndDate = program.applicationEndDate?.toString(),
                targetDescription = program.targetDescription,
                sourceUrl = program.sourceUrl,
            )
    }
}

data class RecruitmentPostViewerResponse(
    val isOwner: Boolean,
)

data class RecruitmentPostPageResponse(
    val items: List<RecruitmentPostResponse>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
) {
    companion object {
        fun from(result: RecruitmentPostPageResult): RecruitmentPostPageResponse =
            RecruitmentPostPageResponse(
                items = java.util.List.copyOf(result.posts.map(RecruitmentPostResponse::from)),
                page = result.page,
                size = result.size,
                totalCount = result.totalCount,
            )
    }
}

data class RecruitmentPostListResponse(
    val items: List<RecruitmentPostResponse>,
) {
    companion object {
        fun from(results: List<RecruitmentPostResult>): RecruitmentPostListResponse =
            RecruitmentPostListResponse(java.util.List.copyOf(results.map(RecruitmentPostResponse::from)))
    }
}
