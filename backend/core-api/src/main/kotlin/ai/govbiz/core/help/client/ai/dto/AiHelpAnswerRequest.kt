package ai.govbiz.core.help.client.ai.dto

data class AiHelpEntryRequest(
    val id: String,
    val title: String,
    val summary: String,
    val body: List<String>,
    val limitation: String,
    val status: String,
)

data class AiHelpAnswerRequest(
    val question: String,
    val entries: List<AiHelpEntryRequest>,
)
