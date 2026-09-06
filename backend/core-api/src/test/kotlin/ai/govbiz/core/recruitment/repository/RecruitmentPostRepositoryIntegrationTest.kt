package ai.govbiz.core.recruitment.repository

import ai.govbiz.core._common.test.MySqlTestContainerConfig
import ai.govbiz.core.account.domain.NewAccount
import ai.govbiz.core.account.domain.NewAccountSession
import ai.govbiz.core.account.domain.VerifiedCompany
import ai.govbiz.core.account.repository.AccountRepository
import ai.govbiz.core.recruitment.helper.RecruitmentTestHelper
import ai.govbiz.core.supportprogram.helper.SupportProgramTestHelper
import ai.govbiz.core.supportprogram.repository.SupportProgramRepository
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest(
    properties = [
        "app.ai-service.base-url=http://127.0.0.1:1",
        "app.ai-service.connect-timeout=10ms",
        "app.ai-service.read-timeout=10ms",
        "app.bizinfo.sync.enabled=false",
        "app.support-program-index.enabled=false",
    ],
)
@Import(MySqlTestContainerConfig::class)
class RecruitmentPostRepositoryIntegrationTest {

    @Autowired
    private lateinit var repository: RecruitmentPostRepository

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var supportProgramRepository: SupportProgramRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private var companyId = 0L
    private var authorId = 0L

    @BeforeEach
    fun resetTables() {
        jdbcTemplate.update("DELETE FROM recruitment_post")
        jdbcTemplate.update("DELETE FROM account_session")
        jdbcTemplate.update("DELETE FROM account")
        jdbcTemplate.update("DELETE FROM company")
        jdbcTemplate.update("DELETE FROM support_program_source_document")
        jdbcTemplate.update("DELETE FROM support_program")

        supportProgramRepository.upsert(SupportProgramTestHelper.catalogProgram("PBLN_OPEN"))
        supportProgramRepository.upsert(SupportProgramTestHelper.catalogProgram("PBLN_OTHER"))
        val account = accountRepository.createAccount(newAccount("author@company.co.kr"), session("a".repeat(64)))
        companyId = account.company.id
        authorId = account.id
    }

    @Test
    fun storesKoreanFieldsAndCapabilityArrayAndReadsThemBackWithTheCompany() {
        val created = repository.create(companyId, authorId, "BIZINFO", "PBLN_OPEN", RecruitmentTestHelper.draft())

        assertTrue(created.post.id > 0)
        assertEquals(RecruitmentTestHelper.draft(), created.post.draft)
        assertEquals("예시 소프트웨어 주식회사", created.company.companyName)
        assertNull(created.post.closedEarlyAt)
        assertEquals(created, repository.findById(created.post.id))
        assertNull(repository.findById(created.post.id + 1_000))
    }

    @Test
    fun rejectsPostsThatReferenceAnUnknownProgram() {
        assertThrows(DataIntegrityViolationException::class.java) {
            repository.create(companyId, authorId, "BIZINFO", "PBLN_MISSING", RecruitmentTestHelper.draft())
        }
        assertEquals(0, countRows())
    }

    @Test
    fun listsOpenPostsSoonestClosingFirstAndFiltersByProgram() {
        val today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))
        val later = repository.create(companyId, authorId, "BIZINFO", "PBLN_OPEN", RecruitmentTestHelper.draft(closesOn = today.plusDays(20)))
        val soon = repository.create(companyId, authorId, "BIZINFO", "PBLN_OPEN", RecruitmentTestHelper.draft(closesOn = today.plusDays(3)))
        val other = repository.create(companyId, authorId, "BIZINFO", "PBLN_OTHER", RecruitmentTestHelper.draft(closesOn = today))
        val expired = repository.create(companyId, authorId, "BIZINFO", "PBLN_OPEN", RecruitmentTestHelper.draft(closesOn = today.plusDays(1)))
        jdbcTemplate.update("UPDATE recruitment_post SET closes_on = ? WHERE id = ?", today.minusDays(1), expired.post.id)
        val closedEarly = repository.create(companyId, authorId, "BIZINFO", "PBLN_OPEN", RecruitmentTestHelper.draft(closesOn = today.plusDays(2)))
        assertTrue(repository.closeEarly(closedEarly.post.id))
        assertFalse(repository.closeEarly(closedEarly.post.id))
        val hidden = repository.create(companyId, authorId, "BIZINFO", "PBLN_OPEN", RecruitmentTestHelper.draft(closesOn = today.plusDays(2)))
        jdbcTemplate.update("UPDATE recruitment_post SET hidden_at = ?, hidden_reason = ? WHERE id = ?", LocalDateTime.now(), "테스트", hidden.post.id)

        val all = repository.findOpenPage(null, null, 0, 10)
        assertEquals(3, all.totalCount)
        assertEquals(listOf(other.post.id, soon.post.id, later.post.id), all.posts.map { it.post.id })

        val byProgram = repository.findOpenPage("BIZINFO", "PBLN_OPEN", 0, 1)
        assertEquals(2, byProgram.totalCount)
        assertEquals(listOf(soon.post.id), byProgram.posts.map { it.post.id })

        assertThrows(IllegalArgumentException::class.java) { repository.findOpenPage("BIZINFO", null, 0, 10) }
        assertThrows(IllegalArgumentException::class.java) { repository.findOpenPage(null, null, 0, 51) }
    }

    @Test
    fun excludesPostsWhoseProgramIsNoLongerPresentButKeepsTheRow() {
        val created = repository.create(companyId, authorId, "BIZINFO", "PBLN_OPEN", RecruitmentTestHelper.draft(closesOn = LocalDate.now().plusDays(5)))
        jdbcTemplate.update("UPDATE support_program SET is_source_present = FALSE WHERE source_program_id = 'PBLN_OPEN'")

        assertEquals(0, repository.findOpenPage(null, null, 0, 10).totalCount)
        assertNotNull(repository.findById(created.post.id))
        assertEquals(listOf(created.post.id), repository.findByCompanyId(companyId).map { it.post.id })
    }

    @Test
    fun updatesTheDraftWithoutChangingTheLinkedProgramOrAuthor() {
        val created = repository.create(companyId, authorId, "BIZINFO", "PBLN_OPEN", RecruitmentTestHelper.draft())
        val draft = RecruitmentTestHelper.draft(title = "수정된 제목", requiredCapabilities = emptyList())

        val updated = repository.update(created.post.id, draft)

        assertEquals(draft, updated.post.draft)
        assertEquals("PBLN_OPEN", updated.post.sourceProgramId)
        assertEquals(authorId, updated.post.authorAccountId)
        assertTrue(!updated.post.updatedAt.isBefore(created.post.updatedAt))
    }

    private fun countRows(): Int =
        requireNotNull(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recruitment_post", Int::class.java))

    private fun newAccount(email: String) =
        NewAccount(
            email = email,
            passwordHash = "\$2a\$10\$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789",
            termsAgreedAt = LocalDateTime.of(2026, 9, 6, 12, 0),
            company = VerifiedCompany("1248100998", "예시 소프트웨어 주식회사", "계속사업자", LocalDateTime.of(2026, 9, 6, 12, 0)),
        )

    private fun session(tokenHash: String) = NewAccountSession(tokenHash, LocalDateTime.of(2999, 1, 1, 0, 0))
}
