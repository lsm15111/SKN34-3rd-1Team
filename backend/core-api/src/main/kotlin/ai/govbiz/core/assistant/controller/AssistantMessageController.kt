package ai.govbiz.core.assistant.controller

import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.assistant.controller.dto.AssistantMessageRequest
import ai.govbiz.core.assistant.controller.dto.AssistantMessageResponse
import ai.govbiz.core.assistant.config.AssistantAgentProperties
import ai.govbiz.core.assistant.service.AssistantMessageService
import ai.govbiz.core._common.exception.AiServiceCallException
import ai.govbiz.core.supportprogram.service.admission.SupportProgramRequestAdmissionService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper

/**
 * GovBiz 가이드 자유 질문 입구입니다. 비로그인도 물을 수 있고, 세션이 있으면 회원 자료 답에 씁니다.
 * 요청량·동시 실행 한도는 검색·원문 질문과 같은 Bean을 공유하므로 가이드가 한도를 따로 늘리지 않습니다.
 * 로그인 회원 질문은 도구 때문에 모델을 여러 번 부를 수 있어 주소당 분당 상한(`app.assistant.agent-per-client-per-minute`)을 더 겁니다.
 */
@RestController
@RequestMapping("/api/v1/assistant")
class AssistantMessageController(
    private val service: AssistantMessageService,
    private val admission: SupportProgramRequestAdmissionService,
    @param:Qualifier("assistantAgentAdmissionService") private val agentAdmission: SupportProgramRequestAdmissionService,
    private val properties: AssistantAgentProperties,
    private val mapper: ObjectMapper,
) {
    @PostMapping("/messages")
    fun answer(
        account: Account?,
        @RequestBody @Valid request: AssistantMessageRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<AssistantMessageResponse> = admission.execute(httpRequest.remoteAddr) {
        withMemberLimit(account, httpRequest.remoteAddr) {
            ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(AssistantMessageResponse.from(service.answer(account, request.toDomain())))
        }
    }

    /**
     * 같은 질문을 SSE로 받습니다. 화면은 진행 상황과 답변 조각을 먼저 보여 주고, 카드·이동 버튼·실행 제안은
     * 검증을 마친 `final`에만 옵니다. 요청 한도는 일반 답변과 같은 Bean을 그대로 거칩니다.
     *
     * 이벤트를 하나도 내보내기 전에 생긴 실패는 평소처럼 상태 코드로 나가고, 내보낸 뒤의 실패만 `error` 이벤트가 됩니다.
     */
    @PostMapping("/messages", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(
        account: Account?,
        @RequestBody @Valid request: AssistantMessageRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ) {
        val writer = AssistantStreamWriter(httpResponse, mapper)
        admission.execute(httpRequest.remoteAddr) {
            withMemberLimit(account, httpRequest.remoteAddr) {
                try {
                    service.answerStreaming(account, request.toDomain(), writer::send)
                } catch (exception: AiServiceCallException) {
                    if (!writer.started) throw exception
                    writer.sendFailure(exception.failure.name)
                }
            }
        }
    }

    private fun <T> withMemberLimit(account: Account?, clientAddress: String, action: () -> T): T =
        if (account != null && properties.toolsEnabled) agentAdmission.execute(clientAddress, action) else action()
}
