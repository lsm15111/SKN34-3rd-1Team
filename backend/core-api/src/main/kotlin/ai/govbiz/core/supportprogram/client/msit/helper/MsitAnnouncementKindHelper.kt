package ai.govbiz.core.supportprogram.client.msit.helper

import java.text.Normalizer

/** 과기정통부 사업공고 게시판의 제목으로 지원사업이 아닌 게시물을 구분합니다. 재공고·연장·정정은 모집 공고로 유지합니다. */
internal object MsitAnnouncementKindHelper {
    enum class Kind { SUPPORT_PROGRAM, SELECTION_RESULT, PROCUREMENT, HIRING, CANCELLATION }

    private val WHITESPACE = Regex("\\s+")
    private val RULES = listOf(
        Kind.SELECTION_RESULT to Regex("선정결과|결과공고|결과발표|결과안내|최종선정|합격자|심사결과|평가결과"),
        Kind.PROCUREMENT to Regex("입찰|용역|사업자선정|낙찰"),
        Kind.HIRING to Regex("채용|공무직|기간제근로자|직원모집"),
        Kind.CANCELLATION to Regex("공고취소|취소공고|모집취소|취소안내"),
    )

    fun classify(title: String): Kind {
        val compact = Normalizer.normalize(title, Normalizer.Form.NFKC).replace(WHITESPACE, "")
        return RULES.firstOrNull { (_, pattern) -> pattern.containsMatchIn(compact) }?.first ?: Kind.SUPPORT_PROGRAM
    }
}
