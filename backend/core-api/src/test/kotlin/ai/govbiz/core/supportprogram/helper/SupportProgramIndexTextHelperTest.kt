package ai.govbiz.core.supportprogram.helper

import ai.govbiz.core.supportprogram.client.msit.dto.MsitProgramPayload
import ai.govbiz.core.supportprogram.client.msit.mapper.MsitProgramMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SupportProgramIndexTextHelperTest {
    @Test
    fun indexesOnlyTheTitleAndDepartmentForMsitInsteadOfTheSharedMissingDataNotice() {
        val msit = MsitProgramMapper.mapValidated(listOf(MsitProgramPayload(
            title = "2026년도 AI 바우처 지원사업 공고", organization = "인공지능기반정책과", publishedAt = "2026-09-09",
            sourceUrl = "https://www.msit.go.kr/bbs/view.do?bbsSeqNo=100&nttSeqNo=3186878",
        ))).single()

        assertEquals("제목: 2026년도 AI 바우처 지원사업 공고\n기관: 인공지능기반정책과", SupportProgramIndexTextHelper.buildText(msit))

        val other = msit.copy(program = msit.program.copy(sourceCode = "BIZINFO"))
        val otherText = SupportProgramIndexTextHelper.buildText(other)
        assertTrue(otherText.contains("신청기간: 정보 없음"))
        assertTrue(otherText.contains("내용: ${msit.program.summary}"))
    }
}
