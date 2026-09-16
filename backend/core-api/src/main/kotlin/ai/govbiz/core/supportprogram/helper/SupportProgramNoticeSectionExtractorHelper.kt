package ai.govbiz.core.supportprogram.helper

import java.text.Normalizer

/** 공고문에서 표제 뒤 원문을 그대로 발췌한 구간입니다. 요약·재작성하지 않습니다. */
data class SupportProgramNoticeSections(val purpose: String?, val target: String?)

/**
 * 공고문의 `사업 목적`·`사업 개요`와 `신청 자격`·`지원 대상` 표제 뒤 원문을 다음 표제 전까지 발췌합니다.
 * 목차 줄, 조사로 이어지는 본문 속 단어, 사업명·사업분류 같은 메타 줄은 버리며 대상 분류는 추정하지 않습니다.
 */
object SupportProgramNoticeSectionExtractorHelper {
    const val MAX_PURPOSE_LENGTH = 500
    const val MAX_TARGET_LENGTH = 400

    private val WHITESPACE = Regex("\\s+")
    private val PURPOSE = Regex("(?:사업\\s*목적|추진\\s*목적|사업\\s*개요|지원\\s*내용|사업\\s*내용)$FOLLOWED_BY_TEXT")
    private val TARGET = Regex("(?:신청\\s*자격|지원\\s*대상|신청\\s*대상|지원\\s*자격|모집\\s*대상|공모\\s*대상|참여\\s*대상)$FOLLOWED_BY_TEXT")
    private val STOP = Regex(
        "\\s(?:[□■▢◇◆●]|\\d{1,2}\\s+(?=[가-힣]{2,}\\s))|" +
            "(?:접수\\s*기간|신청\\s*기간|신청\\s*방법|접수\\s*방법|제출\\s*서류|선정\\s*평가|평가\\s*절차|추진\\s*일정|문의처|사업\\s*기간|" +
            "지원\\s*규모|지원\\s*내용|사업\\s*내용|신청\\s*자격|지원\\s*대상|사업\\s*목적|사업\\s*개요|사\\s*업\\s*명|사업\\s*분류|공고\\s*기간|" +
            "신청\\s*제한|\\(\\s*[가-힣]{2,6}\\s*\\))",
    )
    private val TABLE_OF_CONTENTS = Regex("\\s\\d{1,2}\\s+\\d{1,2}\\s*\\.\\s*[가-힣]|[가-힣]\\s\\d{1,2}\\s\\d{1,2}\\.")
    private val LEADING_MARKS = Regex("^[ㅇᄋ○◦·)\\-:：\\s]+")
    // 공백 뒤에 홀로 남은 목록 기호("○ (", " 나.")만 지우고 문장 끝 "…한다."는 보존합니다.
    private val TRAILING_MARKS = Regex("(?:\\s+(?:[ㅇᄋ○◦※▪·\\-(]|[가-하]\\.|\\d{1,2}\\.))+\\s*$")
    private val HANGUL = Regex("[가-힣]")
    private val METADATA_LINE = Regex("^\\(?\\s*(?:사\\s*업\\s*(?:명|분류|기간|규모)|총\\s*사업비|과제\\s*명)|^[^가-힣]*$")
    private const val MIN_LENGTH = 15
    private const val MIN_HANGUL = 8
    private const val LOOKAHEAD = 200

    /** 표제가 조사·다른 명사로 이어지면(`지원대상 확인`, `사업내용은 별첨`) 본문 속 단어로 봅니다. */
    private const val FOLLOWED_BY_TEXT =
        "(?!\\s*(?:및|과|와|의|에|을|를|이|가|은|는|기간|여부|확인|제한|요건|미적격|부적격|검토|위반|변경|별첨))\\s*[:：]?\\s*"

    // 연락처·로마숫자 목차가 섞인 구간은 사업 설명이 아닙니다.
    private val CONTACT_OR_OUTLINE = Regex("\\d{2,4}-\\d{3,4}-\\d{4}|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+|\\b(?:II|III|IV)\\.")
    // 지원대상에는 신청 주체를 뜻하는 명사가 있어야 하며, 표 머리글("구분 …")로 시작하면 버립니다.
    private val APPLICANT_NOUN = Regex("기업|기관|대학|연구원|연구자|교원|단체|법인|사업자|소상공인|창업|지자체|지방자치|학생|개인|국민|컨소시엄")
    private val TABLE_HEADER = Regex("^구\\s*분")

    fun extract(text: String): SupportProgramNoticeSections {
        val normalized = MsitNoticeContentHelper.sanitize(Normalizer.normalize(text, Normalizer.Form.NFKC)).replace(WHITESPACE, " ")
        return SupportProgramNoticeSections(
            purpose = firstSection(normalized, PURPOSE, MAX_PURPOSE_LENGTH) { !CONTACT_OR_OUTLINE.containsMatchIn(it) },
            target = firstSection(normalized, TARGET, MAX_TARGET_LENGTH) {
                APPLICANT_NOUN.containsMatchIn(it) && !TABLE_HEADER.containsMatchIn(it) && !CONTACT_OR_OUTLINE.containsMatchIn(it)
            },
        )
    }

    private fun firstSection(text: String, heading: Regex, limit: Int, accept: (String) -> Boolean): String? =
        heading.findAll(text).firstNotNullOfOrNull { match ->
            val tail = text.substring(match.range.last + 1, minOf(text.length, match.range.last + 1 + limit + LOOKAHEAD))
            if (tail.isEmpty() || TABLE_OF_CONTENTS.containsMatchIn(tail.take(60))) return@firstNotNullOfOrNull null
            val stop = STOP.find(tail, 1)
            val body = (if (stop != null) tail.substring(0, stop.range.first) else tail.take(limit))
                .replace(LEADING_MARKS, "").take(limit).replace(TRAILING_MARKS, "").trim()
            body.takeIf {
                it.length >= MIN_LENGTH && HANGUL.findAll(it).count() >= MIN_HANGUL && !METADATA_LINE.containsMatchIn(it) && accept(it)
            }
        }
}
