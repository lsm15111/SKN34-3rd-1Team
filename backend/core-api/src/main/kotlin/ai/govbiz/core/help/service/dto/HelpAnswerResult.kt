package ai.govbiz.core.help.service.dto

enum class HelpAnswerStatus {
    ANSWERED,
    OUT_OF_SCOPE_PROGRAM,
    OUT_OF_SCOPE_GENERAL,
    NOT_IN_HELP,
}

data class HelpAnswerResult(
    val answer: String,
    val answerStatus: HelpAnswerStatus,
    val citationEntryIds: List<String>,
)
