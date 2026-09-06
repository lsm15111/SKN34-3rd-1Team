package ai.govbiz.core.recruitment.controller

import ai.govbiz.core._common.test.MySqlTestContainerConfig
import ai.govbiz.core.account.client.bizno.BiznoClient
import ai.govbiz.core.account.client.bizno.dto.BiznoBusiness
import ai.govbiz.core.supportprogram.domain.CatalogSupportProgram
import ai.govbiz.core.supportprogram.helper.SupportProgramTestHelper
import ai.govbiz.core.supportprogram.repository.SupportProgramRepository
import java.time.LocalDate
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper

/** 실제 MySQL과 HTTP 계층으로 작성 기업과 제안 기업이 제안을 주고받는 흐름을 확인합니다. Bizno만 대역입니다. */
@SpringBootTest(
    properties = [
        "app.ai-service.base-url=http://127.0.0.1:1",
        "app.ai-service.connect-timeout=10ms",
        "app.ai-service.read-timeout=10ms",
        "app.bizinfo.sync.enabled=false",
        "app.support-program-index.enabled=false",
    ],
)
@AutoConfigureMockMvc
@Import(MySqlTestContainerConfig::class)
class RecruitmentProposalFlowIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var supportProgramRepository: SupportProgramRepository

    @MockitoBean
    private lateinit var biznoClient: BiznoClient

    @BeforeEach
    fun resetTables() {
        jdbcTemplate.update("DELETE FROM recruitment_proposal")
        jdbcTemplate.update("DELETE FROM recruitment_post")
        jdbcTemplate.update("DELETE FROM account_session")
        jdbcTemplate.update("DELETE FROM account")
        jdbcTemplate.update("DELETE FROM company")
        jdbcTemplate.update("DELETE FROM support_program_source_document")
        jdbcTemplate.update("DELETE FROM support_program")
        supportProgramRepository.upsert(openProgram())
        doReturn(listOf(BiznoBusiness("1248100998", "데이터브릿지 주식회사", "계속사업자")))
            .`when`(biznoClient).findByBusinessNumber("1248100998")
        doReturn(listOf(BiznoBusiness("2208162517", "비전솔루션", "계속사업자")))
            .`when`(biznoClient).findByBusinessNumber("2208162517")
    }

    @Test
    fun partnerProposesOwnerAcceptsAndBothSeeEachOthersEmail() {
        val ownerToken = signUp("owner@company.co.kr", "1248100998")
        val partnerToken = signUp("partner@vision.co.kr", "2208162517")
        val postId = createPost(ownerToken)

        val sent = mockMvc.perform(
            post("$POSTS/$postId/proposals").header(HttpHeaders.AUTHORIZATION, "Bearer $partnerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"message":"라벨링 운영 경험이 있는 참여기관입니다."}"""),
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.company.companyName").value("비전솔루션"))
            .andExpect(jsonPath("$.post.companyName").value("데이터브릿지 주식회사"))
            .andExpect(jsonPath("$.contactEmail").value(nullValue()))
            .andReturn().response.contentAsString
        val proposalId = objectMapper.readTree(sent).path("id").asLong()

        mockMvc.perform(
            post("$POSTS/$postId/proposals").header(HttpHeaders.AUTHORIZATION, "Bearer $partnerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"message":"한 번 더 제안합니다."}"""),
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PROPOSAL_ALREADY_EXISTS"))

        mockMvc.perform(
            post("$POSTS/$postId/proposals").header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"message":"우리 글에 제안"}"""),
        )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("OWN_POST"))

        mockMvc.perform(
            post("$POSTS/$postId/proposals").header(HttpHeaders.AUTHORIZATION, "Bearer $partnerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"message":"연락은 partner@vision.co.kr"}"""),
        )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("CONTACT_IN_TEXT"))

        mockMvc.perform(get("$POSTS/$postId").header(HttpHeaders.AUTHORIZATION, "Bearer $partnerToken"))
            .andExpect(jsonPath("$.proposalCount").value(1))
            .andExpect(jsonPath("$.viewer.myProposalStatus").value("PENDING"))
        mockMvc.perform(get("$POSTS/$postId"))
            .andExpect(jsonPath("$.proposalCount").value(1))
            .andExpect(jsonPath("$.viewer.myProposalStatus").value(nullValue()))

        mockMvc.perform(get("$POSTS/$postId/proposals").header(HttpHeaders.AUTHORIZATION, "Bearer $partnerToken"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_POST_OWNER"))
        mockMvc.perform(get("$POSTS/$postId/proposals").header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].status").value("PENDING"))
            .andExpect(jsonPath("$.items[0].contactEmail").value(nullValue()))

        mockMvc.perform(post("$PROPOSALS/$proposalId/accept").header(HttpHeaders.AUTHORIZATION, "Bearer $partnerToken"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_POST_OWNER"))
        mockMvc.perform(post("$PROPOSALS/$proposalId/accept").header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.contactEmail").value("partner@vision.co.kr"))

        mockMvc.perform(get("$PROPOSALS/sent").header(HttpHeaders.AUTHORIZATION, "Bearer $partnerToken"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].status").value("ACCEPTED"))
            .andExpect(jsonPath("$.items[0].post.title").value("AI 실증 과제 데이터 구축·라벨링 참여기관 구합니다"))
            .andExpect(jsonPath("$.items[0].contactEmail").value("owner@company.co.kr"))

        mockMvc.perform(post("$PROPOSALS/$proposalId/withdraw").header(HttpHeaders.AUTHORIZATION, "Bearer $partnerToken"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_PENDING"))
        mockMvc.perform(post("$PROPOSALS/$proposalId/decline").header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_PENDING"))
    }

    @Test
    fun partnerWithdrawsAndClosedPostsRefuseNewProposals() {
        val ownerToken = signUp("owner@company.co.kr", "1248100998")
        val partnerToken = signUp("partner@vision.co.kr", "2208162517")
        val postId = createPost(ownerToken)

        val sent = mockMvc.perform(
            post("$POSTS/$postId/proposals").header(HttpHeaders.AUTHORIZATION, "Bearer $partnerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"message":"참여하고 싶습니다."}"""),
        ).andExpect(status().isCreated()).andReturn().response.contentAsString
        val proposalId = objectMapper.readTree(sent).path("id").asLong()

        mockMvc.perform(post("$PROPOSALS/$proposalId/withdraw").header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_PROPOSAL_OWNER"))
        mockMvc.perform(post("$PROPOSALS/$proposalId/withdraw").header(HttpHeaders.AUTHORIZATION, "Bearer $partnerToken"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("WITHDRAWN"))
        mockMvc.perform(get("$POSTS/$postId"))
            .andExpect(jsonPath("$.proposalCount").value(0))

        mockMvc.perform(post("$POSTS/$postId/close").header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken"))
            .andExpect(status().isOk())
        mockMvc.perform(
            post("$POSTS/$postId/proposals").header(HttpHeaders.AUTHORIZATION, "Bearer $partnerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"message":"마감된 글에 제안"}"""),
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RECRUITMENT_POST_NOT_OPEN"))
        mockMvc.perform(get("$PROPOSALS/sent").header(HttpHeaders.AUTHORIZATION, "Bearer $partnerToken"))
            .andExpect(jsonPath("$.items[0].status").value("WITHDRAWN"))
            .andExpect(jsonPath("$.items[0].post.status").value("CLOSED"))
        mockMvc.perform(post("$PROPOSALS/${proposalId + 1_000}/accept").header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_FOUND"))
    }

    private fun signUp(email: String, businessNumber: String): String {
        val body = mockMvc.perform(
            post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"password1","businessNumber":"$businessNumber"}"""),
        ).andExpect(status().isCreated()).andReturn().response.contentAsString
        return objectMapper.readTree(body).path("sessionToken").asString()
    }

    private fun createPost(ownerToken: String): Long {
        val closesOn = LocalDate.now().plusDays(7)
        val body = mockMvc.perform(
            post(POSTS).header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"sourceCode":"BIZINFO","sourceProgramId":"PBLN_FLOW","post":{"title":"AI 실증 과제 데이터 구축·라벨링 참여기관 구합니다","body":"학습용 민원 문서 정제와 라벨링을 맡아 주실 참여기관을 찾습니다.","ourRole":"LEAD","wantedRole":"PARTICIPANT","wantedCompanyCount":1,"wantedRegion":"서울·경기·인천","requiredCapabilities":["데이터 구축"],"closesOn":"$closesOn"}}""",
                ),
        ).andExpect(status().isCreated()).andReturn().response.contentAsString
        return objectMapper.readTree(body).path("id").asLong()
    }

    private fun openProgram(): CatalogSupportProgram {
        val base = SupportProgramTestHelper.catalogProgram("PBLN_FLOW")
        return base.copy(
            program = base.program.copy(
                applicationPeriod = "접수 중",
                applicationStartDate = LocalDate.now().minusDays(5),
                applicationEndDate = LocalDate.now().plusDays(30),
            ),
        )
    }

    private companion object {
        const val POSTS = "/api/v1/recruitment-posts"
        const val PROPOSALS = "/api/v1/recruitment-proposals"
    }
}
