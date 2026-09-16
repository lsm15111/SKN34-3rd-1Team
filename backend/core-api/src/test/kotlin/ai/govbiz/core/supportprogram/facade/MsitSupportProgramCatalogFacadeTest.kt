package ai.govbiz.core.supportprogram.facade

import ai.govbiz.core.supportprogram.client.msit.MsitClient
import ai.govbiz.core.supportprogram.client.msit.dto.MsitProgramPayload
import ai.govbiz.core.supportprogram.client.msit.exception.MsitClientException
import ai.govbiz.core.supportprogram.domain.SupportProgramStatus
import ai.govbiz.core.supportprogram.facade.exception.SupportProgramCatalogFacadeException
import ai.govbiz.core.supportprogram.service.sync.config.MsitSupportProgramCatalogSyncProperties
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class MsitSupportProgramCatalogFacadeTest {
    @Mock private lateinit var client: MsitClient

    // UTC 2026-09-16 15:30은 서울 기준 2026-09-17입니다.
    private val clock = Clock.fixed(Instant.parse("2026-09-16T15:30:00Z"), ZoneId.of("Asia/Seoul"))

    @Test
    fun requestsOnlyTheLookbackWindowFromTheSeoulDateWithoutInventingAnApplicationPeriod() {
        doReturn(listOf(payload())).`when`(client).fetchPublishedSince(LocalDate.parse("2025-09-17"))
        val result = facade().load().single()
        assertEquals("MSIT", result.program.sourceCode)
        assertEquals(SupportProgramStatus.UNKNOWN, result.program.status)
        assertEquals("2026-09-09", result.sortTimestamp)
        verify(client).fetchPublishedSince(LocalDate.parse("2025-09-17"))

        doReturn(emptyList<MsitProgramPayload>()).`when`(client).fetchPublishedSince(LocalDate.parse("2026-06-17"))
        assertEquals(emptyList<Any>(), facade(Period.ofMonths(3)).load())
    }

    @Test
    fun publishesRecruitmentsButExcludesResultsProcurementHiringAndCancellationPosts() {
        val titles = listOf(
            "2026년도 AI 바우처 지원사업 공고", "2026년도 신규과제 재공모", "모집 기간 연장 공고", "사업 공고 정정",
            "2026년도 치안 R&D 신규과제 선정계획 공고", "2026년도 신규과제 선정 결과 공고", "최종 선정 발표",
            "정책연구용역 입찰공고", "고객만족도 조사 사업자 선정 공고", "공무직 근로자 채용 공고", "모집 공고 취소 안내",
        )
        doReturn(titles.mapIndexed { index, title -> payload(index + 1, title) }).`when`(client)
            .fetchPublishedSince(LocalDate.parse("2025-09-17"))

        assertEquals(titles.take(5), facade().load().map { it.program.title })
    }

    @Test
    fun translatesEveryClientFailureWithoutChangingItsCategory() {
        val failures = listOf(
            MsitClientException.notConfigured(), MsitClientException.upstreamError(503),
            MsitClientException.invalidResponse("invalid"), MsitClientException.unavailable(), MsitClientException.timeout(),
        )
        failures.forEach { failure ->
            doThrow(failure).`when`(client).fetchPublishedSince(LocalDate.parse("2025-09-17"))
            val error = assertThrows(SupportProgramCatalogFacadeException::class.java) { facade().load() }
            assertEquals(failure.failure.name, error.failure.name)
            assertSame(failure, error.cause)
        }
    }

    @Test
    fun mappingFailureCannotPublishAPartialSnapshotEvenForExcludedPosts() {
        doReturn(listOf(payload(), payload(2, "선정결과 공고").copy(sourceUrl = "not a URL"))).`when`(client)
            .fetchPublishedSince(LocalDate.parse("2025-09-17"))
        val failure = assertThrows(SupportProgramCatalogFacadeException::class.java) { facade().load() }
        assertEquals(SupportProgramCatalogFacadeException.Failure.INVALID_RESPONSE, failure.failure)
    }

    private fun facade(lookback: Period = Period.ofMonths(12)) =
        MsitSupportProgramCatalogFacade(client, MsitSupportProgramCatalogSyncProperties(lookback = lookback), clock)

    private fun payload(id: Int = 3186878, title: String = "사업 공고") = MsitProgramPayload(
        title = title, organization = "과학기술정보통신부", publishedAt = "2026-09-09",
        sourceUrl = "https://www.msit.go.kr/bbs/view.do?bbsSeqNo=100&nttSeqNo=$id",
    )
}
