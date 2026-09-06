package ai.govbiz.core.recruitment.domain

/**
 * 모집글·제안 본문에 담당자 연락처가 들어가지 못하게 합니다.
 *
 * 연락처는 제안이 수락된 뒤 시스템이 공개하므로, 이메일 주소와 한국 전화번호 형태는 저장 전에 거부합니다.
 */
object ContactPatternPolicy {

    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")

    /** 010-1234-5678, 01012345678, 02-123-4567, 031 123 4567, +82-10-1234-5678 등 */
    private val KOREAN_PHONE = Regex(
        """(?<!\d)(\+82[\s-]?|0)(1[016789]|2|[3-6][1-5]|70|80)[\s-]?\d{3,4}[\s-]?\d{4}(?!\d)""",
    )

    fun containsContact(text: String): Boolean =
        EMAIL.containsMatchIn(text) || KOREAN_PHONE.containsMatchIn(text)
}
