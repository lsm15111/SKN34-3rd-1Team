package ai.govbiz.core.account.repository.mapper

import java.time.LocalDateTime
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

/** 계정·기업·로그인 세션 MySQL SQL을 실행하는 MyBatis Mapper입니다. */
@Mapper
interface AccountMapper {

    fun upsertCompany(row: CompanyDbRow): Int

    fun findCompanyByBusinessNumber(@Param("businessNumber") businessNumber: String): CompanyDbRow?

    fun insertAccount(row: AccountDbRow): Int

    fun findAccountById(@Param("id") id: Long): AccountDbRow?

    fun findAccountByEmail(@Param("email") email: String): AccountDbRow?

    fun findAccountBySessionTokenHash(
        @Param("tokenHash") tokenHash: String,
        @Param("now") now: LocalDateTime,
    ): AccountDbRow?

    fun findAccountPage(
        @Param("emailKeyword") emailKeyword: String?,
        @Param("offset") offset: Int,
        @Param("limit") limit: Int,
    ): List<AccountDbRow>

    fun countAccounts(@Param("emailKeyword") emailKeyword: String?): Long

    fun insertSession(row: AccountSessionDbRow): Int

    fun deleteSessionByTokenHash(@Param("tokenHash") tokenHash: String): Int

    fun deleteSessionsByAccountId(@Param("accountId") accountId: Long): Int

    fun deleteExpiredSessionsByAccountId(
        @Param("accountId") accountId: Long,
        @Param("now") now: LocalDateTime,
    ): Int
}
