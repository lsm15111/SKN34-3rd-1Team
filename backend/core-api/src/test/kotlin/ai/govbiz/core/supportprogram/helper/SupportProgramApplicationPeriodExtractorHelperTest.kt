package ai.govbiz.core.supportprogram.helper

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Test

/** 문구는 2025-09~2026-09 과기정통부 공고문 첨부에서 실제로 추출된 표현입니다. */
class SupportProgramApplicationPeriodExtractorHelperTest {
    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "□ 접수기간 : 2026. 7. 22.(수) ～ 2026. 8. 4.(화) 오후 4시까지(이지원 시스템 시간 기준)|2026-07-13|2026-07-22|2026-08-04",
            "(공모기간) 2026년 5월 11일 ~ 2026년 6월 10일 14:00까지 - (접수방법) KISA|2026-05-11|2026-05-11|2026-06-10",
            "신청기간 2026.04.30.(목) ~ 6.16.(화) 18:00 ※ 마감시한 이후 신청 불가|2026-04-30|2026-04-30|2026-06-16",
            "○ 접수기간 ○ ’26. 4. 13.(월) ~ 4. 19.(일) 18:00 限|2026-04-13|2026-04-13|2026-04-19",
            "□ 신청기간 연구자 신청 및 주관연구기관 확인 2026. 10. 1. (목) 09:00 ∼ 2026. 10. 14. (수) 18:00 7 선정평가|2026-09-14|2026-10-01|2026-10-14",
            "연구책임자 신청 기간 및 주관연구기관 검토·승인기간 2026.2.25.(수) ~ 2026.3.6.(금) 18:00까지|2026-02-25|2026-02-25|2026-03-06",
            "■ 신청기한 및 신청방법 ᄋ 2026. 1. 27.(화) ~ 2. 25.(수) 18:00까지, (30일)|2026-01-27|2026-01-27|2026-02-25",
            "공모기간 2025년 12월 26일 ~ 2026년 1월 15일 14:00까지 ※ KISA 전자계약|2025-12-26|2025-12-26|2026-01-15",
            "신청 기간 2026. 2. 16 (월) ~ 3. 4 (수) 18:00까지 주관연구기관 검토|2026-02-03|2026-02-16|2026-03-04",
        ],
    )
    fun extractsRangesWrittenRightAfterTheApplicationHeading(text: String, published: String, start: String, end: String) {
        val period = SupportProgramApplicationPeriodExtractorHelper.extract(text, LocalDate.parse(published))!!
        assertEquals(LocalDate.parse(start), period.startDate)
        assertEquals(LocalDate.parse(end), period.endDate)
        assertTrue(period.evidence.contains(end.substring(8).trimStart('0')))
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "신청기간 : 2026. 8. 7.(금), 18:00까지 ※ 기존 신청 마감일인 2026. 7. 31.(금)에서 연장|2026-07-01|2026-08-07",
            "○ (접수기간) ~ ‘26. 04. 03. (금) 23시 59분까지 ※ 접수시간 초과 시|2026-03-06|2026-04-03",
            "○ 제출기간 : 2026. 3. 31.(화) 15:00까지 ○ 제출서류 : 사업계획서|2026-02-26|2026-03-31",
            "신청 기간 ~ 2026.10.07.(수) 18:00(KST)까지 ※ NRF|2026-08-31|2026-10-07",
        ],
    )
    fun acceptsADeadlineOnlyWhenItFollowsTheHeadingWithUntilOrTilde(text: String, published: String, end: String) {
        val period = SupportProgramApplicationPeriodExtractorHelper.extract(text, LocalDate.parse(published))!!
        assertNull(period.startDate)
        assertEquals(LocalDate.parse(end), period.endDate)
        assertEquals("~ ${end.replace('-', '.')}", period.displayText())
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "5. 신청기간 및 신청 시 유의사항 9 6. 선정평가 11 7. 기타사항 13 8. 향후일정(안) 15",
            "참여제한 기간이 신규과제 신청마감 전일(2026.10.13.)까지 종료되는 경우 신규과제 신청 가능",
            "- 신청 기간 * 협약일로부터 ~‘26.7.31.까지 B200 서버 2대",
            "연구기간 30개월 (2025.12.01.~ 2028.05.31.) 6개월 (2025.12.01.∼ 2026.05.31.)",
            "라. 협약기간 : (1차년도) 협약체결일 ∼ 2026. 12. 31. (2차년도) 2027. 1. 1. ~ 2027. 12. 31.",
            "제1조(시행일) 이 규정은 2026. 05. 28.부터 시행한다.",
            "신청기간 3. 12.(목) ~ 4. 30.(목) 연도 없는 날짜",
            "신청기간 2026. 2. 30.(월) ~ 2026. 3. 4.(수) 존재하지 않는 날짜",
            "연구책임자 신청 기간 (신청 마감일) 2026.3.4.(수) ~ 3.1 8.(수) 18:00까지",
        ],
    )
    fun neverGuessesFromTablesOfContentsProjectPeriodsOrUnrelatedDates(text: String) {
        assertNull(SupportProgramApplicationPeriodExtractorHelper.extract(text, LocalDate.parse("2026-03-01")))
    }

    @Test
    fun combinesTracksAndExtensionsIntoTheWholeOpenWindow() {
        val text = """
            ㅇ (공고기간) 2026. 2. 6.(금) ~ 2026. 3. 17.(화)
            ○ (공모기간) 2026년 2월 20일 ~ 2026년 3월 10일 14:00까지
            ○ 중앙거점 신청기간 2026. 2. 20.(금) ~ 2026. 3. 10.(화)
            ○ 연구센터 접수 마감 : 2026. 3. 21.(토) 18:00까지
        """.trimIndent()
        val period = SupportProgramApplicationPeriodExtractorHelper.extract(text, LocalDate.parse("2026-02-06"))!!
        assertEquals(LocalDate.parse("2026-02-20"), period.startDate)
        assertEquals(LocalDate.parse("2026-03-21"), period.endDate)
        assertEquals("2026.02.20 ~ 2026.03.21", period.displayText())
    }

    @Test
    fun rejectsDatesFarOutsideThePublicationWindowButInfersTheNextYearForDecemberToJanuary() {
        assertNull(SupportProgramApplicationPeriodExtractorHelper.extract("신청기간 2024. 1. 2. ~ 2024. 2. 3.", LocalDate.parse("2026-01-01")))
        val period = SupportProgramApplicationPeriodExtractorHelper.extract("접수기간 2025. 12. 20.(토) ~ 1. 9.(금)", LocalDate.parse("2025-12-19"))!!
        assertEquals(LocalDate.parse("2026-01-09"), period.endDate)
        val undated = SupportProgramApplicationPeriodExtractorHelper.extract("신청기간 2020. 1. 2. ~ 2020. 2. 3.", null)!!
        assertEquals(LocalDate.parse("2020-02-03"), undated.endDate)
    }
}
