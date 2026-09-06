package ai.govbiz.core.account.repository

import ai.govbiz.core._common.test.MySqlTestContainerConfig
import ai.govbiz.core.account.domain.AccountRole
import ai.govbiz.core.account.domain.NewAccount
import ai.govbiz.core.account.domain.NewAccountSession
import ai.govbiz.core.account.domain.VerifiedCompany
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
class AccountRepositoryIntegrationTest {

    @Autowired
    private lateinit var repository: AccountRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun deleteAccounts() {
        jdbcTemplate.update("DELETE FROM recruitment_post")
        jdbcTemplate.update("DELETE FROM account_session")
        jdbcTemplate.update("DELETE FROM account")
        jdbcTemplate.update("DELETE FROM company")
    }

    @Test
    fun createsAccountWithKoreanCompanyAndReadsItBackByEmailAndCredential() {
        val newAccount = newAccount(
            email = "manager@company.co.kr",
            companyName = "예시 \"소프트웨어\" 주식회사 & 파트너",
            businessStatus = "계속사업자",
        )

        val created = repository.createAccount(newAccount, session(TOKEN_HASH_A, FUTURE))

        assertTrue(created.id > 0)
        assertTrue(created.company.id > 0)
        assertEquals("manager@company.co.kr", created.email)
        assertEquals("예시 \"소프트웨어\" 주식회사 & 파트너", created.company.companyName)
        assertEquals("계속사업자", created.company.businessStatus)
        assertEquals(created, repository.findByEmail("manager@company.co.kr"))

        val credential = requireNotNull(repository.findCredentialByEmail("manager@company.co.kr"))
        assertEquals(created, credential.account)
        assertEquals(PASSWORD_HASH, credential.passwordHash)
        assertNull(repository.findByEmail("unknown@company.co.kr"))
        assertEquals("BIZNO", jdbcTemplate.queryForObject("SELECT verified_source FROM company", String::class.java))
    }

    @Test
    fun reusesTheCompanyRowAndRefreshesItsNameAndStatusForASecondAccount() {
        val first = repository.createAccount(
            newAccount(email = "first@company.co.kr", companyName = "이전 상호", businessStatus = "계속사업자"),
            session(TOKEN_HASH_A, FUTURE),
        )

        val second = repository.createAccount(
            newAccount(email = "second@company.co.kr", companyName = "변경된 상호", businessStatus = "휴업자"),
            session(TOKEN_HASH_B, FUTURE),
        )

        assertEquals(first.company.id, second.company.id)
        assertEquals(1, countRows("company"))
        assertEquals(2, countRows("account"))
        val refreshedFirst = requireNotNull(repository.findByEmail("first@company.co.kr"))
        assertEquals("변경된 상호", refreshedFirst.company.companyName)
        assertEquals("휴업자", refreshedFirst.company.businessStatus)
    }

    @Test
    fun rejectsDuplicateEmailAndRollsBackTheSessionInsert() {
        repository.createAccount(newAccount(email = "dup@company.co.kr"), session(TOKEN_HASH_A, FUTURE))

        assertThrows(DuplicateKeyException::class.java) {
            repository.createAccount(
                newAccount(email = "dup@company.co.kr", businessNumber = "2208162517", companyName = "다른 기업"),
                session(TOKEN_HASH_B, FUTURE),
            )
        }

        assertEquals(1, countRows("account"))
        assertEquals(1, countRows("account_session"))
        assertNull(repository.findAccountBySessionTokenHash(TOKEN_HASH_B))
    }

    @Test
    fun treatsEmailUniquenessCaseInsensitivelyAtTheDatabase() {
        repository.createAccount(newAccount(email = "case@company.co.kr"), session(TOKEN_HASH_A, FUTURE))

        assertThrows(DuplicateKeyException::class.java) {
            jdbcTemplate.update(
                "INSERT INTO account (email, password_hash, company_id, terms_agreed_at) " +
                    "SELECT 'CASE@company.co.kr', password_hash, company_id, terms_agreed_at FROM account",
            )
        }
    }

    @Test
    fun resolvesOnlyUnexpiredSessionsAndDeletesThemOnLogout() {
        val account = repository.createAccount(newAccount(email = "session@company.co.kr"), session(TOKEN_HASH_A, FUTURE))
        repository.createSession(account.id, session(TOKEN_HASH_B, PAST))

        assertEquals(account, repository.findAccountBySessionTokenHash(TOKEN_HASH_A))
        assertNull(repository.findAccountBySessionTokenHash(TOKEN_HASH_B))
        assertNull(repository.findAccountBySessionTokenHash(TOKEN_HASH_C))

        assertTrue(repository.deleteSessionByTokenHash(TOKEN_HASH_A))
        assertFalse(repository.deleteSessionByTokenHash(TOKEN_HASH_A))
        assertNull(repository.findAccountBySessionTokenHash(TOKEN_HASH_A))
    }

    @Test
    fun createSessionRemovesExpiredSessionsOfTheSameAccountOnly() {
        val account = repository.createAccount(newAccount(email = "expiry@company.co.kr"), session(TOKEN_HASH_A, PAST))
        val other = repository.createAccount(
            newAccount(email = "other@company.co.kr", businessNumber = "2208162517", companyName = "다른 기업"),
            session(TOKEN_HASH_B, PAST),
        )

        repository.createSession(account.id, session(TOKEN_HASH_C, FUTURE))

        assertEquals(0, countSessions(TOKEN_HASH_A))
        assertEquals(1, countSessions(TOKEN_HASH_B))
        assertEquals(other, repository.findByEmail("other@company.co.kr"))
        assertEquals(account, repository.findAccountBySessionTokenHash(TOKEN_HASH_C))
    }

