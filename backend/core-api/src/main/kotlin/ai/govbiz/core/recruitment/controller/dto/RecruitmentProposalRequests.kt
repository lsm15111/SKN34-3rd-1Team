package ai.govbiz.core.recruitment.controller.dto

import ai.govbiz.core.recruitment.domain.RecruitmentProposal
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** 제안 본문입니다. 연락처 문구는 Service가 다시 거부합니다. */
data class SendProposalRequest(
    @field:NotBlank
    @field:Size(max = RecruitmentProposal.MAX_MESSAGE_LENGTH)
    @field:Pattern(regexp = "(?s)^(?!.*[\\p{C}&&[^\\n\\r\\t]]).+$")
    val message: String,
)
