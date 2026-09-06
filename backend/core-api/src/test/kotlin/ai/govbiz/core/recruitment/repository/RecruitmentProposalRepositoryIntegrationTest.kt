package ai.govbiz.core.recruitment.repository

import ai.govbiz.core._common.test.MySqlTestContainerConfig
import ai.govbiz.core.account.domain.NewAccount
import ai.govbiz.core.account.domain.NewAccountSession
import ai.govbiz.core.account.domain.VerifiedCompany
import ai.govbiz.core.account.repository.AccountRepository
import ai.govbiz.core.recruitment.domain.ProposalDecision
import ai.govbiz.core.recruitment.helper.RecruitmentTestHelper
import ai.govbiz.core.supportprogram.helper.SupportProgramTestHelper
import ai.govbiz.core.supportprogram.repository.SupportProgramRepository
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
import org.springframework.dao.DuplicateKeyException
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
class RecruitmentProposalRepositoryIntegrationTest {

    @Autowired
    private lateinit var repository: RecruitmentProposalRepository

    @Autowired
    private lateinit var postRepository: RecruitmentPostRepository

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var supportProgramRepository: SupportProgramRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private var postId = 0L
    private var otherPostId = 0L
    private var proposerCompanyId = 0L
    private var proposerId = 0L

    @BeforeEach
    fun resetTables() {
        jdbcTemplate.update("DELETE FROM recruitment_proposal")
        jdbcTemplate.update("DELETE FROM recruitment_post")
        jdbcTemplate.update("DELETE FROM account_session")
        jdbcTemplate.update("DELETE FROM account")
        jdbcTemplate.update("DELETE FROM company")
        jdbcTemplate.update("DELETE FROM support_program_source_document")
        jdbcTemplate.update("DELETE FROM support_program")

        supportProgramRepository.upsert(SupportProgramTestHelper.catalogProgram("PBLN_OPEN"))
        val author = accountRepository.createAccount(
            newAccount("author@company.co.kr", "1248100998", "예시 소프트웨어 주식회사"),
            session("a".repeat(64)),
        )
        val proposer = accountRepository.createAccount(
            newAccount("partner@vision.co.kr", "2208162517", "비전솔루션"),
            session("b".repeat(64)),
        )
        proposerCompanyId = proposer.company.id
        proposerId = proposer.id
        postId = postRepository.create(author.company.id, author.id, "BIZINFO", "PBLN_OPEN", RecruitmentTestHelper.draft()).post.id
        otherPostId = postRepository.create(author.company.id, author.id, "BIZINFO", "PBLN_OPEN", RecruitmentTestHelper.draft(title = "두 번째 글")).post.id
    }

    @Test
    fun storesAProposalWithTheProposerCompanyAndEmailAndAllowsOnlyOnePerCompanyAndPost() {
        val created = repository.create(postId, proposerCompanyId, proposerId, "라벨링 운영 경험이 있습니다.")

        assertTrue(created.proposal.id > 0)
        assertEquals(ProposalDecision.PENDING, created.proposal.decision)
        assertNull(created.proposal.decidedAt)
        assertEquals("비전솔루션", created.company.companyName)
        assertEquals("partner@vision.co.kr", created.proposerEmail)
        assertEquals(created, repository.findById(created.proposal.id))

        assertThrows(DuplicateKeyException::class.java) {
            repository.create(postId, proposerCompanyId, proposerId, "다시 제안합니다.")
        }
        assertEquals(1, repository.findByPostId(postId).size)
        assertNotNull(repository.create(otherPostId, proposerCompanyId, proposerId, "다른 글에는 보낼 수 있습니다."))
    }

    @Test
    fun decidesAPendingProposalOnlyOnce() {
        val created = repository.create(postId, proposerCompanyId, proposerId, "라벨링 운영 경험이 있습니다.")

        assertTrue(repository.decide(created.proposal.id, ProposalDecision.ACCEPTED))
        assertFalse(repository.decide(created.proposal.id, ProposalDecision.DECLINED))
        assertFalse(repository.decide(created.proposal.id + 1_000, ProposalDecision.WITHDRAWN))
        assertThrows(IllegalArgumentException::class.java) { repository.decide(created.proposal.id, ProposalDecision.PENDING) }

        val decided = requireNotNull(repository.findById(created.proposal.id))
        assertEquals(ProposalDecision.ACCEPTED, decided.proposal.decision)
        assertNotNull(decided.proposal.decidedAt)
    }

    @Test
    fun countsProposalsPerPostWithoutWithdrawnOnesAndFindsTheCompanysOwn() {
        val first = repository.create(postId, proposerCompanyId, proposerId, "첫 제안")
        val second = repository.create(otherPostId, proposerCompanyId, proposerId, "둘째 제안")
        assertTrue(repository.decide(second.proposal.id, ProposalDecision.WITHDRAWN))

        assertEquals(mapOf(postId to 1), repository.countByPostIds(listOf(postId, otherPostId)))
        assertEquals(emptyMap<Long, Int>(), repository.countByPostIds(emptyList()))

        val mine = repository.findByPostIdsAndCompanyId(listOf(postId, otherPostId, otherPostId + 1_000), proposerCompanyId)
        assertEquals(setOf(postId, otherPostId), mine.keys)
        assertEquals(first.proposal.id, mine.getValue(postId).proposal.id)
        assertEquals(emptyMap<Long, StoredRecruitmentProposal>(), repository.findByPostIdsAndCompanyId(listOf(postId), proposerCompanyId + 1_000))
        assertEquals(listOf(second.proposal.id, first.proposal.id), repository.findByCompanyId(proposerCompanyId).map { it.proposal.id })
    }

    @Test
    fun deletesProposalsTogetherWithTheirPost() {
        val created = repository.create(postId, proposerCompanyId, proposerId, "첫 제안")

        jdbcTemplate.update("DELETE FROM recruitment_post WHERE id = ?", postId)

        assertNull(repository.findById(created.proposal.id))
    }

    private fun newAccount(email: String, businessNumber: String, companyName: String) =
        NewAccount(
            email = email,
            passwordHash = "\$2a\$10\$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789",
            termsAgreedAt = LocalDateTime.of(2026, 9, 6, 12, 0),
            company = VerifiedCompany(businessNumber, companyName, "계속사업자", LocalDateTime.of(2026, 9, 6, 12, 0)),
        )

    private fun session(tokenHash: String) = NewAccountSession(tokenHash, LocalDateTime.of(2999, 1, 1, 0, 0))
}