    @Test
    fun deletingAnAccountCascadesToItsSessionsButKeepsTheCompany() {
        val account = repository.createAccount(newAccount(email = "cascade@company.co.kr"), session(TOKEN_HASH_A, FUTURE))

        jdbcTemplate.update("DELETE FROM account WHERE id = ?", account.id)

        assertEquals(0, countRows("account_session"))
        assertEquals(1, countRows("company"))
    }

    @Test
    fun storesNewAccountsAsUsersAndReadsAPromotedAdminRole() {
        val created = repository.createAccount(newAccount(email = "role@company.co.kr"), session(TOKEN_HASH_A, FUTURE))

        assertEquals(AccountRole.USER, created.role)
        assertFalse(created.isAdmin)
        assertEquals(created, repository.findById(created.id))
        // 가입 시각은 DB 서버 시간대가 아니라 서울 기준 앱 시계로 기록됩니다.
        val seoulNow = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"))
        assertTrue(java.time.Duration.between(created.createdAt, seoulNow).abs().toMinutes() < 5)

        jdbcTemplate.update("UPDATE account SET role = 'ADMIN' WHERE id = ?", created.id)

        assertEquals(AccountRole.ADMIN, requireNotNull(repository.findById(created.id)).role)
        assertTrue(requireNotNull(repository.findAccountBySessionTokenHash(TOKEN_HASH_A)).isAdmin)
        assertNull(repository.findById(created.id + 1_000))
    }

    @Test
    fun pagesAccountsNewestFirstAndFiltersByEmailKeyword() {
        repository.createAccount(newAccount(email = "alpha@company.co.kr"), session(TOKEN_HASH_A, FUTURE))
        repository.createAccount(
            newAccount(email = "beta@other.co.kr", businessNumber = "2208162517", companyName = "다른 기업"),
            session(TOKEN_HASH_B, FUTURE),
        )
        repository.createAccount(newAccount(email = "gamma@company.co.kr"), session(TOKEN_HASH_C, FUTURE))

        val firstPage = repository.findPage(emailKeyword = null, page = 0, size = 2)
        assertEquals(3, firstPage.totalCount)
        assertEquals(listOf("gamma@company.co.kr", "beta@other.co.kr"), firstPage.accounts.map { it.email })

        val secondPage = repository.findPage(emailKeyword = "  ", page = 1, size = 2)
        assertEquals(listOf("alpha@company.co.kr"), secondPage.accounts.map { it.email })

        val filtered = repository.findPage(emailKeyword = "COMPANY.co", page = 0, size = 10)
        assertEquals(2, filtered.totalCount)
        assertEquals(listOf("gamma@company.co.kr", "alpha@company.co.kr"), filtered.accounts.map { it.email })
        assertEquals("다른 기업", requireNotNull(repository.findByEmail("beta@other.co.kr")).company.companyName)

        assertThrows(IllegalArgumentException::class.java) { repository.findPage(null, 0, 101) }
    }

    @Test
    fun revokesEverySessionOfOneAccountOnly() {
        val target = repository.createAccount(newAccount(email = "target@company.co.kr"), session(TOKEN_HASH_A, FUTURE))
        repository.createSession(target.id, session(TOKEN_HASH_B, FUTURE))
        val other = repository.createAccount(
            newAccount(email = "other@company.co.kr", businessNumber = "2208162517", companyName = "다른 기업"),
            session(TOKEN_HASH_C, FUTURE),
        )

        assertEquals(2, repository.deleteSessionsByAccountId(target.id))

        assertNull(repository.findAccountBySessionTokenHash(TOKEN_HASH_A))
        assertNull(repository.findAccountBySessionTokenHash(TOKEN_HASH_B))
        assertEquals(other, repository.findAccountBySessionTokenHash(TOKEN_HASH_C))
        assertEquals(0, repository.deleteSessionsByAccountId(target.id))
    }

    @Test
    fun rejectsTokenHashesThatAreNotSha256Hex() {
        assertThrows(IllegalArgumentException::class.java) {
            NewAccountSession("not-a-hash", FUTURE)
        }
    }

    private fun countRows(table: String): Int =
        requireNotNull(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java))

    private fun countSessions(tokenHash: String): Int =
        requireNotNull(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_session WHERE token_hash = ?",
                Int::class.java,
                tokenHash,
            ),
        )

    private fun newAccount(
        email: String,
        businessNumber: String = "1248100998",
        companyName: String = "예시 소프트웨어 주식회사",
        businessStatus: String = "계속사업자",
    ): NewAccount =
        NewAccount(
            email = email,
            passwordHash = PASSWORD_HASH,
            termsAgreedAt = LocalDateTime.of(2026, 9, 6, 12, 0),
            company = VerifiedCompany(
                businessNumber = businessNumber,
                companyName = companyName,
                businessStatus = businessStatus,
                verifiedAt = LocalDateTime.of(2026, 9, 6, 12, 0),
            ),
        )

    private fun session(tokenHash: String, expiresAt: LocalDateTime) = NewAccountSession(tokenHash, expiresAt)

    private companion object {
        const val PASSWORD_HASH = "\$2a\$10\$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val TOKEN_HASH_A = "a".repeat(64)
        val TOKEN_HASH_B = "b".repeat(64)
        val TOKEN_HASH_C = "c".repeat(64)
        val FUTURE: LocalDateTime = LocalDateTime.of(2999, 1, 1, 0, 0)
        val PAST: LocalDateTime = LocalDateTime.of(2000, 1, 1, 0, 0)
    }
}
