package ai.govbiz.core.recruitment.controller

import ai.govbiz.core._common.exception.ApiExceptionHandler
import ai.govbiz.core.account.helper.AccountTestHelper
import ai.govbiz.core.account.service.AccountSessionService
import ai.govbiz.core.account.service.exception.AuthenticationRequiredException
import ai.govbiz.core.account.web.AuthenticatedAccountArgumentResolver
import ai.govbiz.core.recruitment.domain.ProposalDecision
import ai.govbiz.core.recruitment.domain.ProposalStatus
import ai.govbiz.core.recruitment.domain.RecruitmentPostStatus
import ai.govbiz.core.recruitment.helper.RecruitmentTestHelper
import ai.govbiz.core.recruitment.service.RecruitmentProposalService
import ai.govbiz.core.recruitment.service.dto.RecruitmentProposalResult
import ai.govbiz.core.recruitment.service.exception.NotProposalOwnerException
import ai.govbiz.core.recruitment.service.exception.OwnPostProposalException
import ai.govbiz.core.recruitment.service.exception.ProposalAlreadyExistsException
import ai.govbiz.core.recruitment.service.exception.ProposalNotFoundException
import ai.govbiz.core.recruitment.service.exception.ProposalNotPendingException
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
class RecruitmentProposalControllerTest {

    @Mock
    private lateinit var service: RecruitmentProposalService

    @Mock
    private lateinit var sessionService: AccountSessionService

    private lateinit var mockMvc: MockMvc

    private val proposer = AccountTestHelper.account(id = 2L, email = "partner@vision.co.kr")
        .let { it.copy(company = RecruitmentTestHelper.otherCompany()) }

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(RecruitmentProposalController(service))
            .setCustomArgumentResolvers(AuthenticatedAccountArgumentResolver(sessionService))
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    private fun signIn() {
        doReturn(proposer).`when`(sessionService).requireAccount("Bearer token")
    }

