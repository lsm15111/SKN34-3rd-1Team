package ai.govbiz.core.help.controller.dto

import ai.govbiz.core.help.service.dto.HelpAnswerResult
import ai.govbiz.core.help.service.dto.HelpAnswerStatus

/** 기권일 때 answer는 빈 문자열이며 화면이 상태별 문구를 씁니다. */
data class HelpAnswerResponse(
    val answer: String,
    val answerStatus: HelpAnswerStatus,
    val citationEntryIds: List<String>,
) {
    companion object {
        fun from(result: HelpAnswerResult): HelpAnswerResponse =
            HelpAnswerResponse(
                answer = result.answer,
                answerStatus = result.answerStatus,
                citationEntryIds = java.util.List.copyOf(result.citationEntryIds),
            )
    }
}
