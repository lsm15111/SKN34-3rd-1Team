package ai.govbiz.core.assistant.controller.dto

import ai.govbiz.core.assistant.domain.AssistantApplicationPreparationSummary
import ai.govbiz.core.assistant.domain.AssistantCombinationReviewSummary
import ai.govbiz.core.assistant.domain.AssistantCompanyProfile
import ai.govbiz.core.assistant.domain.AssistantDailyReportStatus
import ai.govbiz.core.assistant.domain.AssistantProgramSearchResult
import ai.govbiz.core.assistant.domain.AssistantProposalSummary
import ai.govbiz.core.assistant.domain.AssistantRecruitmentSummary
import ai.govbiz.core.assistant.domain.AssistantSavedProgramSummary

/** 도구 응답은 AI Service 도구 스키마(`app/assistant/tools.py`)와 필드 이름이 같아야 합니다. */
data class AssistantCompanyProfileResponse(
    val registered: Boolean,
    val companyName: String?,
    val region: String?,
    val industry: String?,
    val foundedYear: Int?,
    val roles: List<String>,
    val interestAreas: List<String>,
    val introduction: String?,
    val capabilities: List<String>,
) {
    companion object {
        fun from(profile: AssistantCompanyProfile) = AssistantCompanyProfileResponse(
            profile.registered, profile.companyName, profile.region, profile.industry, profile.foundedYear,
            profile.roles, profile.interestAreas, profile.introduction, profile.capabilities,
        )
    }
}

data class AssistantRecruitmentResponse(
    val id: Long,
    val title: String,
    val companyName: String,
    val companyRegion: String,
    val companyIndustry: String,
    val ownRole: String,
    val seekingRole: String,
    val seekingCount: Int,
    val region: String,
    val minimumCompanyAgeYears: Int?,
    val capabilities: List<String>,
    val recruitmentDeadline: String,
    val programTitle: String,
    val programApplicationEndDate: String?,
    val body: String,
) {
    companion object {
        fun from(summary: AssistantRecruitmentSummary) = AssistantRecruitmentResponse(
            summary.id, summary.title, summary.companyName, summary.companyRegion, summary.companyIndustry,
            summary.ownRole, summary.seekingRole, summary.seekingCount, summary.region, summary.minimumCompanyAgeYears,
            summary.capabilities, summary.recruitmentDeadline.toString(), summary.programTitle,
            summary.programApplicationEndDate?.toString(), summary.body,
        )
    }
}

data class AssistantSavedProgramResponse(
    val sourceCode: String,
    val sourceProgramId: String,
    val title: String,
    val organization: String,
    val applicationEndDate: String?,
    val status: String,
    val documentId: String?,
) {
    companion object {
        fun from(summary: AssistantSavedProgramSummary) = AssistantSavedProgramResponse(
            summary.sourceCode, summary.sourceProgramId, summary.title, summary.organization,
            summary.applicationEndDate?.toString(), summary.status, summary.documentId,
        )
    }
}

/** 신청 준비 건 한 건입니다. `progressRevision`은 진행 단계 변경 카드가 그대로 되돌려 보내는 값입니다. */
data class AssistantApplicationPreparationResponse(
    val id: Long,
    val sourceCode: String,
    val sourceProgramId: String,
    val programTitle: String?,
    val progressStage: String,
    val progressRevision: Long,
    val updatedAt: String,
) {
    companion object {
        fun from(summary: AssistantApplicationPreparationSummary) = AssistantApplicationPreparationResponse(
            summary.id, summary.sourceCode, summary.sourceProgramId, summary.programTitle,
            summary.progressStage, summary.progressRevision, summary.updatedAt.toString(),
        )
    }
}

data class AssistantCombinationReviewResponse(
    val id: Long,
    val title: String,
    val inputRevision: Long,
    val programTitles: List<String>,
    val latestRunStatus: String?,
    val latestRunId: Long?,
    val latestRunDate: String?,
    val updatedAt: String,
) {
    companion object {
        fun from(summary: AssistantCombinationReviewSummary) = AssistantCombinationReviewResponse(
            summary.id, summary.title, summary.inputRevision, summary.programTitles,
            summary.latestRunStatus, summary.latestRunId, summary.latestRunDate?.toString(), summary.updatedAt.toString(),
        )
    }
}

data class AssistantDailyReportStatusResponse(
    val enabled: Boolean,
    val emailConfirmed: Boolean,
    val supportPurpose: String,
    val serviceEnabled: Boolean,
    val sendHour: Int,
    val latestReportDate: String?,
) {
    companion object {
        fun from(status: AssistantDailyReportStatus) = AssistantDailyReportStatusResponse(
            status.enabled, status.emailConfirmed, status.supportPurpose,
            status.serviceEnabled, status.sendHour, status.latestReportDate?.toString(),
        )
    }
}

data class AssistantProposalSummaryResponse(
    val hasCompany: Boolean,
    val receivedPending: Int,
    val sentPending: Int,
    val earliestExpiryDate: String?,
) {
    companion object {
        fun from(summary: AssistantProposalSummary) = AssistantProposalSummaryResponse(
            summary.hasCompany, summary.receivedPending, summary.sentPending, summary.earliestExpiryDate?.toString(),
        )
    }
}

data class AssistantProgramSearchResponse(
    val sourceCode: String,
    val sourceProgramId: String,
    val title: String,
    val organization: String,
    val applicationEndDate: String?,
    val status: String,
    val regions: List<String>,
    val saved: Boolean,
) {
    companion object {
        fun from(result: AssistantProgramSearchResult) = AssistantProgramSearchResponse(
            result.sourceCode, result.sourceProgramId, result.title, result.organization,
            result.applicationEndDate?.toString(), result.status, result.regions, result.saved,
        )
    }
}