    @Test
    fun sendsAProposalAndReturnsTheStableContract() {
        signIn()
        doReturn(result()).`when`(service).send(proposer, 1L, RecruitmentTestHelper.proposal().message)

        mockMvc.perform(
            post("$POSTS/1/proposals")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"message":"${RecruitmentTestHelper.proposal().message}"}"""),
        )
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.postId").value(1))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.message").value(RecruitmentTestHelper.proposal().message))
            .andExpect(jsonPath("$.createdAt").value("2026-09-06T12:00:00+09:00"))
            .andExpect(jsonPath("$.decidedAt").value(nullValue()))
            .andExpect(jsonPath("$.company.companyName").value("비전솔루션"))
            .andExpect(jsonPath("$.post.id").value(1))
            .andExpect(jsonPath("$.post.status").value("OPEN"))
            .andExpect(jsonPath("$.post.title").value("AI 실증 과제 데이터 구축·라벨링 참여기관 구합니다"))
            .andExpect(jsonPath("$.post.companyName").value("삼성전자(주)"))
            .andExpect(jsonPath("$.contactEmail").value(nullValue()))
    }

    @Test
    fun exposesTheContactEmailOnlyInAcceptedResults() {
        signIn()
        doReturn(result(status = ProposalStatus.ACCEPTED, contactEmail = "manager@company.co.kr"))
            .`when`(service).accept(proposer, 1L)
        doReturn(listOf(result(status = ProposalStatus.ACCEPTED, contactEmail = "manager@company.co.kr")))
            .`when`(service).listSent(proposer)

        mockMvc.perform(post("$PROPOSALS/1/accept").header(HttpHeaders.AUTHORIZATION, "Bearer token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.decidedAt").value("2026-09-06T12:00:00+09:00"))
            .andExpect(jsonPath("$.contactEmail").value("manager@company.co.kr"))

        mockMvc.perform(get("$PROPOSALS/sent").header(HttpHeaders.AUTHORIZATION, "Bearer token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].contactEmail").value("manager@company.co.kr"))
    }

    @Test
    fun requiresASessionEverywhere() {
        doThrow(AuthenticationRequiredException()).`when`(sessionService).requireAccount(null)

        mockMvc.perform(post("$POSTS/1/proposals").contentType(MediaType.APPLICATION_JSON).content("""{"message":"제안"}"""))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
        mockMvc.perform(get("$POSTS/1/proposals")).andExpect(status().isUnauthorized())
        mockMvc.perform(get("$PROPOSALS/sent")).andExpect(status().isUnauthorized())
        mockMvc.perform(post("$PROPOSALS/1/accept")).andExpect(status().isUnauthorized())
        mockMvc.perform(post("$PROPOSALS/1/decline")).andExpect(status().isUnauthorized())
        mockMvc.perform(post("$PROPOSALS/1/withdraw")).andExpect(status().isUnauthorized())

        verifyNoInteractions(service)
    }

    @Test
    fun rejectsInvalidMessagesBeforeReachingTheService() {
        signIn()
        mockMvc.perform(
            post("$POSTS/1/proposals").header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .contentType(MediaType.APPLICATION_JSON).content("""{"message":"   "}"""),
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[*].field").isArray())

        mockMvc.perform(
            post("$POSTS/1/proposals").header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .contentType(MediaType.APPLICATION_JSON).content("""{"message":"${"가".repeat(501)}"}"""),
        )
            .andExpect(status().isBadRequest())

        mockMvc.perform(post("$POSTS/0/proposals").header(HttpHeaders.AUTHORIZATION, "Bearer token")
            .contentType(MediaType.APPLICATION_JSON).content("""{"message":"제안"}"""))
            .andExpect(status().isBadRequest())

        verifyNoInteractions(service)
    }

    @Test
    fun mapsBusinessFailuresToStableProblems() {
        signIn()
        doThrow(OwnPostProposalException()).`when`(service).send(proposer, 1L, "우리 글")
        doThrow(ProposalAlreadyExistsException()).`when`(service).send(proposer, 2L, "두 번째")
        doThrow(ProposalNotPendingException()).`when`(service).decline(proposer, 3L)
        doThrow(NotProposalOwnerException()).`when`(service).withdraw(proposer, 4L)
        doThrow(ProposalNotFoundException()).`when`(service).accept(proposer, 404L)

        mockMvc.perform(
            post("$POSTS/1/proposals").header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .contentType(MediaType.APPLICATION_JSON).content("""{"message":"우리 글"}"""),
        )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("OWN_POST"))

        mockMvc.perform(
            post("$POSTS/2/proposals").header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .contentType(MediaType.APPLICATION_JSON).content("""{"message":"두 번째"}"""),
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PROPOSAL_ALREADY_EXISTS"))

        mockMvc.perform(post("$PROPOSALS/3/decline").header(HttpHeaders.AUTHORIZATION, "Bearer token"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_PENDING"))

        mockMvc.perform(post("$PROPOSALS/4/withdraw").header(HttpHeaders.AUTHORIZATION, "Bearer token"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_PROPOSAL_OWNER"))

        mockMvc.perform(post("$PROPOSALS/404/accept").header(HttpHeaders.AUTHORIZATION, "Bearer token"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_FOUND"))
    }

    private fun result(status: ProposalStatus = ProposalStatus.PENDING, contactEmail: String? = null) =
        RecruitmentProposalResult(
            proposal = if (status == ProposalStatus.PENDING) {
                RecruitmentTestHelper.proposal()
            } else {
                RecruitmentTestHelper.proposal(decision = ProposalDecision.valueOf(status.name), decidedAt = RecruitmentTestHelper.NOW)
            },
            status = status,
            company = RecruitmentTestHelper.otherCompany(),
            post = RecruitmentTestHelper.post(),
            postStatus = RecruitmentPostStatus.OPEN,
            postCompany = AccountTestHelper.company(),
            contactEmail = contactEmail,
        )

    private companion object {
        const val POSTS = "/api/v1/recruitment-posts"
        const val PROPOSALS = "/api/v1/recruitment-proposals"
    }
}
