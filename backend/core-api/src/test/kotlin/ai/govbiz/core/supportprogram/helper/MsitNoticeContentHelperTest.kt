package ai.govbiz.core.supportprogram.helper

import ai.govbiz.core.supportprogram.client.msit.dto.MsitProgramPayload
import ai.govbiz.core.supportprogram.client.msit.mapper.MsitProgramMapper
import ai.govbiz.core.supportprogram.domain.CatalogSupportProgram
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MsitNoticeContentHelperTest {
    private val base: CatalogSupportProgram = MsitProgramMapper.mapValidated(listOf(MsitProgramPayload(
        title = "2026년도 AI 반도체 원천기술 개발사업 신규과제 공모", organization = "반도체과", publishedAt = "2026-09-01",
        sourceUrl = "https://www.msit.go.kr/bbs/view.do?bbsSeqNo=100&nttSeqNo=3186900",
    ))).single()
    private val period = SupportProgramApplicationPeriod(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-14"), "신청기간 2026. 9. 1. ~ 10. 14.")

    @Test
    fun mapperStartsFromTheSharedMissingNoticeThatIsNeverIndexed() {
        assertEquals(MsitNoticeContentHelper.MISSING_CONTENT_SUMMARY, base.program.summary)
        assertEquals(MsitNoticeContentHelper.MISSING_TARGET, base.program.targetDescription)
        assertFalse(MsitNoticeContentHelper.isExcerptSummary(base.program.summary))
        assertFalse(MsitNoticeContentHelper.isExcerptSummary(MsitNoticeContentHelper.PERIOD_ONLY_SUMMARY))
    }

    @Test
    fun replacesHwpPrivateUseGlyphsAndFormatCharactersThatTheAiServiceRejects() {
        val dirty = "󰊱 신청자격​ 국내  대학 및\t기업\n다음 줄"
        assertEquals("  신청자격  국내 대학 및\t기업\n다음 줄".replace("  ", " "), MsitNoticeContentHelper.sanitize(dirty))
        val applied = MsitNoticeContentHelper.apply(base.program, MsitNoticeExtraction(null, SupportProgramNoticeSections(null, "󰊱 국내 기업 󰊲")))
        assertEquals("국내 기업", applied.targetDescription)
        assertFalse(Regex("[\\p{C}&&[^\\n\\t]]").containsMatchIn(applied.summary + applied.targetDescription))
    }

    @Test
    fun periodOnlyExtractionChangesDatesButKeepsTheSearchIndexTextIdentical() {
        val applied = base.copy(program = MsitNoticeContentHelper.apply(base.program, MsitNoticeExtraction(period, SupportProgramNoticeSections(null, null))))

        assertEquals("2026.09.01 ~ 2026.10.14", applied.program.applicationPeriod)
        assertEquals(LocalDate.parse("2026-10-14"), applied.program.applicationEndDate)
        assertEquals(MsitNoticeContentHelper.PERIOD_ONLY_SUMMARY, applied.program.summary)
        // 추출 워커가 동기화 전에 기간을 바꿔도 색인 해시가 달라지지 않아야 AI 검색이 색인 불일치로 실패하지 않습니다.
        assertEquals(SupportProgramIndexTextHelper.buildText(base), SupportProgramIndexTextHelper.buildText(applied))
    }

    @Test
    fun excerptsReplaceTheNoticesAndEnterTheIndexTextWithAnAutomaticExcerptWarning() {
        val sections = SupportProgramNoticeSections("AI 반도체 원천기술 확보 및 생태계 조성", "국내 대학, 정부출연연구기관, 기업부설연구소")
        val applied = base.copy(program = MsitNoticeContentHelper.apply(base.program, MsitNoticeExtraction(null, sections)))

        assertTrue(applied.program.summary.startsWith("AI 반도체 원천기술 확보 및 생태계 조성\n\n※ 공식 첨부 공고문에서 자동으로 발췌"))
        assertTrue(MsitNoticeContentHelper.isExcerptSummary(applied.program.summary))
        assertEquals("국내 대학, 정부출연연구기관, 기업부설연구소", applied.program.targetDescription)
        assertEquals(null, applied.program.applicationEndDate)
        val text = SupportProgramIndexTextHelper.buildText(applied)
        assertTrue(text.contains("지원대상: 국내 대학, 정부출연연구기관, 기업부설연구소"))
        assertTrue(text.contains("내용: AI 반도체 원천기술 확보 및 생태계 조성"))
        assertFalse(text.contains("신청기간"))
        assertNotEquals(SupportProgramIndexTextHelper.buildText(base), text)
    }
}
