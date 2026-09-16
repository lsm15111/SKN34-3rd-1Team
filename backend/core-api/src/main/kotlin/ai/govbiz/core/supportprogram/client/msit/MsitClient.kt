package ai.govbiz.core.supportprogram.client.msit

import ai.govbiz.core.supportprogram.client.msit.config.MsitClientProperties
import ai.govbiz.core.supportprogram.client.msit.dto.MsitPage
import ai.govbiz.core.supportprogram.client.msit.dto.MsitProgramPayload
import ai.govbiz.core.supportprogram.client.msit.exception.MsitClientException
import ai.govbiz.core.supportprogram.client.msit.helper.MsitDetailUrlHelper
import ai.govbiz.core.supportprogram.client.msit.helper.MsitPageDecoderHelper
import ai.govbiz.core.supportprogram.client.msit.helper.executeMsitHttpCall
import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode

@Component
class MsitClient(
    @param:Qualifier("msitRestClient") private val restClient: RestClient,
    private val properties: MsitClientProperties,
) {
    /**
     * 게시일 최신순 목록을 1페이지부터 읽다가 [publishedSince]보다 오래된 공고에 도달하면 멈춥니다.
     * 수집 중 새 글이 올라와 앞 페이지 공고가 다음 페이지로 밀리면 같은 ID를 한 번만 담습니다.
     */
    fun fetchPublishedSince(publishedSince: LocalDate): List<MsitProgramPayload> {
        val key = properties.decodedApiKey()
        if (key.isBlank()) throw MsitClientException.notConfigured()
        val first = fetchPage(key, 1)
        if (first.perPage !in 1..PAGE_SIZE || first.totalCount > MAX_ITEMS) invalid("MSIT API returned unsupported pagination metadata")
        val programs = ArrayList<MsitProgramPayload>()
        val identities = HashSet<String>()
        var previousPublishedOn: LocalDate? = null
        var shiftedDuplicates = 0
        var undatedSkipped = 0
        var pageNumber = 1
        var page = first
        while (true) {
            validatePage(page, pageNumber, first)
            // 새 글로 전체 건수가 늘면 밀린 게시물이 다음 페이지로 넘어가므로 최신 페이지의 건수로 끝을 판단합니다.
            val pageCount = pageCount(page)
            if (pageCount > MAX_PAGES) invalid("MSIT API exceeded the safe pagination limit")
            var reachedOlder = false
            for (program in page.items) {
                val id = MsitDetailUrlHelper.extractProgramId(program.sourceUrl)
                val publishedOn = publishedDate(program.publishedAt)
                if (publishedOn == null) {
                    // 게시일이 없으면 수집 기간을 판단할 수 없어 담지 않고, 최신순 검증에서도 제외합니다.
                    undatedSkipped++
                    continue
                }
                if (previousPublishedOn != null && publishedOn.isAfter(previousPublishedOn)) {
                    invalid("MSIT API is no longer ordered by published date")
                }
                previousPublishedOn = publishedOn
                if (publishedOn.isBefore(publishedSince)) {
                    reachedOlder = true
                    continue
                }
                if (identities.add(id)) programs.add(program) else shiftedDuplicates++
            }
            if (pageNumber == 1 || pageNumber % 10 == 0) {
                logger.info("MSIT 공고 수집 진행: {}페이지, 기간 내 {}건", pageNumber, programs.size)
            }
            if (reachedOlder || pageNumber >= pageCount) break
            pageNumber++
            page = fetchPage(key, pageNumber)
        }
        logger.info(
            "MSIT {} 이후 게시 공고 {}건을 {}페이지에서 수집했습니다. 밀린 중복 {}건, 게시일 없음 {}건 제외",
            publishedSince, programs.size, pageNumber, shiftedDuplicates, undatedSkipped,
        )
        return java.util.List.copyOf(programs)
    }

    private fun pageCount(page: MsitPage): Int =
        maxOf(1, ((page.totalCount.toLong() + page.perPage - 1) / page.perPage).toInt())

    private fun validatePage(page: MsitPage, expected: Int, first: MsitPage) {
        // 수집 중 게시물이 추가·삭제되면 전체 건수는 달라질 수 있으나, 마지막 페이지 전에는 페이지가 가득 차야 합니다.
        if (page.page != expected || page.perPage != first.perPage || page.items.size > page.perPage || page.totalCount > MAX_ITEMS) {
            invalid("MSIT API returned an incomplete page or inconsistent pagination metadata")
        }
        if (expected < pageCount(page) && page.items.size != page.perPage) {
            invalid("MSIT API returned an incomplete page or inconsistent pagination metadata")
        }
    }

    private fun publishedDate(value: String?): LocalDate? {
        val text = value?.trim().orEmpty()
        if (!PUBLISHED_DATE.matches(text)) return null
        return try { LocalDate.parse(text) } catch (_: DateTimeParseException) { null }
    }

    private fun fetchPage(key: String, page: Int): MsitPage = executeMsitHttpCall {
        val body = restClient.get()
            .uri("$PROGRAMS_PATH?serviceKey={key}&pageNo={page}&numOfRows={perPage}&returnType=json", key, page, PAGE_SIZE)
            .retrieve()
            .onStatus({ it.value() != HttpStatus.OK.value() }, { _, response ->
                throw MsitClientException.upstreamError(response.statusCode.value())
            })
            .body(JsonNode::class.java) ?: invalid("MSIT API returned an empty response")
        MsitPageDecoderHelper.decode(body)
    }

    private fun invalid(message: String): Nothing = throw MsitClientException.invalidResponse(message)

    companion object {
        const val PROGRAMS_PATH = "/1721000/msitannouncementinfo/businessAnnouncMentList"
        // 운영 API가 요청 크기와 관계없이 10건을 반환하므로 해당 계약을 명시합니다.
        const val PAGE_SIZE = 10
        private const val MAX_ITEMS = 20_000
        private const val MAX_PAGES = 2_000
        private val PUBLISHED_DATE = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
        private val logger = LoggerFactory.getLogger(MsitClient::class.java)
    }
}
