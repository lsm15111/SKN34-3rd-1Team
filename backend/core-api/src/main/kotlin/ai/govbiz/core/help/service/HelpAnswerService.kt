package ai.govbiz.core.help.service

import ai.govbiz.core._common.exception.AiServiceCallException
import ai.govbiz.core.help.client.ai.AiHelpAnswerClient
import ai.govbiz.core.help.client.ai.dto.AiHelpAnswerRequest
import ai.govbiz.core.help.client.ai.dto.AiHelpEntryRequest
import ai.govbiz.core.help.controller.dto.HelpAnswerRequest
import ai.govbiz.core.help.service.dto.HelpAnswerResult
import ai.govbiz.core.help.service.dto.HelpAnswerStatus
import org.springframework.stereotype.Service

/**
 * 도움말 항목만 근거로 쓰는 답변 흐름입니다. AI가 돌려준 인용이 요청한 항목 밖을 가리키거나
 * 기권이 답변 문장을 함께 담으면 실패로 처리합니다. 근거를 확인할 수 없는 답을 화면에 내보내지 않습니다.
 */
@Service
class HelpAnswerService(
    private val client: AiHelpAnswerClient,
) {
    fun answer(request: HelpAnswerRequest): HelpAnswerResult {
        val payload = client.answer(
            AiHelpAnswerRequest(
                question = request.question,
                entries = request.entries.map { entry ->
                    AiHelpEntryRequest(
                        id = entry.id,
                        title = entry.title,
                        summary = entry.summary,
                        body = entry.body,
                        limitation = entry.limitation,
                    )
                },
            ),
        )

        val status = payload.answerStatus?.let { name -> HelpAnswerStatus.entries.firstOrNull { it.name == name } }
            ?: throw AiServiceCallException.invalidResponse("AI help answer status was not recognised", null)
        val citations = payload.citationEntryIds
        val requestedIds = request.entries.map { it.id }.toSet()
        if (!requestedIds.containsAll(citations) || citations.size != citations.toSet().size) {
            throw AiServiceCallException.invalidResponse("AI help answer cited an unknown help entry", null)
        }
        if (status == HelpAnswerStatus.ANSWERED) {
            if (payload.answer.isBlank() || citations.isEmpty()) {
                throw AiServiceCallException.invalidResponse("AI help answer had no evidence", null)
            }
            return HelpAnswerResult(payload.answer, status, citations)
        }
        if (citations.isNotEmpty()) {
            throw AiServiceCallException.invalidResponse("Only answered help questions may cite entries", null)
        }
        // 기권 문구는 화면이 가지고 있습니다. AI가 쓴 문장을 그대로 내보내지 않습니다.
        return HelpAnswerResult("", status, emptyList())
    }
}
