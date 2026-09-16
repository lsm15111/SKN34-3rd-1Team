package ai.govbiz.core.supportprogram.client.msit.mapper

import ai.govbiz.core.supportprogram.client.msit.dto.MsitProgramPayload
import ai.govbiz.core.supportprogram.client.msit.exception.MsitClientException
import ai.govbiz.core.supportprogram.client.msit.helper.MsitDetailUrlHelper
import ai.govbiz.core.supportprogram.domain.CatalogSupportProgram
import ai.govbiz.core.supportprogram.domain.SupportProgram
import ai.govbiz.core.supportprogram.domain.SupportProgramStatus
import ai.govbiz.core.supportprogram.helper.MsitNoticeContentHelper
import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.jsoup.Jsoup

internal object MsitProgramMapper {
    fun mapValidated(payloads: List<MsitProgramPayload>): List<CatalogSupportProgram> {
        val identities = HashSet<String>()
        return java.util.List.copyOf(payloads.map { payload ->
            val id = MsitDetailUrlHelper.extractProgramId(payload.sourceUrl)
            if (!identities.add(id)) invalid("MSIT API returned duplicate announcement identities")
            val title = plainText(payload.title).takeIf(String::isNotBlank)
                ?: invalid("MSIT API returned an announcement without a title")
            val organization = plainText(payload.organization).ifBlank { "과학기술정보통신부" }
            val sourceUrl = payload.sourceUrl!!
            requireCharacterLimit(title, 500, "title")
            requireCharacterLimit(organization, 255, "organization")
            requireCharacterLimit(sourceUrl, 2048, "source URL")
            CatalogSupportProgram(
                program = SupportProgram(
                    id = id, sourceCode = "MSIT", title = title, organization = organization,
                    summary = MsitNoticeContentHelper.MISSING_CONTENT_SUMMARY,
                    categories = emptyList(), regions = emptyList(), targetDescription = MsitNoticeContentHelper.MISSING_TARGET,
                    applicationPeriod = MsitNoticeContentHelper.MISSING_PERIOD, applicationStartDate = null, applicationEndDate = null,
                    status = SupportProgramStatus.UNKNOWN, sourceName = "과학기술정보통신부", sourceUrl = sourceUrl,
                    matchedReasons = emptyList(),
                ),
                // 게시일을 접수 시작일이나 신청 자격으로 오인하지 않고 정렬에만 사용합니다.
                sortTimestamp = publishedDate(payload.publishedAt),
            )
        })
    }

    private fun publishedDate(value: String?): String {
        val text = value?.trim().orEmpty()
        if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(text)) return ""
        return try { LocalDate.parse(text).toString() } catch (_: DateTimeParseException) { "" }
    }

    private fun plainText(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val document = Jsoup.parseBodyFragment(value)
        document.select("script,style").remove()
        return document.body().text().trim()
    }

    private fun requireCharacterLimit(value: String, limit: Int, field: String) {
        if (value.codePointCount(0, value.length) > limit) invalid("MSIT API returned an oversized $field")
    }

    private fun invalid(message: String): Nothing = throw MsitClientException.invalidResponse(message)
}
