package ai.govbiz.core.assistant.client.dto

/**
 * AI Service `POST /internal/v1/assistant/answers` 요청입니다. 필드 이름과 상한은 AI Service `app/assistant/models.py`와 같고,
 * 두 서비스의 테스트가 `src/test/resources/assistant/contract-*.json`을 함께 읽어 맞춥니다.
 * 로그인 회원이면 도구가 Core를 되부를 때 쓰는 계정 묶음 토큰(`principal`)을 싣고, 비로그인은 null입니다.
 */
data class AiAssistantAnswerRequest(
    val schemaVersion: String,
    val message: String,
    val history: List<AiAssistantHistoryMessage>,
    val session: AiAssistantSession,
    val context: AiAssistantContext,
    val helpEntries: List<AiAssistantHelpEntry>,
    val principal: AiAssistantPrincipal?,
)

data class AiAssistantHistoryMessage(
    val role: String,
    val content: String,
)

data class AiAssistantSession(
    val authenticated: Boolean,
    val hasCompany: Boolean,
)

data class AiAssistantContext(
    val route: String,
    val programSelected: Boolean,
)

data class AiAssistantHelpEntry(
    val id: String,
    val title: String,
    val question: String,
    val summary: String,
    val body: List<String>,
    val limitation: String?,
    val audience: String,
    val status: String,
    val action: AiAssistantHelpAction?,
)

data class AiAssistantHelpAction(
    val label: String,
    val to: String,
)

data class AiAssistantPrincipal(
    val accountId: Long,
    val toolToken: String,
    val hasCompany: Boolean,
)

/** AI Service 응답입니다. 의도별 필드 조합과 카드·이동 경로는 Core Service가 다시 검증합니다. */
data class AiAssistantAnswerPayload(
    val schemaVersion: String?,
    val intent: String?,
    val answer: String?,
    val citations: List<String?>?,
    val clarificationQuestion: String?,
    val searchQuery: String?,
    val accountTopic: String?,
    val cards: List<AiAssistantCardPayload?>?,
    val navigation: AiAssistantNavigationPayload?,
    val toolCalls: List<AiAssistantToolCallPayload?>?,
)

data class AiAssistantCardPayload(
    val kind: String?,
    val id: String?,
    val title: String?,
    val subtitle: String?,
    val reason: String?,
    val to: String?,
)

data class AiAssistantNavigationPayload(
    val label: String?,
    val to: String?,
)

data class AiAssistantToolCallPayload(
    val name: String?,
    val ms: Int?,
)
