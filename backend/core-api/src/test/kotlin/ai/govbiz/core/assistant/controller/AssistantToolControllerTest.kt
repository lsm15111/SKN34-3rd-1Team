package ai.govbiz.core.assistant.controller

import ai.govbiz.core._common.exception.ApiExceptionHandler
import ai.govbiz.core.assistant.config.AssistantAgentProperties
import ai.govbiz.core.assistant.domain.AssistantApplicationPreparationSummary
import ai.govbiz.core.assistant.domain.AssistantCombinationReviewSummary
import ai.govbiz.core.assistant.domain.AssistantCompanyProfile
import ai.govbiz.core.assistant.domain.AssistantDailyReportStatus
import ai.govbiz.core.assistant.domain.AssistantProgramSearchResult
import ai.govbiz.core.assistant.domain.AssistantProposalSummary
import ai.govbiz.core.assistant.domain.AssistantRecruitmentSummary
import ai.govbiz.core.assistant.domain.AssistantSavedProgramSummary
import ai.govbiz.core.assistant.service.AssistantToolService
import ai.govbiz.core.assistant.service.AssistantWorkToolService
import ai.govbiz.core.assistant.service.AssistantToolTokenService
import ai.govbiz.core.assistant.web.AssistantToolAuthInterceptor
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.http.converter.json.ProblemDetailJacksonMixin
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

