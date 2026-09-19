package ai.govbiz.core.assistant.client

import ai.govbiz.core._common.exception.AiServiceCallException
import ai.govbiz.core._common.helper.executeAiServiceCall
import ai.govbiz.core.assistant.client.dto.AiAssistantAnswerPayload
import ai.govbiz.core.assistant.client.dto.AiAssistantAnswerRequest
import ai.govbiz.core.assistant.client.dto.AiAssistantStreamEvent
import java.io.BufferedReader
import java.nio.charset.StandardCharsets
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper

/**
 * GovBiz 가이드 자유 질문을 AI Service에 한 번 요청합니다. AI Service의 에이전트가 의도를 고르고, 로그인 회원이면
 * Core 내부 읽기 도구를 되불러 답과 카드를 만듭니다. 검색 실행·저장은 하지 않습니다.
 */
@Component
class AiAssistantClient(
    @param:Qualifier("aiServiceRestClient") private val restClient: RestClient,
    private val mapper: ObjectMapper,
) {
    fun answer(request: AiAssistantAnswerRequest): AiAssistantAnswerPayload =
        executeAiServiceCall { post(ANSWERS_PATH, request).toEntity(AiAssistantAnswerPayload::class.java).body ?: empty() }

    /**
     * 같은 질문을 SSE로 받습니다. 이벤트가 올 때마다 [onEvent]를 부르고, 연결이 끝나면 돌아옵니다.
     * 본문을 흘려 읽어야 하므로 응답이 열려 있는 동안 처리하고, 이름·데이터가 짝을 이루지 않는 덩어리는 버립니다.
     */
    fun stream(request: AiAssistantAnswerRequest, onEvent: (AiAssistantStreamEvent) -> Unit) = executeAiServiceCall {
        restClient.post()
            .uri(ANSWERS_STREAM_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .body(request)
            .exchange { _, response ->
                when (val status = response.statusCode.value()) {
                    HttpStatus.OK.value() -> Unit
                    HttpStatus.SERVICE_UNAVAILABLE.value() -> throw AiServiceCallException.unavailable(null)
                    HttpStatus.REQUEST_TIMEOUT.value(), HttpStatus.GATEWAY_TIMEOUT.value() ->
                        throw AiServiceCallException.timeout(null)
                    else -> throw AiServiceCallException.upstreamError("AI assistant stream returned HTTP ${'$'}status", null)
                }
                response.body.bufferedReader(StandardCharsets.UTF_8).use { reader -> readEvents(reader) { name, data -> onEvent(parse(name, data)) } }
            }
    }

    /** 모르는 이벤트 이름이나 깨진 JSON은 답을 지어내지 않고 계약 위반으로 끝냅니다. */
    private fun parse(name: String, data: String): AiAssistantStreamEvent = try {
        when (name) {
            "status" -> mapper.readValue(data, AiAssistantStreamEvent.Status::class.java)
            "text" -> mapper.readValue(data, AiAssistantStreamEvent.Text::class.java)
            "final" -> AiAssistantStreamEvent.Final(mapper.readValue(data, AiAssistantAnswerPayload::class.java))
            "error" -> mapper.readValue(data, AiAssistantStreamEvent.Failure::class.java)
            else -> throw AiServiceCallException.invalidResponse("AI assistant stream sent an unknown event", null)
        }
    } catch (exception: JacksonException) {
        throw AiServiceCallException.invalidResponse("AI assistant stream sent malformed data", exception)
    }

    private fun readEvents(reader: BufferedReader, onEvent: (String, String) -> Unit) {
        var name: String? = null
        val data = StringBuilder()
        while (true) {
            val line = reader.readLine() ?: break
            when {
                line.isEmpty() -> {
                    if (name != null && data.isNotEmpty()) onEvent(name!!, data.toString())
                    name = null
                    data.setLength(0)
                }
                line.startsWith(EVENT_PREFIX) -> name = line.removePrefix(EVENT_PREFIX).trim()
                line.startsWith(DATA_PREFIX) -> data.append(line.removePrefix(DATA_PREFIX))
                // 주석(`:`)과 모르는 필드는 SSE 규격대로 무시합니다.
            }
        }
        if (name != null && data.isNotEmpty()) onEvent(name!!, data.toString())
    }

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
        const val ANSWERS_STREAM_PATH = "/internal/v1/assistant/answers/stream"
        private const val EVENT_PREFIX = "event:"
        private const val DATA_PREFIX = "data:"
    }
}
