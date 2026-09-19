package ai.govbiz.core.assistant.controller

import ai.govbiz.core.assistant.controller.dto.AssistantApplicationPreparationResponse
import ai.govbiz.core.assistant.controller.dto.AssistantCombinationReviewResponse
import ai.govbiz.core.assistant.controller.dto.AssistantCompanyProfileResponse
import ai.govbiz.core.assistant.controller.dto.AssistantDailyReportStatusResponse
import ai.govbiz.core.assistant.controller.dto.AssistantProgramSearchResponse
import ai.govbiz.core.assistant.controller.dto.AssistantProposalSummaryResponse
import ai.govbiz.core.assistant.controller.dto.AssistantRecruitmentResponse
import ai.govbiz.core.assistant.controller.dto.AssistantSavedProgramResponse
import ai.govbiz.core.assistant.service.AssistantToolService
import ai.govbiz.core.assistant.service.AssistantWorkToolService
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * AI Service의 도우미 도구가 부르는 내부 읽기 API입니다. 인증은 [ai.govbiz.core.assistant.web.AssistantToolAuthInterceptor]가
 * 공유 비밀과 계정 묶음 토큰으로 하며, 여기서는 그 계정의 자료만 읽습니다. 쓰기 엔드포인트는 두지 않습니다.
 */
@RestController
@RequestMapping("/internal/v1/assistant/tools")
class AssistantToolController(
    private val service: AssistantToolService,
    private val workService: AssistantWorkToolService,
) {
    @GetMapping("/company-profile")
    fun companyProfile(@RequestParam @Min(1) accountId: Long): ResponseEntity<AssistantCompanyProfileResponse> =
        noStore(AssistantCompanyProfileResponse.from(service.companyProfile(accountId)))

    @GetMapping("/recruitments")
    fun recruitments(
        @RequestParam @Min(1) accountId: Long,
        @RequestParam(required = false) @Size(max = 50) region: String?,
        @RequestParam(required = false) @Pattern(regexp = "LEAD|PARTICIPANT|DEMAND") seekingRole: String?,
        @RequestParam(required = false) @Size(max = 100) keyword: String?,
    ): ResponseEntity<List<AssistantRecruitmentResponse>> =
        noStore(service.searchRecruitments(accountId, region, seekingRole, keyword).map(AssistantRecruitmentResponse::from))

    @GetMapping("/saved-programs")
    fun savedPrograms(@RequestParam @Min(1) accountId: Long): ResponseEntity<List<AssistantSavedProgramResponse>> =
        noStore(service.savedPrograms(accountId).map(AssistantSavedProgramResponse::from))

    /** 공개 공고를 키워드·지역으로 찾습니다. AI 점수화가 없는 카탈로그 조회라 비용이 들지 않습니다. */
    @GetMapping("/programs")
    fun programs(
        @RequestParam @Min(1) accountId: Long,
        @RequestParam(required = false) @Size(max = 100) keyword: String?,
        @RequestParam(required = false) @Size(max = 50) region: String?,
    ): ResponseEntity<List<AssistantProgramSearchResponse>> =
        noStore(service.findPrograms(accountId, keyword, region).map(AssistantProgramSearchResponse::from))

    @GetMapping("/application-preparations")
    fun applicationPreparations(@RequestParam @Min(1) accountId: Long): ResponseEntity<List<AssistantApplicationPreparationResponse>> =
        noStore(workService.applicationPreparations(accountId).map(AssistantApplicationPreparationResponse::from))

    @GetMapping("/combination-reviews")
    fun combinationReviews(@RequestParam @Min(1) accountId: Long): ResponseEntity<List<AssistantCombinationReviewResponse>> =
        noStore(workService.combinationReviews(accountId).map(AssistantCombinationReviewResponse::from))

    @GetMapping("/daily-report")
    fun dailyReport(@RequestParam @Min(1) accountId: Long): ResponseEntity<AssistantDailyReportStatusResponse> =
        noStore(AssistantDailyReportStatusResponse.from(workService.dailyReportStatus(accountId)))

    @GetMapping("/proposals")
    fun proposals(@RequestParam @Min(1) accountId: Long): ResponseEntity<AssistantProposalSummaryResponse> =
        noStore(AssistantProposalSummaryResponse.from(workService.proposalSummary(accountId)))

    private fun <T : Any> noStore(body: T): ResponseEntity<T> = ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body)
}
