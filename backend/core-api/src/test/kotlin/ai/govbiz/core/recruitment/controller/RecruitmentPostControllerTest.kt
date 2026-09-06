package ai.govbiz.core.recruitment.controller

import ai.govbiz.core._common.exception.ApiExceptionHandler
import ai.govbiz.core.account.helper.AccountTestHelper
import ai.govbiz.core.account.service.AccountSessionService
import ai.govbiz.core.account.service.exception.AuthenticationRequiredException
import ai.govbiz.core.account.web.AuthenticatedAccountArgumentResolver
import ai.govbiz.core.recruitment.domain.RecruitmentPostStatus
import ai.govbiz.core.recruitment.helper.RecruitmentTestHelper
import ai.govbiz.core.recruitment.service.RecruitmentPostService
import ai.govbiz.core.recruitment.service.dto.RecruitmentPostPageResult
import ai.govbiz.core.recruitment.service.dto.RecruitmentPostResult
import ai.govbiz.core.recruitment.service.exception.ContactInTextException
import ai.govbiz.core.recruitment.service.exception.NotPostOwnerException
import ai.govbiz.core.recruitment.service.exception.RecruitmentPostNotFoundException
import ai.govbiz.core.recruitment.service.exception.RecruitmentPostNotOpenException
import ai.govbiz.core.recruitment.service.exception.SupportProgramNotOpenException
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class RecruitmentPostControllerTest {

    @Mock
    private lateinit var service: RecruitmentPostService

    @Mock
    private lateinit var sessionService: AccountSessionService

    private lateinit var mockMvc: MockMvc

    private val author = AccountTestHelper.account()

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(RecruitmentPostController(service))
            .setCustomArgumentResolvers(AuthenticatedAccountArgumentResolver(sessionService))
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun listsOpenPostsWithoutASessionAndReturnsTheStableContract() {
        doReturn(RecruitmentPostPageResult(listOf(result()), 0, 20, 1))
            .`when`(service).listOpen("BIZINFO", "PBLN_000000091203", 0, 20, null)

        mockMvc.perform(get(PATH).queryParam("sourceCode", "BIZINFO").queryParam("sourceProgramId", "PBLN_000000091203"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.totalCount").value(1))
            .andExpect(jsonPath("$.items[0].id").value(1))
            .andExpect(jsonPath("$.items[0].status").value("OPEN"))
            .andExpect(jsonPath("$.items[0].title").value("AI 실증 과제 데이터 구축·라벨링 참여기관 구합니다"))
            .andExpect(jsonPath("$.items[0].ourRole").value("LEAD"))
            .andExpect(jsonPath("$.items[0].wantedRole").value("PARTICIPANT"))
            .andExpect(jsonPath("$.items[0].requiredCapabilities[1]").value("라벨링 운영"))
            .andExpect(jsonPath("$.items[0].closesOn").value("2026-09-20"))
            .andExpect(jsonPath("$.items[0].createdAt").value("2026-09-06T12:00:00+09:00"))
            .andExpect(jsonPath("$.items[0].company.companyName").value("삼성전자(주)"))
            .andExpect(jsonPath("$.items[0].program.title").value("서울 AI 스타트업 실증 지원사업"))
            .andExpect(jsonPath("$.items[0].program.applicationEndDate").value("2026-09-30"))
            .andExpect(jsonPath("$.items[0].proposalCount").value(0))
            .andExpect(jsonPath("$.items[0].viewer.isOwner").value(false))

        verifyNoInteractions(sessionService)
    }

    @Test
    fun resolvesTheViewerWhenABearerTokenIsPresentAndRejectsAnInvalidOne() {
        doReturn(author).`when`(sessionService).requireAccount("Bearer token")
        doReturn(result(isOwner = true)).`when`(service).get(1L, author)
        doThrow(AuthenticationRequiredException()).`when`(sessionService).requireAccount("Bearer expired")

        mockMvc.perform(get("$PATH/1").header(HttpHeaders.AUTHORIZATION, "Bearer token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.viewer.isOwner").value(true))

        mockMvc.perform(get("$PATH/1").header(HttpHeaders.AUTHORIZATION, "Bearer expired"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
    }

    @Test
    fun createsAPostForTheSignedInAuthor() {
        doReturn(author).`when`(sessionService).requireAccount("Bearer token")
        doReturn(result(isOwner = true)).`when`(service)
            .create(author, "BIZINFO", "PBLN_000000091203", RecruitmentTestHelper.draft())

        mockMvc.perform(
            post(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody()),
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.viewer.isOwner").value(true))
    }

    @Test
    fun requiresASessionForWritingAndMyPosts() {
        doThrow(AuthenticationRequiredException()).`when`(sessionService).requireAccount(null)

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(createBody()))
            .andExpect(status().isUnauthorized())
        mockMvc.perform(get("$PATH/mine"))
            .andExpect(status().isUnauthorized())
        mockMvc.perform(post("$PATH/1/close"))
            .andExpect(status().isUnauthorized())

        verifyNoInteractions(service)
    }

    @Test
    fun rejectsInvalidFieldsBeforeReachingTheService() {
        doReturn(author).`when`(sessionService).requireAccount("Bearer token")

        mockMvc.perform(
            post(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(title = "a".repeat(81), wantedCompanyCount = 0)),
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[*].field").isArray())

        mockMvc.perform(
            post(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(ourRole = "DEMAND")),
        )
            .andExpect(status().isBadRequest())

        mockMvc.perform(get(PATH).queryParam("size", "51"))
            .andExpect(status().isBadRequest())

        verifyNoInteractions(service)
    }

    @Test
    fun mapsBusinessFailuresToStableProblems() {
        doReturn(author).`when`(sessionService).requireAccount("Bearer token")
        doThrow(SupportProgramNotOpenException()).`when`(service)
            .create(author, "BIZINFO", "PBLN_000000091203", RecruitmentTestHelper.draft())
        doThrow(ContactInTextException()).`when`(service).update(author, 1L, RecruitmentTestHelper.draft())
        doThrow(NotPostOwnerException()).`when`(service).closeEarly(author, 2L)
        doThrow(RecruitmentPostNotOpenException()).`when`(service).closeEarly(author, 3L)
        doThrow(RecruitmentPostNotFoundException()).`when`(service).get(404L, null)

        mockMvc.perform(
            post(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .contentType(MediaType.APPLICATION_JSON).content(createBody()),
        )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("SUPPORT_PROGRAM_NOT_OPEN"))

        mockMvc.perform(
            put("$PATH/1").header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .contentType(MediaType.APPLICATION_JSON).content("""{"post":${fieldsBody()}}"""),
        )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("CONTACT_IN_TEXT"))

        mockMvc.perform(post("$PATH/2/close").header(HttpHeaders.AUTHORIZATION, "Bearer token"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_POST_OWNER"))

        mockMvc.perform(post("$PATH/3/close").header(HttpHeaders.AUTHORIZATION, "Bearer token"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RECRUITMENT_POST_NOT_OPEN"))

        mockMvc.perform(get("$PATH/404"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RECRUITMENT_POST_NOT_FOUND"))
    }

    private fun result(isOwner: Boolean = false) =
        RecruitmentPostResult(
            post = RecruitmentTestHelper.post(),
            status = RecruitmentPostStatus.OPEN,
            company = AccountTestHelper.company(),
            program = RecruitmentTestHelper.program(),
            isOwner = isOwner,
        )

    private fun createBody(
        title: String = "AI 실증 과제 데이터 구축·라벨링 참여기관 구합니다",
        wantedCompanyCount: Int = 1,
        ourRole: String = "LEAD",
    ) = """{"sourceCode":"BIZINFO","sourceProgramId":"PBLN_000000091203","post":${fieldsBody(title, wantedCompanyCount, ourRole)}}"""

    private fun fieldsBody(
        title: String = "AI 실증 과제 데이터 구축·라벨링 참여기관 구합니다",
        wantedCompanyCount: Int = 1,
        ourRole: String = "LEAD",
    ) = """{"title":"$title","body":"학습용 민원 문서 정제와 라벨링을 맡아 주실 참여기관을 찾습니다.","ourRole":"$ourRole","wantedRole":"PARTICIPANT","wantedCompanyCount":$wantedCompanyCount,"wantedRegion":"서울·경기·인천","requiredCapabilities":["데이터 구축","라벨링 운영"],"closesOn":"2026-09-20"}"""

    private companion object {
        const val PATH = "/api/v1/recruitment-posts"
    }
}
