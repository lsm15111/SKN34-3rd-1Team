package ai.govbiz.core.supportprogram.helper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** 문구는 2025-09~2026-09 과기정통부 공고문 첨부에서 추출된 실제 구조를 줄인 것입니다. */
class SupportProgramNoticeSectionExtractorHelperTest {
    @Test
    fun excerptsTheOfficialPurposeAndTargetUntilTheNextHeading() {
        val text = """
            1 사업 개요 □ 사업명 : AI 보안 기술개발 □ 사업 목적 ᄋ 국내 AI 보안 유망기업의 시제품 개발부터 사업화·실증 지원으로
            국가 AI 보안 산업의 경쟁력 제고 □ 사업 기간 : 2026년 ~ 2028년
            2 신청 자격 ○ 신규 AI 보안 제품·서비스 개발 역량을 보유한 국내 기업(컨소시엄 가능, 기업규모 제한없음) □ 접수기간 : 2026. 3. 3. ~ 3. 25.
        """.trimIndent()

        val sections = SupportProgramNoticeSectionExtractorHelper.extract(text)

        assertEquals("국내 AI 보안 유망기업의 시제품 개발부터 사업화·실증 지원으로 국가 AI 보안 산업의 경쟁력 제고", sections.purpose)
        assertEquals("신규 AI 보안 제품·서비스 개발 역량을 보유한 국내 기업(컨소시엄 가능, 기업규모 제한없음)", sections.target)
    }

    @Test
    fun skipsTableOfContentsLinesAndUsesTheRealSectionLater() {
        val text = "목 차 1. 사업개요 1 2. 지원대상 3 3. 신청방법 7 4. 선정평가 9 " +
            "□ 지원대상 : 중소기업 애로기술에 대한 연구를 희망하는 시니어 과학기술인 단체 □ 신청방법 : 이메일"

        val sections = SupportProgramNoticeSectionExtractorHelper.extract(text)

        assertEquals("중소기업 애로기술에 대한 연구를 희망하는 시니어 과학기술인 단체", sections.target)
        assertNull(sections.purpose)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "※ 사업내용은 별첨에서 정하는 방법 및 기준을 우선적용하며 사업별 문의가 있을 경우 담당에게 확인",
            "심사위원회는 지원자격 미적격 등의 경우에 심사에서 제외 가능하며 적격한 사업단장이 없는 경우 선정하지 않을 수 있음",
            "□ 사업 목적 (사 업 명) 2026년 산업맞춤형 혁신바우처 지원사업",
            "□ 지원대상 확인 절차는 별도 안내",
            "□ 사업개요 2026 ~ 2028 (3년)",
            "5. 신청기간 및 신청 시 유의사항 9 6. 선정평가 11 7. 기타사항 13",
            "□ 사업내용 관련 (AI인프라활용팀) 043-931-5755 / gjung@nipa.kr 전산등록 시스템 관련 문의",
            "□ 사업개요 1 II. 제안 요청사항 2 III. 입찰 및 평가 11 IV. 제안서 작성 15",
            "□ 지원대상 소형 데이터센터 기반 AI산업 성장 지원 3개 과제",
            "□ 신청자격 구분 구성 및 자격요건 주관연구 개발기관 참여기관",
            "",
        ],
    )
    fun neverExcerptsInlineWordsMetadataOrNumbersAsSections(text: String) {
        val sections = SupportProgramNoticeSectionExtractorHelper.extract(text)
        assertNull(sections.purpose)
        assertNull(sections.target)
    }

    @Test
    fun capsExcerptsAndTrimsDanglingListMarkers() {
        val longTarget = "국내 대학 및 정부출연연구기관 " + "공동연구 수행 역량을 보유한 기관 ".repeat(40)
        val sections = SupportProgramNoticeSectionExtractorHelper.extract("□ 지원대상 $longTarget")
        assertTrue(sections.target!!.length in 300..SupportProgramNoticeSectionExtractorHelper.MAX_TARGET_LENGTH)

        val dangling = SupportProgramNoticeSectionExtractorHelper.extract("□ 사업목적 연구자의 창의적 아이디어 기반 신소재 원천기술 확보 ○ (")
        assertEquals("연구자의 창의적 아이디어 기반 신소재 원천기술 확보", dangling.purpose)
        val sentence = SupportProgramNoticeSectionExtractorHelper.extract("□ 사업목적 지역 주도의 과학기술 혁신 생태계를 조성한다.")
        assertEquals("지역 주도의 과학기술 혁신 생태계를 조성한다.", sentence.purpose)
    }
}