class AssistantToolControllerTest {
    private val service = Mockito.mock(AssistantToolService::class.java)
    private val workService = Mockito.mock(AssistantWorkToolService::class.java)
    private val secret = "assistant-tools-secret-for-tests-0123456789"
    private val clock = Clock.fixed(Instant.parse("2026-09-14T09:00:00Z"), ZoneOffset.UTC)
    private val enabled = AssistantAgentProperties(toolsSecret = secret)
    private val tokens = AssistantToolTokenService(enabled, clock)
    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build())
        .addMixIn(ProblemDetail::class.java, ProblemDetailJacksonMixin::class.java).build()
    private val validator = LocalValidatorFactoryBean().apply { afterPropertiesSet() }

    @AfterEach
    fun closeValidator() = validator.close()

    private fun mvc(properties: AssistantAgentProperties = enabled): MockMvc =
        MockMvcBuilders.standaloneSetup(AssistantToolController(service, workService))
            .addInterceptors(AssistantToolAuthInterceptor(properties, AssistantToolTokenService(properties, clock)))
            .setControllerAdvice(ApiExceptionHandler()).setValidator(validator)
            .setMessageConverters(JacksonJsonHttpMessageConverter(mapper)).build()

    private fun authorized(path: String, accountId: Long = 7L, vararg params: Pair<String, String>) =
        get("/internal/v1/assistant/tools/$path").param("accountId", accountId.toString())
            .header(AssistantToolAuthInterceptor.SECRET_HEADER, secret)
            .header(AssistantToolAuthInterceptor.TOKEN_HEADER, tokens.issue(accountId).value)
            .also { request -> params.forEach { (name, value) -> request.param(name, value) } }

    @Test
    fun companyProfileReturnsTheProfileWithoutCaching() {
        `when`(service.companyProfile(7L)).thenReturn(
            AssistantCompanyProfile(true, "데이터브릿지 주식회사", "서울특별시", "정보통신업", 2021, listOf("LEAD"), listOf("AI"), "소개", listOf("라벨링")),
        )

        mvc().perform(authorized("company-profile"))
            .andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.registered").value(true))
            .andExpect(jsonPath("$.companyName").value("데이터브릿지 주식회사"))
            .andExpect(jsonPath("$.roles[0]").value("LEAD"))
            .andExpect(jsonPath("$.businessNumber").doesNotExist())
            .andExpect(jsonPath("$.email").doesNotExist())
    }

    @Test
    fun recruitmentsPassFiltersThroughAndRenderDatesAsIsoStrings() {
        `when`(service.searchRecruitments(7L, "서울", "PARTICIPANT", "실증")).thenReturn(
            listOf(
                AssistantRecruitmentSummary(
                    21L, "AI 실증 참여기관 구합니다", "데이터브릿지 주식회사", "서울특별시", "정보통신업", "LEAD", "PARTICIPANT", 1, "서울", null,
                    listOf("라벨링"), LocalDate.of(2026, 9, 20), "서울 AI 실증 지원사업", LocalDate.of(2026, 9, 30), "본문",
                ),
            ),
        )

        mvc().perform(authorized("recruitments", 7L, "region" to "서울", "seekingRole" to "PARTICIPANT", "keyword" to "실증"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(21))
            .andExpect(jsonPath("$[0].recruitmentDeadline").value("2026-09-20"))
            .andExpect(jsonPath("$[0].programApplicationEndDate").value("2026-09-30"))
            .andExpect(jsonPath("$[0].minimumCompanyAgeYears").value(null as Any?))
    }

    @Test
    fun recruitmentsWithoutFiltersPassNulls() {
        `when`(service.searchRecruitments(7L, null, null, null)).thenReturn(emptyList())

        mvc().perform(authorized("recruitments"))
            .andExpect(status().isOk)
            .andExpect(content().json("[]"))
        Mockito.verify(service).searchRecruitments(7L, null, null, null)
    }

    @Test
    fun recruitmentsRejectAnUnknownRoleAndOverlongFilters() {
        mvc().perform(authorized("recruitments", 7L, "seekingRole" to "OWNER"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
        mvc().perform(authorized("recruitments", 7L, "keyword" to "가".repeat(101)))
            .andExpect(status().isBadRequest)
        Mockito.verifyNoInteractions(service)
    }

    @Test
    fun savedProgramsReturnTheListWithOptionalDocumentIds() {
        `when`(service.savedPrograms(7L)).thenReturn(
            listOf(
                AssistantSavedProgramSummary("BIZINFO", "PBLN_000000000000001", "서울 AI 실증 지원사업", "서울경제진흥원", LocalDate.of(2026, 9, 30), "OPEN", "BIZINFO:PBLN_000000000000001"),
                AssistantSavedProgramSummary("BIZINFO", "PBLN_000000000000002", "경기 데이터 바우처", "경기도", null, "CLOSED", null),
            ),
        )

        mvc().perform(authorized("saved-programs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].documentId").value("BIZINFO:PBLN_000000000000001"))
            .andExpect(jsonPath("$[0].applicationEndDate").value("2026-09-30"))
            .andExpect(jsonPath("$[1].documentId").value(null as Any?))
    }

    @Test
    fun requestsWithoutTheSharedSecretAreUnauthorized() {
        mvc().perform(get("/internal/v1/assistant/tools/company-profile").param("accountId", "7").header(AssistantToolAuthInterceptor.TOKEN_HEADER, tokens.issue(7L).value))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("ASSISTANT_TOOL_UNAUTHORIZED"))
        mvc().perform(get("/internal/v1/assistant/tools/company-profile").param("accountId", "7").header(AssistantToolAuthInterceptor.SECRET_HEADER, "wrong").header(AssistantToolAuthInterceptor.TOKEN_HEADER, tokens.issue(7L).value))
            .andExpect(status().isUnauthorized)
        Mockito.verifyNoInteractions(service)
    }

    @Test
    fun requestsWithoutAValidAccountTokenAreUnauthorized() {
        mvc().perform(get("/internal/v1/assistant/tools/saved-programs").param("accountId", "7").header(AssistantToolAuthInterceptor.SECRET_HEADER, secret))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("ASSISTANT_TOOL_UNAUTHORIZED"))
        mvc().perform(get("/internal/v1/assistant/tools/saved-programs").param("accountId", "8").header(AssistantToolAuthInterceptor.SECRET_HEADER, secret).header(AssistantToolAuthInterceptor.TOKEN_HEADER, tokens.issue(7L).value))
            .andExpect(status().isUnauthorized)
        mvc().perform(get("/internal/v1/assistant/tools/saved-programs").header(AssistantToolAuthInterceptor.SECRET_HEADER, secret).header(AssistantToolAuthInterceptor.TOKEN_HEADER, tokens.issue(7L).value))
            .andExpect(status().isUnauthorized)
        mvc().perform(get("/internal/v1/assistant/tools/saved-programs").param("accountId", "abc").header(AssistantToolAuthInterceptor.SECRET_HEADER, secret).header(AssistantToolAuthInterceptor.TOKEN_HEADER, tokens.issue(7L).value))
            .andExpect(status().isUnauthorized)
        Mockito.verifyNoInteractions(service)
    }

    @Test
    fun toolsWithoutASecretAreUnavailable() {
        mvc(AssistantAgentProperties()).perform(authorized("company-profile"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.code").value("ASSISTANT_TOOLS_DISABLED"))
        Mockito.verifyNoInteractions(service)
    }

    @Test
    fun programsSearchPassesKeywordAndRegionAndMarksSavedPrograms() {
        `when`(service.findPrograms(7L, "창업 지원", "서울")).thenReturn(
            listOf(AssistantProgramSearchResult("KSTARTUP", "174520", "예비창업패키지", "창업진흥원", LocalDate.of(2026, 10, 10), "OPEN", listOf("서울"), false)),
        )

        mvc().perform(authorized("programs", params = arrayOf("keyword" to "창업 지원", "region" to "서울")))
            .andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$[0].sourceProgramId").value("174520"))
            .andExpect(jsonPath("$[0].applicationEndDate").value("2026-10-10"))
            .andExpect(jsonPath("$[0].saved").value(false))
    }

    @Test
    fun applicationPreparationsCarryTheProgressStageAndRevision() {
        `when`(workService.applicationPreparations(7L)).thenReturn(
            listOf(AssistantApplicationPreparationSummary(31L, "BIZINFO", "PBLN_000000000000001", "서울 AI 실증 지원사업", "PREPARING", 2L, LocalDate.of(2026, 9, 16))),
        )

        mvc().perform(authorized("application-preparations"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(31))
            .andExpect(jsonPath("$[0].progressStage").value("PREPARING"))
            .andExpect(jsonPath("$[0].progressRevision").value(2))
            .andExpect(jsonPath("$[0].updatedAt").value("2026-09-16"))
    }

    @Test
    fun combinationReviewsCarryTheLatestRunStatusOnly() {
        `when`(workService.combinationReviews(7L)).thenReturn(
            listOf(AssistantCombinationReviewSummary(41L, "혁신바우처와 R&D", 3L, listOf("혁신바우처", "R&D"), "SUCCEEDED", 77L, LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 15))),
        )

        mvc().perform(authorized("combination-reviews"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].latestRunStatus").value("SUCCEEDED"))
            .andExpect(jsonPath("$[0].programTitles[1]").value("R&D"))
            .andExpect(jsonPath("$[0].judgment").doesNotExist())
    }

    @Test
    fun dailyReportAndProposalsCarryCountsWithoutEmailOrCompanyNames() {
        `when`(workService.dailyReportStatus(7L)).thenReturn(AssistantDailyReportStatus(true, true, "AI 실증", true, 8, LocalDate.of(2026, 9, 17)))
        `when`(workService.proposalSummary(7L)).thenReturn(AssistantProposalSummary(true, 2, 1, LocalDate.of(2026, 9, 22)))

        mvc().perform(authorized("daily-report"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.latestReportDate").value("2026-09-17"))
            .andExpect(jsonPath("$.email").doesNotExist())
        mvc().perform(authorized("proposals"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.receivedPending").value(2))
            .andExpect(jsonPath("$.earliestExpiryDate").value("2026-09-22"))
            .andExpect(jsonPath("$.companyName").doesNotExist())
    }
}
