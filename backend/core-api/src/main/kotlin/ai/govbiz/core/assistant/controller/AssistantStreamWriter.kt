package ai.govbiz.core.assistant.controller

import ai.govbiz.core.assistant.controller.dto.AssistantMessageResponse
import ai.govbiz.core.assistant.domain.AssistantStreamEvent
import jakarta.servlet.http.HttpServletResponse
import java.nio.charset.StandardCharsets
import tools.jackson.databind.ObjectMapper

/**
 * 가이드 답을 SSE로 내보냅니다. 첫 이벤트를 쓸 때 머리글이 확정되므로, 그 전에 생긴 오류는 평소처럼 상태 코드로 나가고
 * 그 뒤에 생긴 오류만 `error` 이벤트로 알립니다. 어떤 경우에도 실패를 성공처럼 보이게 하지 않습니다.
 */
class AssistantStreamWriter(
    private val response: HttpServletResponse,
    private val mapper: ObjectMapper,
) {
    var started = false
        private set

    fun send(event: AssistantStreamEvent) {
        when (event) {
            is AssistantStreamEvent.Status -> write("status", mapOf("phase" to event.phase.name))
            is AssistantStreamEvent.Text -> write("text", mapOf("delta" to event.delta))
            is AssistantStreamEvent.Final -> write("final", AssistantMessageResponse.from(event.answer))
        }
    }

    /** 이미 내보내기 시작한 뒤의 실패입니다. 화면은 이 이벤트를 보고 실패 말풍선으로 바꿉니다. */
    fun sendFailure(code: String) = write("error", mapOf("code" to code))

    private fun write(name: String, data: Any) {
        if (!started) start()
        val body = mapper.writeValueAsString(data)
        // 데이터는 JSON 한 줄이라 줄바꿈으로 덩어리가 쪼개지지 않습니다.
        response.writer.write("event: $name\ndata: $body\n\n")
        response.writer.flush()
    }

    private fun start() {
        response.status = HttpServletResponse.SC_OK
        response.contentType = "text/event-stream"
        response.characterEncoding = StandardCharsets.UTF_8.name()
        response.setHeader("Cache-Control", "no-store")
        // 중간 프록시가 모아서 보내면 실시간이 아니게 되므로 버퍼링을 끕니다.
        response.setHeader("X-Accel-Buffering", "no")
        started = true
    }
}
