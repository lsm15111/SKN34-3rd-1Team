package ai.govbiz.core.supportprogram.helper

import java.text.Normalizer
import java.time.DateTimeException
import java.time.LocalDate

/** 공식 공고문에서 찾은 신청 가능 기간입니다. 시작일이 없으면 마감일만 명시된 공고입니다. */
data class SupportProgramApplicationPeriod(
    val startDate: LocalDate?,
    val endDate: LocalDate,
    val evidence: String,
) {
    fun displayText(): String =
        listOfNotNull(startDate?.let(::format), "~", format(endDate)).joinToString(" ")

    private fun format(date: LocalDate) = "%04d.%02d.%02d".format(date.year, date.monthValue, date.dayOfMonth)
}

/**
 * 공고문 텍스트의 `신청기간`·`접수기간`·`공모기간` 같은 표제 바로 뒤에 적힌 날짜만 신청 기간으로 인정합니다.
 * 연구·협약·사업기간, 목차 줄, 연도 없는 날짜는 추측하지 않으며 표 안의 일정표처럼 표제와 떨어진 날짜는 찾지 않습니다.
 */
object SupportProgramApplicationPeriodExtractorHelper {
    /** 추출 규칙을 바꾸면 올려 기존 결과를 다시 계산하게 합니다. */
    const val VERSION = 1

    /** 기간만 공고문에서 확인한 공고의 안내 문구입니다. 지원 대상·자격은 여전히 추정하지 않습니다. */
    const val OFFICIAL_ATTACHMENT_SUMMARY =
        "신청 기간은 공식 첨부 공고문에서 자동으로 확인했습니다. 지원 대상·신청 자격·지원 내용은 원문을 확인해 주세요."

    private val WHITESPACE = Regex("\\s+")
    private val KEYWORD = Regex("(접수|신청|공모|제출|모집|공고)\\s*(기간|기한|마감\\s*일시|마감일|마감)")
    private val DATE = Regex(
        "(?:(?<y4>20\\d{2})\\s*(?:년|[./-])\\s*|['’‘`]\\s*(?<y2>\\d{2})\\s*[./-]\\s*)?" +
            "(?<m>\\d{1,2})\\s*(?:월|[./-])\\s*(?<d>\\d{1,2})(?!\\d)\\s*(?:일|\\.)?",
    )
    private val TAIL = Regex("^\\s*(?:\\([^)]{0,6}\\))?\\s*,?\\s*(?:오전|오후)?\\s*(?:\\d{1,2}\\s*(?::\\s*\\d{2}|시(?:\\s*\\d{1,2}\\s*분)?))?\\s*")
    private val RANGE_SEPARATOR = Regex("^[~～∼〜\\-–—]\\s*")
    private val UNTIL = Regex("^[,\\s]*(?:\\(\\s*KST\\s*\\))?\\s*까지")
    private val TILDE_BEFORE = Regex("[~～∼〜]\\s*$")
    private val REJECTED_LABEL = Regex("협약|연구기간|수행기간|사업기간|사용|운영기간|교육|행사|평가|발표|전일|이후|이전|로부터|부터\\s*$|다\\.|\\d{4}\\s*년도")
    private val PRIMARY_KINDS = setOf("접수", "신청", "공모", "제출", "모집")
    private const val MAX_LABEL_LENGTH = 45
    private const val MAX_SINGLE_DATE_OFFSET = 16
    private const val WINDOW_LENGTH = MAX_LABEL_LENGTH + 60
    private const val MAX_EVIDENCE_LENGTH = 300

    private data class Candidate(val kind: String, val start: LocalDate?, val end: LocalDate, val evidence: String)

