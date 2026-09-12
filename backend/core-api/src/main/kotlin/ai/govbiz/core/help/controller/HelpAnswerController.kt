package ai.govbiz.core.help.controller

import ai.govbiz.core.help.controller.dto.HelpAnswerRequest
import ai.govbiz.core.help.controller.dto.HelpAnswerResponse
import ai.govbiz.core.help.service.HelpAnswerService
import ai.govbiz.core.supportprogram.service.admission.SupportProgramRequestAdmissionService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 로그인 전 화면에서도 열리므로 다른 공개 AI 요청과 같은 요청량·동시 실행 한도를 함께 씁니다. */
@RestController
@RequestMapping("/api/v1/help")
class HelpAnswerController(
    private val service: HelpAnswerService,
    private val admission: SupportProgramRequestAdmissionService,
) {
    @PostMapping("/answers")
    fun answer(
        @RequestBody @Valid request: HelpAnswerRequest,
        httpRequest: HttpServletRequest,
    ): HelpAnswerResponse = admission.execute(httpRequest.remoteAddr) {
        HelpAnswerResponse.from(service.answer(request))
    }
}
