package ai.govbiz.core.account.admin

import ai.govbiz.core._common.exception.ApiExceptionHandler
import ai.govbiz.core.account.domain.AccountRole
import ai.govbiz.core.account.helper.AccountTestHelper
import ai.govbiz.core.account.service.AccountSessionService
import ai.govbiz.core.account.web.AuthenticatedAccountArgumentResolver
import ai.govbiz.core.recruitment.domain.RecruitmentPostStatus
import ai.govbiz.core.recruitment.helper.RecruitmentTestHelper
import ai.govbiz.core.recruitment.service.RecruitmentPostService
import ai.govbiz.core.recruitment.service.dto.RecruitmentPostPageResult
import ai.govbiz.core.recruitment.service.dto.RecruitmentPostResult
import ai.govbiz.core.recruitment.service.exception.RecruitmentPostAlreadyHiddenException
import ai.govbiz.core.recruitment.service.exception.RecruitmentPostNotFoundException
import ai.govbiz.core.recruitment.service.exception.RecruitmentPostNotHiddenException
import ai.govbiz.core.recruitment.service.exception.RecruitmentPostNotOpenException
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class AdminRecruitmentPostControllerTest {

    @Mock
    private lateinit var recruitmentPostService: RecruitmentPostService

    @Mock
    private lateinit var sessionService: AccountSessionService

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(AdminRecruitmentPostController(AdminRecruitmentPostService(recruitmentPostService)))
            .setCustomArgumentResolvers(AuthenticatedAccountArgumentResolver(sessionService))
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun listsEveryPostWithHiddenDetailsForAnAdministrator() {
        doReturn(admin()).`when`(sessionService).requireAccount(ADMIN_BEARER)
        val hidden = RecruitmentTestHelper.post(hiddenAt = RecruitmentTestHelper.NOW, hiddenReason = "연락처 노출")
        doReturn(RecruitmentPostPageResult(listOf(result(hidden, RecruitmentPostStatus.HIDDEN)), 0, 20, 1))
            .`when`(recruitmentPostService).listForAdmin(RecruitmentPostStatus.HIDDEN, 0, 20)

        mockMvc.perform(get(PATH).queryParam("status", "HIDDEN").header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.totalCount").value(1))
            .andExpect(jsonPath("$.items[0].id").value(1))
            .andExpect(jsonPath("$.items[0].status").value("HIDDEN"))
            .andExpect(jsonPath("$.items[0].title").value("AI 실증 과제 데이터 구축·라벨링 참여기관 구합니다"))
            .andExpect(jsonPath("$.items[0].hiddenAt").value("2026-09-06T12:00:00+09:00"))
            .andExpect(jsonPath("$.items[0].hiddenReason").value("연락처 노출"))
            .andExpect(jsonPath("$.items[0].company.companyName").value("삼성전자(주)"))
            .andExpect(jsonPath("$.items[0].program.title").value("서울 AI 스타트업 실증 지원사업"))
            .andExpect(jsonPath("$.items[0].proposalCount").value(2))
            .andExpect(jsonPath("$.items[0].viewer").doesNotExist())
    }

    @Test
    fun hidesUnhidesAndClosesWithAReason() {
        doReturn(admin()).`when`(sessionService).requireAccount(ADMIN_BEARER)
        val hidden = RecruitmentTestHelper.post(hiddenAt = RecruitmentTestHelper.NOW, hiddenReason = "연락처 노출")
        doReturn(result(hidden, RecruitmentPostStatus.HIDDEN)).`when`(recruitmentPostService).hide(1L, "연락처 노출")
        doReturn(result(RecruitmentTestHelper.post(), RecruitmentPostStatus.OPEN)).`when`(recruitmentPostService).unhide(1L)
        doReturn(result(RecruitmentTestHelper.post(closedEarlyAt = RecruitmentTestHelper.NOW), RecruitmentPostStatus.CLOSED))
            .`when`(recruitmentPostService).closeByAdmin(1L)

        mockMvc.perform(
            post("$PATH/1/hide").header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER)
                .contentType(MediaType.APPLICATION_JSON).content("""{"reason":"연락처 노출"}"""),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("HIDDEN"))
            .andExpect(jsonPath("$.hiddenReason").value("연락처 노출"))

        mockMvc.perform(post("$PATH/1/unhide").header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.hiddenAt").value(nullValue()))

        mockMvc.perform(
            post("$PATH/1/close").header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER)
                .contentType(MediaType.APPLICATION_JSON).content("""{"reason":"공고와 무관한 모집"}"""),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLOSED"))
            .andExpect(jsonPath("$.closedEarlyAt").value("2026-09-06T12:00:00+09:00"))
    }

    @Test
    fun rejectsNonAdministratorsAndMissingSessions() {
        doReturn(AccountTestHelper.account()).`when`(sessionService).requireAccount(USER_BEARER)

        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, USER_BEARER))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"))
        mockMvc.perform(
            post("$PATH/1/hide").header(HttpHeaders.AUTHORIZATION, USER_BEARER)
                .contentType(MediaType.APPLICATION_JSON).content("""{"reason":"사유"}"""),
        )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"))

        verifyNoInteractions(recruitmentPostService)
    }

    @Test
    fun requiresAReasonAndValidPagingBeforeTheService() {
        doReturn(admin()).`when`(sessionService).requireAccount(ADMIN_BEARER)

        mockMvc.perform(
            post("$PATH/1/hide").header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER)
                .contentType(MediaType.APPLICATION_JSON).content("""{"reason":"  "}"""),
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
        mockMvc.perform(
            post("$PATH/1/close").header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER)
                .contentType(MediaType.APPLICATION_JSON).content("""{"reason":"${"가".repeat(201)}"}"""),
        )
            .andExpect(status().isBadRequest())
        mockMvc.perform(get(PATH).queryParam("size", "101").header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER))
            .andExpect(status().isBadRequest())
        mockMvc.perform(get(PATH).queryParam("status", "MAYBE").header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER))
            .andExpect(status().isBadRequest())

        verifyNoInteractions(recruitmentPostService)
    }

    @Test
    fun mapsStateConflictsAndMissingPostsToStableProblems() {
        doReturn(admin()).`when`(sessionService).requireAccount(ADMIN_BEARER)
        doThrow(RecruitmentPostAlreadyHiddenException()).`when`(recruitmentPostService).hide(1L, "사유")
        doThrow(RecruitmentPostNotHiddenException()).`when`(recruitmentPostService).unhide(2L)
        doThrow(RecruitmentPostNotOpenException()).`when`(recruitmentPostService).closeByAdmin(3L)
        doThrow(RecruitmentPostNotFoundException()).`when`(recruitmentPostService).unhide(404L)

        mockMvc.perform(
            post("$PATH/1/hide").header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER)
                .contentType(MediaType.APPLICATION_JSON).content("""{"reason":"사유"}"""),
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RECRUITMENT_POST_ALREADY_HIDDEN"))
        mockMvc.perform(post("$PATH/2/unhide").header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RECRUITMENT_POST_NOT_HIDDEN"))
        mockMvc.perform(
            post("$PATH/3/close").header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER)
                .contentType(MediaType.APPLICATION_JSON).content("""{"reason":"사유"}"""),
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RECRUITMENT_POST_NOT_OPEN"))
        mockMvc.perform(post("$PATH/404/unhide").header(HttpHeaders.AUTHORIZATION, ADMIN_BEARER))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RECRUITMENT_POST_NOT_FOUND"))
    }

    private fun result(post: ai.govbiz.core.recruitment.domain.RecruitmentPost, status: RecruitmentPostStatus) =
        RecruitmentPostResult(
            post = post,
            status = status,
            company = AccountTestHelper.company(),
            program = RecruitmentTestHelper.program(),
            isOwner = false,
            proposalCount = 2,
        )

    private fun admin() = AccountTestHelper.account(id = 9L, email = "admin@govbiz.test", role = AccountRole.ADMIN)

    private companion object {
        const val PATH = "/api/v1/admin/recruitment-posts"
        const val ADMIN_BEARER = "Bearer admin-token"
        const val USER_BEARER = "Bearer user-token"
    }
}
