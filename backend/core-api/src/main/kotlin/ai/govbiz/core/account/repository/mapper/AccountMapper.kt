package ai.govbiz.core.account.repository.mapper

import java.time.LocalDateTime
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

/** 계정·로그인 세션 MySQL SQL을 실행하는 MyBatis Mapper입니다. 삭제된 계정은 모든 조회에서 제외합니다. */
@Mapper
interface AccountMapper {

    fun insertAccount(row: AccountDbRow): Int

    fun findAccountById(@Param("id") id: Long): AccountDbRow?

    fun findAccountByEmail(@Param("email") email: String): AccountDbRow?

    fun insertSession(row: AccountSessionDbRow): Int

    fun findSessionByTokenHash(@Param("tokenHash") tokenHash: String): AccountSessionDbRow?

    fun updateSessionLastUsedAt(
        @Param("tokenHash") tokenHash: String,
        @Param("lastUsedAt") lastUsedAt: LocalDateTime,
    ): Int

    fun deleteSessionByTokenHash(@Param("tokenHash") tokenHash: String): Int

    fun deleteExpiredSessionsByAccountId(
        @Param("accountId") accountId: Long,
        @Param("now") now: LocalDateTime,
    ): Int

    fun updateEmailVerifiedAtIfNull(
        @Param("accountId") accountId: Long,
        @Param("verifiedAt") verifiedAt: LocalDateTime,
    ): Int

    fun findSocialIdentity(
        @Param("provider") provider: String,
        @Param("providerUserId") providerUserId: String,
    ): AccountSocialIdentityDbRow?

    fun insertSocialIdentity(row: AccountSocialIdentityDbRow): Int
}
