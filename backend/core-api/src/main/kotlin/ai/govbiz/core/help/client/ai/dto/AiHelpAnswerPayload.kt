package ai.govbiz.core.help.client.ai.dto

data class AiHelpAnswerPayload(
    val answer: String = "",
    val answerStatus: String? = null,
    val citationEntryIds: List<String> = emptyList(),
)
