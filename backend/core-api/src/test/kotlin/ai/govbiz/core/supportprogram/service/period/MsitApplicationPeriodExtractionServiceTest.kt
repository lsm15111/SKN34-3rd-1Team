package ai.govbiz.core.supportprogram.service.period

import ai.govbiz.core.supportprogram.client.document.SupportProgramAttachment
import ai.govbiz.core.supportprogram.client.document.SupportProgramAttachments
import ai.govbiz.core.supportprogram.client.document.SupportProgramDocumentBlock
import ai.govbiz.core.supportprogram.client.document.SupportProgramDocumentException
import ai.govbiz.core.supportprogram.client.document.SupportProgramDocumentParser
import ai.govbiz.core.supportprogram.client.msit.MsitAttachmentClient
import ai.govbiz.core.supportprogram.helper.MsitNoticeExtraction
import ai.govbiz.core.supportprogram.helper.SupportProgramApplicationPeriod
import ai.govbiz.core.supportprogram.helper.SupportProgramNoticeSections
import ai.govbiz.core.supportprogram.repository.SupportProgramPeriodExtractionRepository
import ai.govbiz.core.supportprogram.repository.SupportProgramPeriodExtractionRepository.Status
import ai.govbiz.core.supportprogram.repository.mapper.SupportProgramPeriodExtractionTargetDbRow
import ai.govbiz.core.supportprogram.service.period.config.MsitApplicationPeriodExtractionProperties
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class MsitApplicationPeriodExtractionServiceTest {
    @Mock private lateinit var attachments: MsitAttachmentClient
    @Mock private lateinit var parser: SupportProgramDocumentParser
    @Mock private lateinit var repository: SupportProgramPeriodExtractionRepository

    private val clock = Clock.fixed(Instant.parse("2026-09-17T01:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val now = LocalDateTime.parse("2026-09-17T10:00:00")
    private val properties = MsitApplicationPeriodExtractionProperties(batchSize = 3)

    @Test
    fun savesThePeriodFoundInAnyReadableOfficialAttachmentAndSkipsUnreadableOnes() {
        doReturn(listOf(target("1"))).`when`(repository).findDueTargets("MSIT", now, 3)
        doReturn(files("공고문.pdf", "신청서.hwp")).`when`(attachments).collect("MSIT", "1", URL)
        doThrow(SupportProgramDocumentException(SupportProgramDocumentException.Reason.UNSUPPORTED)).`when`(parser).parse(BYTES_PDF, "PDF")
        doReturn(listOf(
            SupportProgramDocumentBlock("HWP paragraphs 1-3", "□ 사업목적 ᄋ 국내 AI 보안 유망기업의 시제품 개발부터 사업화까지 지원"),
            SupportProgramDocumentBlock("HWP paragraphs 4-6", "□ 접수기간 : 2026. 9. 1.(화) ~ 2026. 10. 14.(수) 18:00까지"),
        )).`when`(parser).parse(BYTES_HWP, "HWP")

        assertEquals(1, service().runBatch())

        verify(repository).saveExtraction(
            "MSIT", "1",
            MsitNoticeExtraction(
                SupportProgramApplicationPeriod(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-14"), "접수기간 : 2026. 9. 1.(화) ~ 2026. 10. 14.(수) 18:00"),
                SupportProgramNoticeSections("국내 AI 보안 유망기업의 시제품 개발부터 사업화까지 지원", null),
            ),
            1, now, now.plus(properties.extractedRecheckAfter),
        )
    }

    @Test
    fun recordsMissingPeriodsAndDocumentsWithoutGuessing() {
        doReturn(listOf(target("1"), target("2"))).`when`(repository).findDueTargets("MSIT", now, 3)
        doReturn(files("공고문.pdf")).`when`(attachments).collect("MSIT", "1", URL)
        doReturn(listOf(SupportProgramDocumentBlock("PDF page 1 part 1", "연구기간 2026. 10. 1. ~ 2027. 9. 30. □ 지원대상 : 국내 대학 및 정부출연연구기관 컨소시엄")))
            .`when`(parser).parse(BYTES_PDF, "PDF")
        doThrow(SupportProgramDocumentException(SupportProgramDocumentException.Reason.UNSUPPORTED)).`when`(attachments).collect("MSIT", "2", URL)

        assertEquals(2, service().runBatch())

        verify(repository).reapplyUnapplied("MSIT")
        verify(repository).findDueTargets("MSIT", now, 3)
        verify(repository).saveExtraction(
            "MSIT", "1", MsitNoticeExtraction(null, SupportProgramNoticeSections(null, "국내 대학 및 정부출연연구기관 컨소시엄")),
            1, now, now.plus(properties.recheckAfter),
        )
        verify(repository).saveFailure("MSIT", "2", Status.DOCUMENT_UNAVAILABLE, "UNSUPPORTED", 1, now, now.plus(properties.recheckAfter))
        verifyNoMoreInteractions(repository)
    }

    @Test
    fun retriesTransientFailuresSoonThenBacksOffToTheRecheckInterval() {
        doReturn(listOf(target("1", attempts = 0), target("2", attempts = 2))).`when`(repository).findDueTargets("MSIT", now, 3)
        doThrow(SupportProgramDocumentException(SupportProgramDocumentException.Reason.UNAVAILABLE)).`when`(attachments).collect("MSIT", "1", URL)
        doThrow(SupportProgramDocumentException(SupportProgramDocumentException.Reason.INVALID)).`when`(attachments).collect("MSIT", "2", URL)

        service().runBatch()

        verify(repository).saveFailure("MSIT", "1", Status.RETRY_WAITING, "UNAVAILABLE", 1, now, now.plus(Duration.ofHours(1)))
        verify(repository).saveFailure("MSIT", "2", Status.RETRY_WAITING, "INVALID", 3, now, now.plus(properties.recheckAfter))
    }

    @Test
    fun databaseFailuresPropagateSoTheWorkerRetriesOnItsNextTick() {
        doThrow(IllegalStateException("db down")).`when`(repository).findDueTargets("MSIT", now, 3)
        assertThrows(IllegalStateException::class.java) { service().runBatch() }
    }

    @Test
    fun rejectsUnsafeBatchSizesAndNonPositiveIntervals() {
        for (size in listOf(0, 51)) {
            assertThrows(IllegalArgumentException::class.java) { MsitApplicationPeriodExtractionProperties(batchSize = size) }
        }
        assertThrows(IllegalArgumentException::class.java) { MsitApplicationPeriodExtractionProperties(delay = Duration.ZERO) }
        assertThrows(IllegalArgumentException::class.java) { MsitApplicationPeriodExtractionProperties(recheckAfter = Duration.ofSeconds(-1)) }
        assertEquals(false, MsitApplicationPeriodExtractionProperties().enabled)
    }

    private fun service() = MsitApplicationPeriodExtractionService(attachments, parser, repository, properties, MsitCatalogWriteGuard(), clock)

    private fun target(id: String, attempts: Int = 0) = SupportProgramPeriodExtractionTargetDbRow(id, URL, "2026-09-01", attempts)

    private fun files(vararg names: String) = SupportProgramAttachments(
        programTitle = "공고", warnings = emptyList(),
        files = names.map { name ->
            if (name.endsWith(".pdf")) SupportProgramAttachment("https://www.msit.go.kr/file/1", name, "PDF", BYTES_PDF)
            else SupportProgramAttachment("https://www.msit.go.kr/file/2", name, "HWP", BYTES_HWP)
        },
    )

    private companion object {
        const val URL = "https://www.msit.go.kr/bbs/view.do?bbsSeqNo=100&nttSeqNo=1"
        val BYTES_PDF = byteArrayOf(1)
        val BYTES_HWP = byteArrayOf(2)
    }
}
