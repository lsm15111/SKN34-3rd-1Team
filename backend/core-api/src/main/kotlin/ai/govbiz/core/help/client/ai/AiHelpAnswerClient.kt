package ai.govbiz.core.help.client.ai

import ai.govbiz.core._common.exception.AiServiceCallException
import ai.govbiz.core._common.helper.executeAiServiceCall
import ai.govbiz.core.help.client.ai.dto.AiHelpAnswerPayload
import ai.govbiz.core.help.client.ai.dto.AiHelpAnswerRequest
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/** AI Service의 도움말 답변 HTTP 계약을 소유합니다. */
@Component
class AiHelpAnswerClient(
    @param:Qualifier("aiServiceRestClient") private val restClient: RestClient,
) {
    fun answer(request: AiHelpAnswerRequest): AiHelpAnswerPayload =
        executeAiServiceCall {
            restClient.post()
                .uri("/internal/v1/help/answers")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(
                    { it.value() != HttpStatus.OK.value() },
                    { _, response ->
                        when (val status = response.statusCode.value()) {
                            HttpStatus.NO_CONTENT.value() ->
                                throw AiServiceCallException.invalidResponse("AI help answer response was empty", null)
                            HttpStatus.SERVICE_UNAVAILABLE.value() ->
                                throw AiServiceCallException.unavailable(null)
                            HttpStatus.REQUEST_TIMEOUT.value(), HttpStatus.GATEWAY_TIMEOUT.value() ->
                                throw AiServiceCallException.timeout(null)
                            else ->
                                throw AiServiceCallException.upstreamError("AI help answer returned HTTP $status", null)
                        }
                    },
                )
                .toEntity(AiHelpAnswerPayload::class.java)
                .body
                ?: throw AiServiceCallException.invalidResponse("AI help answer response was empty", null)
        }
}
