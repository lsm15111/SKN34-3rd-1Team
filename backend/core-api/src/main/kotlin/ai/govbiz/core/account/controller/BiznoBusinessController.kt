package ai.govbiz.core.account.controller

import ai.govbiz.core.account.controller.dto.BiznoBusinessLookupResponse
import ai.govbiz.core.account.service.BiznoBusinessService
import jakarta.validation.constraints.Pattern
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth/businesses")
class BiznoBusinessController(private val service: BiznoBusinessService) {

    /** 회원가입 전에 사업자등록번호(하이픈 선택)로 국세청 등록 기업을 확인합니다. */
    @GetMapping("/lookup")
    fun lookup(
        @RequestParam
        @Pattern(regexp = "[0-9]{3}-?[0-9]{2}-?[0-9]{5}")
        businessNumber: String,
    ): BiznoBusinessLookupResponse =
        BiznoBusinessLookupResponse.from(service.findByBusinessNumber(businessNumber))
}
