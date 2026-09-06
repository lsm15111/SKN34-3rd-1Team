package ai.govbiz.core.recruitment.controller.dto

import ai.govbiz.core.recruitment.domain.RecruitmentPostDraft
import ai.govbiz.core.recruitment.domain.RecruitmentRole
import ai.govbiz.core.supportprogram.controller.validation.CodePointMax
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDate

/** 등록·수정이 공유하는 모집 조건입니다. Domain의 [RecruitmentPostDraft]가 같은 규칙을 다시 검사합니다. */
data class RecruitmentPostFieldsRequest(
    @field:NotBlank
    @field:Size(max = RecruitmentPostDraft.MAX_TITLE_LENGTH)
    @field:Pattern(regexp = "(?s)^(?!\\s)(?!.*\\s$)(?!.*[\\p{C}]).+$")
    val title: String,
    @field:NotBlank
    @field:Size(max = RecruitmentPostDraft.MAX_BODY_LENGTH)
    @field:Pattern(regexp = "(?s)^(?!.*[\\p{C}&&[^\\n\\r\\t]]).+$")
    val body: String,
    @field:NotNull
    val ourRole: RecruitmentRole,
    @field:NotNull
    val wantedRole: RecruitmentRole,
    @field:Min(1)
    @field:Max(RecruitmentPostDraft.MAX_WANTED_COMPANY_COUNT.toLong())
    val wantedCompanyCount: Int,
    @field:Size(max = RecruitmentPostDraft.MAX_REGION_LENGTH)
    @field:Pattern(regexp = "(?s)^(?!\\s)(?!.*\\s$)(?!.*[\\p{C}]).*$")
    val wantedRegion: String = "",
    @field:Size(max = RecruitmentPostDraft.MAX_CAPABILITIES)
    val requiredCapabilities: List<
        @NotBlank
        @Size(max = RecruitmentPostDraft.MAX_CAPABILITY_LENGTH)
        @Pattern(regexp = "^(?!\\s)(?!.*\\s$)(?!.*[\\p{C}]).+$")
        String,
    > = emptyList(),
    @field:NotNull
    val closesOn: LocalDate,
) {
    /** 작성 기업은 주관·참여만 맡을 수 있습니다. 수요처는 찾는 역할로만 씁니다. */
    @get:AssertTrue
    val ourRoleIsLeadOrParticipant: Boolean
        get() = ourRole != RecruitmentRole.DEMAND

    fun toDraft(): RecruitmentPostDraft =
        RecruitmentPostDraft(
            title = title,
            body = body,
            ourRole = ourRole,
            wantedRole = wantedRole,
            wantedCompanyCount = wantedCompanyCount,
            wantedRegion = wantedRegion,
            requiredCapabilities = requiredCapabilities,
            closesOn = closesOn,
        )
}

data class CreateRecruitmentPostRequest(
    @field:NotBlank
    @field:Size(max = 64)
    @field:Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    val sourceCode: String,
    @field:NotBlank
    @field:CodePointMax(max = 255)
    @field:Pattern(regexp = "(?Us)^(?!\\s)(?!.*\\s$)(?!.*\\p{C}).+$")
    val sourceProgramId: String,
    @field:NotNull
    @field:Valid
    val post: RecruitmentPostFieldsRequest,
)

data class UpdateRecruitmentPostRequest(
    @field:NotNull
    @field:Valid
    val post: RecruitmentPostFieldsRequest,
)
