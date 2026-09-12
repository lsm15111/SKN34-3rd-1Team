package ai.govbiz.core.help.controller.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * 화면이 가진 도움말 항목을 질문과 함께 보냅니다. 항목은 네 표면이 함께 쓰는 한 벌이며 Core는 사본을 두지 않습니다.
 * 화면이 보낸 항목만 근거가 되므로 답변에 인용된 항목이 이 목록 안에 있는지 Core가 다시 확인합니다.
 */
data class HelpAnswerRequest(
    @field:NotBlank
    @field:Size(max = 500)
    @field:Pattern(regexp = "(?s)^(?!.*[\\p{C}&&[^\\n\\r\\t]]).+$")
    val question: String,
    @field:NotEmpty
    @field:Size(max = 50)
    @field:Valid
    val entries: List<HelpEntryRequest>,
)

data class HelpEntryRequest(
    @field:NotBlank
    @field:Size(max = 64)
    @field:Pattern(regexp = "[a-z][a-z0-9]*(-[a-z0-9]+)*")
    val id: String,
    @field:NotBlank
    @field:Size(max = 120)
    val title: String,
    @field:NotBlank
    @field:Size(max = 400)
    val summary: String,
    @field:NotEmpty
    @field:Size(max = 8)
    val body: List<@NotBlank @Size(max = 1000) String>,
    @field:NotBlank
    @field:Size(max = 400)
    val limitation: String,
)
