package ai.govbiz.core.supportprogram.facade

import ai.govbiz.core.supportprogram.client.document.SupportProgramAttachment
import ai.govbiz.core.supportprogram.client.document.SupportProgramAttachments
import ai.govbiz.core.supportprogram.client.document.SupportProgramDocumentBlock
import ai.govbiz.core.supportprogram.client.document.SupportProgramDocumentException
import ai.govbiz.core.supportprogram.client.document.SupportProgramDocumentParser
import ai.govbiz.core.supportprogram.client.msit.MsitAttachmentClient
import ai.govbiz.core.supportprogram.domain.SupportProgramSourceDocument
import ai.govbiz.core.supportprogram.facade.exception.SupportProgramSourceDocumentFacadeException
import ai.govbiz.core.supportprogram.helper.SupportProgramTestHelper
import ai.govbiz.core.supportprogram.service.evidence.SupportProgramEvidenceChunker
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class MsitSupportProgramSourceDocumentFacadeTest {
    @Mock private lateinit var attachments: MsitAttachmentClient
    @Mock private lateinit var parser: SupportProgramDocumentParser

    private val clock = Clock.fixed(Instant.parse("2026-09-17T01:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val program = SupportProgramTestHelper.catalogProgram("3186880").program.copy(
        sourceCode = "MSIT", sourceName = "과학기술정보통신부",
        sourceUrl = "https://www.msit.go.kr/bbs/view.do?bbsSeqNo=100&nttSeqNo=3186880",
    )

    @Test
    fun buildsTheEvidenceDocumentFromTheNoticeBeforeApplicationFormsAndSkipsUnreadableFiles() {
        val form = file("붙임2. 연구개발계획서 서식.hwpx", "HWPX", 1)
        val scanned = file("붙임3. 스캔본 공고.pdf", "PDF", 2)
        val notice = file("[공고문] 2026년도 국가과학자지원사업 공모.hwp", "HWP", 3)
        doReturn(SupportProgramAttachments("공고", listOf(form, scanned, notice), emptyList())).`when`(attachments)
            .collect("MSIT", "3186880", program.sourceUrl)
        doReturn(listOf(SupportProgramDocumentBlock("HWPX paragraphs 1-2", "연구개발계획서\n  작성 요령  "))).`when`(parser).parse(form.bytes, "HWPX")
        doThrow(SupportProgramDocumentException(SupportProgramDocumentException.Reason.UNSUPPORTED)).`when`(parser).parse(scanned.bytes, "PDF")
        // 실제 HWP 공고문에서 보이는 글꼴 기호(U+F02B1)는 AI 서비스가 청크를 거부하므로 공백으로 바뀌어야 합니다.
        doReturn(listOf(SupportProgramDocumentBlock("HWP paragraphs 1-3", "□ 신청자격\n󰊱만 55세 이하 산·학·연 연구자\n\n□ 신청기간 2026. 9. 11. ~ 10. 13.")))
            .`when`(parser).parse(notice.bytes, "HWP")

        val document = facade().load(program)

        assertEquals(
            "□ 신청자격\n만 55세 이하 산·학·연 연구자\n□ 신청기간 2026. 9. 11. ~ 10. 13.\n연구개발계획서\n작성 요령",
            document.content,
        )
        assertEquals(program.sourceUrl, document.sourceUrl)
        assertEquals(LocalDateTime.parse("2026-09-17T10:00:00"), document.fetchedAt)
        SupportProgramEvidenceChunker.chunk(document)
    }

    @Test
    fun capsVeryLongNoticesAtALineBoundarySoTheEvidenceChunkerAlwaysAcceptsThem() {
        val notice = file("공고문.hwp", "HWP", 1)
        doReturn(SupportProgramAttachments("공고", listOf(notice), emptyList())).`when`(attachments).collect("MSIT", "3186880", program.sourceUrl)
        val longLines = (1..2_000).joinToString("\n") { "○ 연구개발기관의 자격과 제한 사항 설명 문장 번호 $it 입니다." }
        doReturn(listOf(SupportProgramDocumentBlock("HWP paragraphs", longLines))).`when`(parser).parse(notice.bytes, "HWP")

        val document = facade().load(program)

        assertTrue(document.content.length <= MsitSupportProgramSourceDocumentFacade.MAX_SOURCE_CHARACTERS)
        assertTrue(document.content.endsWith("입니다."))
        assertTrue(SupportProgramEvidenceChunker.chunk(document).size <= 50)
    }

    @Test
    fun reportsAttachmentsWithNoReadableTextAsAnInvalidSourceWithoutGuessing() {
        val scanned = file("공고문.pdf", "PDF", 1)
        doReturn(SupportProgramAttachments("공고", listOf(scanned), emptyList())).`when`(attachments).collect("MSIT", "3186880", program.sourceUrl)
        doThrow(SupportProgramDocumentException(SupportProgramDocumentException.Reason.TOO_LARGE)).`when`(parser).parse(scanned.bytes, "PDF")

        val failure = assertThrows(SupportProgramSourceDocumentFacadeException::class.java) { facade().load(program) }
        assertEquals(SupportProgramSourceDocumentFacadeException.Failure.INVALID_RESPONSE, failure.failure)
    }

    @ParameterizedTest
    @EnumSource(SupportProgramDocumentException.Reason::class)
    fun mapsCollectionFailuresToStableSourceDocumentFailures(reason: SupportProgramDocumentException.Reason) {
        doThrow(SupportProgramDocumentException(reason)).`when`(attachments).collect("MSIT", "3186880", program.sourceUrl)

        val failure = assertThrows(SupportProgramSourceDocumentFacadeException::class.java) { facade().load(program) }

        val expected = when (reason) {
            SupportProgramDocumentException.Reason.UNAVAILABLE -> SupportProgramSourceDocumentFacadeException.Failure.UNAVAILABLE
            SupportProgramDocumentException.Reason.NOT_FOUND -> SupportProgramSourceDocumentFacadeException.Failure.UPSTREAM_ERROR
            else -> SupportProgramSourceDocumentFacadeException.Failure.INVALID_RESPONSE
        }
        assertEquals(expected, failure.failure)
        verifyNoInteractions(parser)
    }

    @Test
    fun refusesNonMsitProgramsBeforeAnyNetworkCall() {
        assertThrows(IllegalStateException::class.java) { facade().load(program.copy(sourceCode = "BIZINFO")) }
        verifyNoInteractions(attachments, parser)
    }

    private fun facade() = MsitSupportProgramSourceDocumentFacade(attachments, parser, clock)

    private fun file(name: String, format: String, marker: Int) =
        SupportProgramAttachment("https://www.msit.go.kr/ssm/file/fileDown.do?atchFileNo=$marker", name, format, byteArrayOf(marker.toByte()))

    @Suppress("unused")
    private fun SupportProgramSourceDocument.lineCount() = content.lines().size
}
