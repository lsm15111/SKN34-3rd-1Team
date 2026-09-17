package ai.govbiz.core.supportprogram.client.elasticsearch

import ai.govbiz.core.supportprogram.client.elasticsearch.config.ElasticsearchClientProperties
import ai.govbiz.core.supportprogram.client.elasticsearch.dto.ElasticsearchSupportProgramReferenceRequest
import ai.govbiz.core.supportprogram.client.elasticsearch.exception.ElasticsearchClientException
import java.net.URI
import java.time.Duration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.*
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper

class ElasticsearchSupportProgramClientTest {
    private val builder = RestClient.builder().baseUrl("http://es.test")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = ElasticsearchSupportProgramClient(builder.build(),
        ElasticsearchClientProperties(null, null, null, null, null), JsonMapper.builder().build())
    private val reference = ElasticsearchSupportProgramReferenceRequest("v1", "BIZINFO:one", "hash", "2026")
    private val response = """{
        "timed_out":false,"_shards":{"total":1,"successful":1,"failed":0},
        "aggregations":{"catalog":{"versions":{"doc_count":1}}},
        "hits":{"total":{"value":1,"relation":"eq"},"hits":[
          {"_id":"v1","_score":1.5,"_source":{"id":"BIZINFO:one","contentHash":"hash","sortTimestamp":"2026"}}
        ]}}
    """

    @Test
    fun sendsNoriMatchWithExactAllowedVersionsAndGlobalCompletenessCheck() {
        server.expect(requestTo("http://es.test/govbiz-support-program-lexical-v2/_search"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""{
                "size":20,"query":{"bool":{"filter":[{"ids":{"values":["v1"]}}],
                "must":[{"match":{"text":{"query":"서울 창업","operator":"or","zero_terms_query":"none"}}}]}},
                "aggs":{"catalog":{"global":{},"aggs":{"versions":{"filter":{"ids":{"values":["v1"]}}}}}}}
            """))
            .andRespond(withSuccess(response, MediaType.APPLICATION_JSON))
        assertEquals(listOf("BIZINFO:one"), client.search("서울 창업", listOf(reference), 20))
        server.verify()
    }

    @Test
    fun rejectsMissingStaleDuplicatePartialTimedOutAndMalformedResults() {
        val malformed = listOf(
            response.replace("\"doc_count\":1", "\"doc_count\":0"),
            response.replace("\"failed\":0", "\"failed\":1"),
            response.replace("\"successful\":1", "\"successful\":0"),
            response.replace("\"timed_out\":false", "\"timed_out\":true"),
            response.replace("\"v1\"", "\"unknown\""),
            response.replace("BIZINFO:one", "OTHER:one"),
            response.replace("\"hash\"", "\"stale\""),
            response.replace("\"2026\"", "\"2020\""),
            response.replace("\"relation\":\"eq\"", "\"relation\":\"gte\""),
            response.replace("\"value\":1", "\"value\":0"),
            response.replace("\"_score\":1.5", "\"_score\":null"),
            "{bad", "{}",
        )
        for (body in malformed) {
            server.reset()
            server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
            assertThrows(ElasticsearchClientException::class.java) { client.search("서울", listOf(reference), 20) }
            server.verify()
        }
        server.reset()
        server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))
        assertThrows(ElasticsearchClientException::class.java) { client.search("서울", listOf(reference), 20) }
        server.verify()
    }

    @Test
    fun prunesOnlyTheSourcePrefixAfterConfirmingEveryCurrentVersionIsVisible() {
        expectRefreshAndCount(1)
        server.expect(requestTo("http://es.test/govbiz-support-program-lexical-v2/_delete_by_query?refresh=true&conflicts=abort"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""{"query":{"bool":{
                "filter":[{"prefix":{"id":"BIZINFO:"}}],
                "must_not":[{"ids":{"values":["v1"]}}]}}}""", true))
            .andRespond(withSuccess(deleted(812), MediaType.APPLICATION_JSON))

        assertEquals(812, client.pruneSource("BIZINFO", listOf(reference)))
        server.verify()
    }

