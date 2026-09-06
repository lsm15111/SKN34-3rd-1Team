package ai.govbiz.core.account.helper

import ai.govbiz.core.account.config.AccountSessionProperties
import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.account.domain.Company
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.mockito.Mockito

/** account 기능 테스트가 함께 쓰는 고정 시계·설정·계정 예시입니다. */
object AccountTestHelper {

    val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

    /** 2026-09-06T12:00:00+09:00 */
    val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-06T03:00:00Z"), SEOUL)

    val SESSION_TTL: Duration = Duration.ofDays(30)

    fun sessionProperties(): AccountSessionProperties = AccountSessionProperties(SESSION_TTL)

    fun company(id: Long = 1L): Company =
        Company(
            id = id,
            businessNumber = "1248100998",
            companyName = "삼성전자(주)",
            businessStatus = "계속사업자",
        )

    fun account(id: Long = 1L, email: String = "manager@company.co.kr"): Account =
        Account(id = id, email = email, company = company())

    /** Kotlin의 non-null 인자에 Mockito matcher를 넘길 수 있게 null을 T로 다룹니다. */
    @Suppress("UNCHECKED_CAST")
    fun <T> anyValue(): T = Mockito.any<T>() as T
}
