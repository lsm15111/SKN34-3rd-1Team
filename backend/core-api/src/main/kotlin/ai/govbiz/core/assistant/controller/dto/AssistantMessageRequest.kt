package ai.govbiz.core.assistant.controller.dto

import ai.govbiz.core.assistant.domain.AssistantHistoryMessage
import ai.govbiz.core.assistant.domain.AssistantHistoryRole
import ai.govbiz.core.assistant.domain.AssistantQuestion
import ai.govbiz.core.assistant.domain.AssistantScreenContext
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

private const val LAYOUT_TEXT = "(?Us)^(?!\\s*$)(?!.*[\\p{C}&&[^\\n\\r\\t]]).*$"
private const val ROUTE = "/[A-Za-z0-9._~!$&'()*+,;=:@%/-]*"

/**
 * GovBiz 가이드 자유 질문 요청입니다. 답의 근거인 도움말은 Core 카탈로그가 가지므로 요청에 싣지 않습니다.
 * 상한은 AI Service 계약과 같습니다.
 */
data class AssistantMessageRequest(
    @param:JsonProperty(required = true)
    @field:NotBlank
    @field:Size(max = 500)
    @field:Pattern(regexp = LAYOUT_TEXT)
    val message: String,
    @param:JsonProperty(required = true)
    @field:Size(max = 6)
    @field:Valid
    val history: List<AssistantHistoryMessageRequest>,
    @param:JsonProperty(required = true)
    @field:Valid
    val context: AssistantContextRequest,
) {
    fun toDomain() = AssistantQuestion(
        message,
        history.map { AssistantHistoryMessage(AssistantHistoryRole.valueOf(it.role), it.content) },
        AssistantScreenContext(context.route, context.programSelected),
    )
}

data class AssistantHistoryMessageRequest(
    @param:JsonProperty(required = true)
    @field:Pattern(regexp = "USER|ASSISTANT")
    val role: String,
    @param:JsonProperty(required = true)
    @field:NotBlank
    @field:Size(max = 1000)
    @field:Pattern(regexp = LAYOUT_TEXT)
    val content: String,
)

data class AssistantContextRequest(
    @param:JsonProperty(required = true)
    @field:NotBlank
    @field:Size(max = 200)
    @field:Pattern(regexp = ROUTE)
    val route: String,
    @param:JsonProperty(required = true)
    @field:JsonSetter(nulls = Nulls.FAIL)
    val programSelected: Boolean,
)