    @Test
    fun prunesEverySourceVersionWhenThePublishedSnapshotIsEmpty() {
        server.expect(requestTo("http://es.test/govbiz-support-program-lexical-v2/_refresh")).andRespond(withSuccess(shards, MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://es.test/govbiz-support-program-lexical-v2/_count"))
            .andExpect(content().json("""{"query":{"match_none":{}}}""", true))
            .andRespond(withSuccess("""{"count":0,$shardsField}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://es.test/govbiz-support-program-lexical-v2/_delete_by_query?refresh=true&conflicts=abort"))
            .andExpect(content().json("""{"query":{"bool":{"filter":[{"prefix":{"id":"MSIT:"}}],"must_not":[]}}}""", true))
            .andRespond(withSuccess(deleted(3), MediaType.APPLICATION_JSON))

        assertEquals(3, client.pruneSource("MSIT", emptyList()))
        server.verify()
    }

    @Test
    fun neverDeletesWhenCurrentVersionsAreMissingOrTheResultIsPartial() {
        // 현재 버전 일부가 보이지 않으면 삭제 요청을 보내지 않습니다.
        expectRefreshAndCount(0)
        assertThrows(ElasticsearchClientException::class.java) { client.pruneSource("BIZINFO", listOf(reference)) }
        server.verify()

        val partial = listOf(
            deleted(5).replace("\"failures\":[]", "\"failures\":[{\"cause\":{}}]"),
            deleted(5).replace("\"timed_out\":false", "\"timed_out\":true"),
            deleted(5).replace("\"total\":5", "\"total\":6"),
            deleted(5).replace("\"deleted\":5", "\"deleted\":\"5\""),
            "{}",
        )
        for (body in partial) {
            server.reset()
            expectRefreshAndCount(1)
            server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
            assertThrows(ElasticsearchClientException::class.java) { client.pruneSource("BIZINFO", listOf(reference)) }
            server.verify()
        }
    }

    @Test
    fun rejectsReferencesFromAnotherSourceOrUnsafeSourceCodesBeforeAnyRequest() {
        for (sourceCode in listOf("MSIT", "BIZ", "bizinfo", "BIZINFO*", "*", "")) {
            assertThrows(IllegalArgumentException::class.java) { client.pruneSource(sourceCode, listOf(reference)) }
        }
        server.verify()
    }

    private val shardsField = "\"_shards\":{\"total\":1,\"successful\":1,\"failed\":0}"
    private val shards = "{$shardsField}"

    private fun expectRefreshAndCount(count: Int) {
        server.expect(requestTo("http://es.test/govbiz-support-program-lexical-v2/_refresh"))
            .andExpect(method(HttpMethod.POST)).andRespond(withSuccess(shards, MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://es.test/govbiz-support-program-lexical-v2/_count"))
            .andExpect(content().json("""{"query":{"ids":{"values":["v1"]}}}""", true))
            .andRespond(withSuccess("""{"count":$count,$shardsField}""", MediaType.APPLICATION_JSON))
    }

    private fun deleted(count: Int) =
        """{"took":12,"timed_out":false,"total":$count,"deleted":$count,"batches":1,"version_conflicts":0,"noops":0,"failures":[]}"""

    @Test
    fun rejectsUnsafeConfiguration() {
        for (name in listOf("*", "a,b", "../all", "_all", "a/b", "")) {
            assertThrows(IllegalArgumentException::class.java) { ElasticsearchClientProperties(null, name, null, null, null) }
        }
        assertThrows(IllegalArgumentException::class.java) { ElasticsearchClientProperties(URI.create("http://user:pass@host"), null, null, null, null) }
        assertThrows(IllegalArgumentException::class.java) { ElasticsearchClientProperties(null, null, "bad\nkey", null, null) }
        assertThrows(IllegalArgumentException::class.java) { ElasticsearchClientProperties(null, null, null, Duration.ZERO, null) }
    }
}
