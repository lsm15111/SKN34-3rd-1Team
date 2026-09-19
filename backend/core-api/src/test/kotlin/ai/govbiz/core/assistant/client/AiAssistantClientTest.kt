package ai.govbiz.core.assistant.client

import ai.govbiz.core._common.config.JsonDeserializationConfig
import ai.govbiz.core._common.exception.AiServiceCallException
import ai.govbiz.core._common.exception.AiServiceFailure
import ai.govbiz.core.assistant.client.dto.AiAssistantAnswerRequest
import ai.govbiz.core.assistant.client.dto.AiAssistantStreamEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.*
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/** AI Service `tests/assistant/test_core_contract.py`가 읽는 것과 같은 요청·응답 파일을 주고받는지 확인합니다. */
class AiAssistantClientTest {
    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).also {
        JsonDeserializationConfig().strictJsonRequestTypes().customize(it)
    }.build()
    private val builder = RestClient.builder().baseUrl("http://ai-service.test")
        .messageConverters { it.clear(); it.add(JacksonJsonHttpMessageConverter(mapper)) }
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = AiAssistantClient(builder.build(), mapper)
    private val requestJson = resource("contract-request.json")
    private val request = mapper.readValue(requestJson, AiAssistantAnswerRequest::class.java)

    @AfterEach
    fun verifyRequests() = server.verify()

    @Test
    fun sendsTheSharedContractWithThePrincipalAndDecodesCards() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(requestJson, JsonCompareMode.STRICT))
            .andRespond(withSuccess(resource("contract-response.json"), MediaType.APPLICATION_JSON))
        val payload = client.answer(request)
        assertEquals("govbiz-assistant-v2", payload.schemaVersion)
        assertEquals("PARTNER_MATCH", payload.intent)
        assertEquals(7L, request.principal!!.accountId)
        assertEquals("21", payload.cards!!.single()!!.id)
        assertEquals("/app/partners/detail?recruitmentId=21", payload.cards!!.single()!!.to)
        assertEquals("/app/partners", payload.navigation!!.to)
        assertEquals(listOf("get_my_company_profile", "search_partner_recruitments"), payload.toolCalls!!.map { it!!.name })
        assertEquals("SAVE_PROGRAM", payload.actions!!.single()!!.kind)
        assertEquals("KSTARTUP:174520", payload.actions!!.single()!!.targetId)
        assertNull(payload.clarificationQuestion)
    }

    @Test
    fun sendsNullPrincipalForGuests() {
        val guest = request.copy(principal = null)
        server.expect(requestTo(URL)).andExpect(content().json("""{"principal":null}""", JsonCompareMode.LENIENT))
            .andRespond(withSuccess(resource("contract-response.json"), MediaType.APPLICATION_JSON))
        client.answer(guest)
    }

    @ParameterizedTest
    @CsvSource("503,UNAVAILABLE", "504,TIMEOUT", "408,TIMEOUT", "500,UPSTREAM_ERROR", "422,UPSTREAM_ERROR", "204,INVALID_RESPONSE")
    fun mapsAiServiceStatusesToSharedFailures(status: Int, failure: AiServiceFailure) {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatusCode.valueOf(status)))
        val error = assertThrows(AiServiceCallException::class.java) { client.answer(request) }
        assertEquals(failure, error.failure)
    }

    @Test
    fun treatsUndecodableBodiesAsInvalidResponses() {
        server.expect(requestTo(URL)).andRespond(withSuccess("""{"intent":["not","a","string"]}""", MediaType.APPLICATION_JSON))
        val error = assertThrows(AiServiceCallException::class.java) { client.answer(request) }
        assertEquals(AiServiceFailure.INVALID_RESPONSE, error.failure)
    }

    private fun resource(name: String) =
        requireNotNull(javaClass.getResourceAsStream("/assistant/$name")).bufferedReader().use { it.readText() }

    private companion object {
        const val URL = "http://ai-service.test/internal/v1/assistant/answers"
    }

    @Test
    fun readsSseEventsInOrderAndIgnoresCommentsAndUnknownFields() {
        val body = listOf(
            ": 연결 유지용 주석",
            "",
            """event: status""",
            """data: {"phase":"thinking","tool":null}""",
            "",
            // id 같은 모르는 필드가 섞여도 이름·데이터만 읽습니다.
            "id: 7",
            """event: status""",
            """data: {"phase":"reading","tool":"list_saved_programs"}""",
            "",
            """event: text""",
            """data: {"delta":"관심 공고 2건"}""",
            "",
            """event: final""",
            "data: " + resource("contract-response.json").lines().joinToString("") { it.trim() },
            "",
        ).joinToString("\n", postfix = "\n")
        server.expect(requestTo("http://ai-service.test/internal/v1/assistant/answers/stream"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(body, MediaType.TEXT_EVENT_STREAM))

        val events = mutableListOf<AiAssistantStreamEvent>()
        client.stream(request) { events += it }

        assertEquals(4, events.size)
        assertEquals(AiAssistantStreamEvent.Status("thinking", null), events[0])
        assertEquals(AiAssistantStreamEvent.Status("reading", "list_saved_programs"), events[1])
        assertEquals(AiAssistantStreamEvent.Text("관심 공고 2건"), events[2])
        assertEquals("PARTNER_MATCH", (events[3] as AiAssistantStreamEvent.Final).payload.intent)
    }

    @Test
    fun rejectsUnknownEventNamesAndMalformedData() {
        for (body in listOf(
            "event: surprise\ndata: {}\n\n",
            "event: text\ndata: {깨진\n\n",
        )) {
            server.reset()
            server.expect(anything()).andRespond(withSuccess(body, MediaType.TEXT_EVENT_STREAM))
            val error = assertThrows(AiServiceCallException::class.java) { client.stream(request) { } }
            assertEquals(AiServiceFailure.INVALID_RESPONSE, error.failure)
        }
    }

    @Test
    fun mapsStreamStartFailuresToTheSameFailuresAsTheJsonCall() {
        for ((status, failure) in listOf(
            HttpStatus.SERVICE_UNAVAILABLE to AiServiceFailure.UNAVAILABLE,
            HttpStatus.GATEWAY_TIMEOUT to AiServiceFailure.TIMEOUT,
            HttpStatus.INTERNAL_SERVER_ERROR to AiServiceFailure.UPSTREAM_ERROR,
        )) {
            server.reset()
            server.expect(anything()).andRespond(withStatus(status))
            assertEquals(failure, assertThrows(AiServiceCallException::class.java) { client.stream(request) { } }.failure)
        }
    }
}