    fun extract(text: String, publishedOn: LocalDate?): SupportProgramApplicationPeriod? {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).replace(WHITESPACE, " ")
        val candidates = KEYWORD.findAll(normalized).mapNotNull { keyword ->
            candidateAfter(keyword.groupValues[1], keyword.value, normalized, keyword.range.last + 1)
        }.filter { candidate ->
            (candidate.start == null || !candidate.start.isAfter(candidate.end)) && withinPublicationWindow(candidate, publishedOn)
        }.toList()
        if (candidates.isEmpty()) return null
        val pool = candidates.filter { it.kind in PRIMARY_KINDS }.ifEmpty { candidates }
        // 트랙·연장으로 기간이 여러 개면 가장 이른 시작부터 가장 늦은 마감까지를 신청 가능 범위로 봅니다.
        val end = pool.maxOf { it.end }
        val start = pool.mapNotNull { it.start }.minOrNull()
        return SupportProgramApplicationPeriod(start, end, pool.first { it.end == end }.evidence)
    }

    private fun candidateAfter(kind: String, keyword: String, text: String, position: Int): Candidate? {
        val window = text.substring(position, minOf(text.length, position + WINDOW_LENGTH))
        for (first in DATE.findAll(window)) {
            if (first.range.first > MAX_LABEL_LENGTH) return null
            val label = window.substring(0, first.range.first)
            if (REJECTED_LABEL.containsMatchIn(label)) return null
            // 연도 없는 첫 날짜는 목차 번호·항목 번호와 구분할 수 없어 건너뛰고, 존재하지 않는 날짜면 후보 전체를 버립니다.
            if (first.groups["y4"] == null && first.groups["y2"] == null) continue
            val (startDate, year) = toDate(first, null) ?: return null
            val rest = window.substring(first.range.last + 1)
            val tail = TAIL.find(rest)?.value.orEmpty()
            val afterTail = rest.substring(tail.length)
            val separator = RANGE_SEPARATOR.find(afterTail)
            if (separator != null) {
                val afterSeparator = afterTail.substring(separator.value.length)
                val second = DATE.find(afterSeparator)?.takeIf { it.range.first == 0 }
                if (second != null) {
                    var endDate = toDate(second, year)?.first ?: return null
                    val explicitYear = second.groups["y4"] != null || second.groups["y2"] != null
                    if (endDate.isBefore(startDate) && !explicitYear) {
                        // 연도 없는 마감일은 11~12월 시작·1~2월 마감처럼 해를 넘기는 경우에만 다음 해로 봅니다.
                        if (startDate.monthValue < 11 || endDate.monthValue > 2) return null
                        endDate = endDate.plusYears(1)
                    }
                    val endTail = TAIL.find(afterSeparator.substring(second.range.last + 1))?.value.orEmpty()
                    val length = first.range.last + 1 + tail.length + separator.value.length + second.range.last + 1 + endTail.length
                    return Candidate(kind, startDate, endDate, evidence(keyword, window, length))
                }
            }
            val deadlineOnly = UNTIL.containsMatchIn(afterTail) || TILDE_BEFORE.containsMatchIn(label)
            if (first.range.first <= MAX_SINGLE_DATE_OFFSET && deadlineOnly) {
                return Candidate(kind, null, startDate, evidence(keyword, window, first.range.last + 1 + tail.length))
            }
            return null
        }
        return null
    }

    private fun toDate(match: MatchResult, inheritedYear: Int?): Pair<LocalDate, Int>? {
        val year = match.groups["y4"]?.value?.toInt()
            ?: match.groups["y2"]?.value?.let { 2000 + it.toInt() }
            ?: inheritedYear
            ?: return null
        return try {
            LocalDate.of(year, match.groups["m"]!!.value.toInt(), match.groups["d"]!!.value.toInt()) to year
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun withinPublicationWindow(candidate: Candidate, publishedOn: LocalDate?): Boolean {
        if (publishedOn == null) return true
        if (candidate.end.isBefore(publishedOn.minusDays(31)) || candidate.end.isAfter(publishedOn.plusDays(400))) return false
        return candidate.start == null || !candidate.start.isBefore(publishedOn.minusDays(60))
    }

    private fun evidence(keyword: String, window: String, length: Int): String =
        "$keyword ${window.substring(0, minOf(window.length, length)).trim()}".take(MAX_EVIDENCE_LENGTH)
}
