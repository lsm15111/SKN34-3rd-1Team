package ai.govbiz.core.assistant.client

import ai.govbiz.core._common.exception.AiServiceCallException
import ai.govbiz.core._common.helper.executeAiServiceCall
import ai.govbiz.core.assistant.client.dto.AiAssistantAnswerPayload
import ai.govbiz.core.assistant.client.dto.AiAssistantAnswerRequest
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * GovBiz 가이드 자유 질문을 AI Service에 한 번 요청합니다. AI Service의 에이전트가 의도를 고르고, 로그인 회원이면
 * Core 내부 읽기 도구를 되불러 답과 카드를 만듭니다. 검색 실행·저장은 하지 않습니다.
 */
@Component
class AiAssistantClient(
    @param:Qualifier("aiServiceRestClient") private val restClient: RestClient,
) {
    fun answer(request: AiAssistantAnswerRequest): AiAssistantAnswerPayload =
        executeAiServiceCall { post(ANSWERS_PATH, request).toEntity(AiAssistantAnswerPayload::class.java).body ?: empty() }

    private fun post(path: String, request: Any): RestClient.ResponseSpec =
        restClient.post()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .onStatus(
                { it.value() != HttpStatus.OK.value() },
                { _, response ->
                    when (val status = response.statusCode.value()) {
                        HttpStatus.NO_CONTENT.value() -> empty()
                        HttpStatus.SERVICE_UNAVAILABLE.value() -> throw AiServiceCallException.unavailable(null)
                        HttpStatus.REQUEST_TIMEOUT.value(), HttpStatus.GATEWAY_TIMEOUT.value() ->
                            throw AiServiceCallException.timeout(null)
                        else -> throw AiServiceCallException.upstreamError("AI assistant returned HTTP $status", null)
                    }
                },
            )

    private fun empty(): Nothing = throw AiServiceCallException.invalidResponse("AI assistant response was empty", null)

    companion object {
        const val ANSWERS_PATH = "/internal/v1/assistant/answers"
    }
}
