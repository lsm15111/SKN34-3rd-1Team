package ai.govbiz.core.recruitment.controller

import ai.govbiz.core._common.test.MySqlTestContainerConfig
import ai.govbiz.core.account.client.bizno.BiznoClient
import ai.govbiz.core.account.client.bizno.dto.BiznoBusiness
import ai.govbiz.core.supportprogram.domain.CatalogSupportProgram
import ai.govbiz.core.supportprogram.helper.SupportProgramTestHelper
import ai.govbiz.core.supportprogram.repository.SupportProgramRepository
import java.time.LocalDate
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper

/** 실제 MySQL과 HTTP 계층으로 두 기업이 모집글을 등록·수정·마감하는 흐름을 확인합니다. Bizno만 대역입니다. */
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
class RecruitmentPostFlowIntegrationTest {

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
    fun ownerCreatesUpdatesAndClosesWhileOthersOnlyRead() {
        val ownerToken = signUp("owner@company.co.kr", "1248100998")
        val otherToken = signUp("other@company.co.kr", "2208162517")

        val created = mockMvc.perform(
            post(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content(createBody(closesOn = LocalDate.now().plusDays(7))),
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.company.companyName").value("데이터브릿지 주식회사"))
            .andExpect(jsonPath("$.program.title").value("PBLN_FLOW 지원사업"))
            .andExpect(jsonPath("$.viewer.isOwner").value(true))
            .andReturn().response.contentAsString
        val postId = objectMapper.readTree(created).path("id").asLong()

        mockMvc.perform(get(PATH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCount").value(1))
            .andExpect(jsonPath("$.items[0].viewer.isOwner").value(false))

        mockMvc.perform(get("$PATH/$postId").header(HttpHeaders.AUTHORIZATION, "Bearer $otherToken"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.viewer.isOwner").value(false))

        mockMvc.perform(
            put("$PATH/$postId").header(HttpHeaders.AUTHORIZATION, "Bearer $otherToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"post":${fieldsBody(LocalDate.now().plusDays(7), title = "남의 글 수정")}}"""),
        )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_POST_OWNER"))

        mockMvc.perform(
            put("$PATH/$postId").header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"post":${fieldsBody(LocalDate.now().plusDays(7), title = "수정된 제목")}}"""),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("수정된 제목"))

        mockMvc.perform(
            put("$PATH/$postId").header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content("""{"post":${fieldsBody(LocalDate.now().plusDays(7), body = "연락 010-1234-5678")}}"""),
        )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("CONTACT_IN_TEXT"))

        mockMvc.perform(post("$PATH/$postId/close").header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLOSED"))

        mockMvc.perform(get(PATH))
            .andExpect(jsonPath("$.totalCount").value(0))
        mockMvc.perform(get("$PATH/$postId"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RECRUITMENT_POST_NOT_FOUND"))
        mockMvc.perform(get("$PATH/mine").header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken"))
            .andExpect(jsonPath("$.items[0].status").value("CLOSED"))
        mockMvc.perform(post("$PATH/$postId/close").header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RECRUITMENT_POST_NOT_OPEN"))
    }

    @Test
    fun rejectsPostsForUnknownProgramsOrLateClosingDates() {
        val ownerToken = signUp("owner@company.co.kr", "1248100998")

        mockMvc.perform(
            post(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(closesOn = LocalDate.now().plusDays(7), sourceProgramId = "PBLN_MISSING")),
        )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("SUPPORT_PROGRAM_NOT_OPEN"))

        mockMvc.perform(
            post(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON).content(createBody(closesOn = LocalDate.now().plusDays(40))),
        )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("RECRUITMENT_CLOSES_ON_INVALID"))
    }

    private fun signUp(email: String, businessNumber: String): String {
        val body = mockMvc.perform(
            post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"password1","businessNumber":"$businessNumber"}"""),
        ).andExpect(status().isCreated()).andReturn().response.contentAsString
        return objectMapper.readTree(body).path("sessionToken").asString()
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

    private fun createBody(closesOn: LocalDate, sourceProgramId: String = "PBLN_FLOW") =
        """{"sourceCode":"BIZINFO","sourceProgramId":"$sourceProgramId","post":${fieldsBody(closesOn)}}"""

    private fun fieldsBody(
        closesOn: LocalDate,
        title: String = "AI 실증 과제 데이터 구축·라벨링 참여기관 구합니다",
        body: String = "학습용 민원 문서 정제와 라벨링을 맡아 주실 참여기관을 찾습니다.",
    ) = """{"title":"$title","body":"$body","ourRole":"LEAD","wantedRole":"PARTICIPANT","wantedCompanyCount":1,"wantedRegion":"서울·경기·인천","requiredCapabilities":["데이터 구축"],"closesOn":"$closesOn"}"""

    private companion object {
        const val PATH = "/api/v1/recruitment-posts"
    }
}
