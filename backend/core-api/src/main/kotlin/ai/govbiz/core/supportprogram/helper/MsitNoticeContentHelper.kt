package ai.govbiz.core.supportprogram.helper

import ai.govbiz.core.supportprogram.domain.SupportProgram

/** MSIT 공고 첨부에서 찾은 기간·원문 발췌입니다. 찾지 못한 항목은 null입니다. */
data class MsitNoticeExtraction(
    val period: SupportProgramApplicationPeriod?,
    val sections: SupportProgramNoticeSections,
)

/**
 * MSIT 공고의 안내 문구와 첨부 발췌 반영 규칙을 한곳에 둡니다.
 *
 * 발췌문은 검색 색인 텍스트에 들어가므로 색인을 먼저 갱신하는 카탈로그 동기화에서만 반영하고,
 * 추출 워커는 색인에 들어가지 않는 기간과 기간 안내 문구만 즉시 바꿉니다.
 */
object MsitNoticeContentHelper {
    const val MISSING_CONTENT_SUMMARY = "공식 API에 지원 대상·접수 기간·본문이 제공되지 않습니다. 모집 여부와 신청 자격은 원문을 확인해 주세요."
    const val PERIOD_ONLY_SUMMARY =
        "신청 기간은 공식 첨부 공고문에서 자동으로 확인했습니다. 지원 대상·신청 자격·지원 내용은 원문을 확인해 주세요."
    const val MISSING_TARGET = "정보 없음"
    const val MISSING_PERIOD = "정보 없음"
    private const val EXCERPT_NOTICE = "※ 공식 첨부 공고문에서 자동으로 발췌한 원문입니다. 신청 자격과 접수 기간은 원문을 확인해 주세요."

    /** 안내 문구가 아니라 원문 발췌가 담긴 요약인지 판단합니다. 색인 텍스트 포함 여부에 사용합니다. */
    fun isExcerptSummary(summary: String): Boolean = summary != MISSING_CONTENT_SUMMARY && summary != PERIOD_ONLY_SUMMARY

    fun apply(program: SupportProgram, extraction: MsitNoticeExtraction): SupportProgram {
        val period = extraction.period
        val purpose = extraction.sections.purpose
        return program.copy(
            applicationPeriod = period?.displayText() ?: program.applicationPeriod,
            applicationStartDate = period?.startDate ?: program.applicationStartDate,
            applicationEndDate = period?.endDate ?: program.applicationEndDate,
            summary = when {
                purpose != null -> "$purpose\n\n$EXCERPT_NOTICE"
                period != null -> PERIOD_ONLY_SUMMARY
                else -> program.summary
            },
            targetDescription = extraction.sections.target ?: program.targetDescription,
        )
    }
}
