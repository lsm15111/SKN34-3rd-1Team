package ai.govbiz.core.recruitment.controller.dto

import ai.govbiz.core.account.controller.dto.CompanyResponse
import ai.govbiz.core.recruitment.domain.ProposalStatus
import ai.govbiz.core.recruitment.domain.RecruitmentPostStatus
import ai.govbiz.core.recruitment.domain.RecruitmentRole
import ai.govbiz.core.recruitment.service.dto.RecruitmentPostPageResult
import ai.govbiz.core.recruitment.service.dto.RecruitmentPostResult
import ai.govbiz.core.supportprogram.domain.SupportProgram
import ai.govbiz.core.supportprogram.domain.SupportProgramStatus
import java.time.LocalDateTime
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
                closedEarlyAt = post.closedEarlyAt?.let(::formatSeoulDateTime),
                createdAt = formatSeoulDateTime(post.createdAt),
                updatedAt = formatSeoulDateTime(post.updatedAt),
                company = CompanyResponse.from(result.company),
                program = result.program?.let(LinkedProgramResponse::from),
                proposalCount = result.proposalCount,
                viewer = RecruitmentPostViewerResponse(
                    isOwner = result.isOwner,
                    myProposalStatus = result.myProposalStatus,
                ),
            )
        }
    }
}

private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

/** 모집글·제안 응답이 공유하는 서울 기준 ISO 8601 시각 표기입니다. */
internal fun formatSeoulDateTime(value: LocalDateTime): String =
    value.atZone(SEOUL).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

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

/** [myProposalStatus]는 조회 기업이 이 글에 보낸 제안의 상태이며 비로그인·미제안이면 null입니다. */
data class RecruitmentPostViewerResponse(
    val isOwner: Boolean,
    val myProposalStatus: ProposalStatus? = null,
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
