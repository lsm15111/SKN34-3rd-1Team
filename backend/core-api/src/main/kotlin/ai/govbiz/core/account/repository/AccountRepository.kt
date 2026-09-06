package ai.govbiz.core.account.repository

import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.account.domain.AccountCredential
import ai.govbiz.core.account.domain.Company
import ai.govbiz.core.account.domain.NewAccount
import ai.govbiz.core.account.domain.NewAccountSession
import ai.govbiz.core.account.repository.mapper.AccountDbRow
import ai.govbiz.core.account.repository.mapper.AccountMapper
import ai.govbiz.core.account.repository.mapper.AccountSessionDbRow
import ai.govbiz.core.account.repository.mapper.CompanyDbRow
import java.time.Clock
import java.time.LocalDateTime
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/** 계정·기업·로그인 세션을 MySQL에 저장하고 읽습니다. 비밀번호 해시는 로그인 검증 조회에서만 내보냅니다. */
@Repository
class AccountRepository(
    private val accountMapper: AccountMapper,
    @param:Qualifier("seoulClock") private val clock: Clock,
) {

    /**
     * 기업 UPSERT → 계정 INSERT → 첫 세션 INSERT를 하나의 짧은 transaction으로 저장합니다.
     *
     * Bizno 조회와 비밀번호 해시는 이 호출 전에 끝나 있어야 합니다. 이메일 중복은 DB UNIQUE 제약이 막으며
     * 그때 던져지는 [org.springframework.dao.DuplicateKeyException]은 호출한 Service가 변환합니다.
     */
    @Transactional
    fun createAccount(newAccount: NewAccount, session: NewAccountSession): Account {
        accountMapper.upsertCompany(
            CompanyDbRow(
                businessNumber = newAccount.company.businessNumber,
                companyName = newAccount.company.companyName,
                businessStatus = newAccount.company.businessStatus,
                verifiedSource = BIZNO_VERIFIED_SOURCE,
                verifiedAt = newAccount.company.verifiedAt,
            ),
        )
        val company = requireNotNull(
            accountMapper.findCompanyByBusinessNumber(newAccount.company.businessNumber),
        ) { "company row was not created" }

        val accountRow = AccountDbRow(
            email = newAccount.email,
            passwordHash = newAccount.passwordHash,
            companyId = company.id,
            termsAgreedAt = newAccount.termsAgreedAt,
        )
        check(accountMapper.insertAccount(accountRow) == 1) { "account row was not created" }
        insertSession(accountRow.id, session)

        return Account(
            id = accountRow.id,
            email = newAccount.email,
            company = company.toCompany(),
        )
    }

    /** 정규화된(소문자) 이메일로 계정을 조회합니다. */
    fun findByEmail(email: String): Account? =
        accountMapper.findAccountByEmail(email)?.toAccount()

    /** 로그인 검증을 위해 비밀번호 해시를 포함해 조회합니다. */
    fun findCredentialByEmail(email: String): AccountCredential? =
        accountMapper.findAccountByEmail(email)?.let { row ->
            AccountCredential(account = row.toAccount(), passwordHash = row.passwordHash)
        }

    /** 로그인 성공 시 새 세션을 저장하고 같은 계정의 만료 세션을 정리합니다. */
    @Transactional
    fun createSession(accountId: Long, session: NewAccountSession) {
        accountMapper.deleteExpiredSessionsByAccountId(accountId, LocalDateTime.now(clock))
        insertSession(accountId, session)
    }

    /** 만료되지 않은 세션 토큰 해시로 계정을 조회합니다. 없거나 만료됐으면 null입니다. */
    fun findAccountBySessionTokenHash(tokenHash: String): Account? =
        accountMapper.findAccountBySessionTokenHash(tokenHash, LocalDateTime.now(clock))?.toAccount()

    /** 로그아웃한 세션을 삭제합니다. 이미 없으면 false입니다. */
    @Transactional
    fun deleteSessionByTokenHash(tokenHash: String): Boolean =
        accountMapper.deleteSessionByTokenHash(tokenHash) == 1

    private fun insertSession(accountId: Long, session: NewAccountSession) {
        val inserted = accountMapper.insertSession(
            AccountSessionDbRow(
                tokenHash = session.tokenHash,
                accountId = accountId,
                expiresAt = session.expiresAt,
            ),
        )
        check(inserted == 1) { "account session row was not created" }
    }

    private fun AccountDbRow.toAccount(): Account =
        Account(
            id = id,
            email = email,
            company = Company(
                id = companyId,
                businessNumber = companyBusinessNumber,
                companyName = companyName,
                businessStatus = companyBusinessStatus,
            ),
        )

    private fun CompanyDbRow.toCompany(): Company =
        Company(
            id = id,
            businessNumber = businessNumber,
            companyName = companyName,
            businessStatus = businessStatus,
        )

    private companion object {
        const val BIZNO_VERIFIED_SOURCE = "BIZNO"
    }
}
