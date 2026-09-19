package ai.govbiz.core.assistant.controller

import ai.govbiz.core._common.config.JsonDeserializationConfig
import ai.govbiz.core._common.exception.AiServiceCallException
import ai.govbiz.core._common.exception.ApiExceptionHandler
import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.account.domain.AccountRole
import ai.govbiz.core.account.helper.SessionCookieHelper
import jakarta.servlet.http.Cookie
import ai.govbiz.core.account.service.AccountSessionService
import ai.govbiz.core.account.web.AuthenticatedAccountArgumentResolver
import ai.govbiz.core.assistant.config.AssistantAgentProperties
import ai.govbiz.core.assistant.domain.AssistantAnswer
import ai.govbiz.core.assistant.domain.AssistantCard
import ai.govbiz.core.assistant.domain.AssistantCardKind
import ai.govbiz.core.assistant.domain.AssistantIntent
import ai.govbiz.core.assistant.domain.AssistantNavigation
import ai.govbiz.core.assistant.domain.AssistantStreamEvent
import ai.govbiz.core.assistant.domain.AssistantQuestion
import ai.govbiz.core.assistant.domain.AssistantStreamPhase
import ai.govbiz.core.assistant.domain.AssistantScreenContext
import ai.govbiz.core.assistant.service.AssistantMessageService
import ai.govbiz.core.supportprogram.service.admission.SupportProgramRequestAdmissionService
import ai.govbiz.core.supportprogram.service.admission.config.SupportProgramRequestAdmissionProperties
import java.time.LocalDateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.json.ProblemDetailJacksonMixin
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

class AssistantMessageControllerTest {
    private val service = Mockito.mock(AssistantMessageService::class.java)
    private val sessionService = Mockito.mock(AccountSessionService::class.java)
    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build())
        .addMixIn(ProblemDetail::class.java, ProblemDetailJacksonMixin::class.java).also {
        JsonDeserializationConfig().strictJsonRequestTypes().customize(it)
    }.build()
    private val validator = LocalValidatorFactoryBean().apply { afterPropertiesSet() }
    private val member = Account(7L, "member@example.com", AccountRole.USER, LocalDateTime.of(2026, 9, 1, 9, 0), null, LocalDateTime.of(2026, 9, 1, 9, 0))

    @AfterEach
    fun closeValidator() = validator.close()

    private fun mvc(perClient: Int = 100, agent: AssistantAgentProperties = AssistantAgentProperties(), agentPerClient: Int = 100): MockMvc {
        val admission = SupportProgramRequestAdmissionService(SupportProgramRequestAdmissionProperties(perClient, 100, 4)) { 0L }
        val agentAdmission = SupportProgramRequestAdmissionService(SupportProgramRequestAdmissionProperties(agentPerClient, 100, 4)) { 0L }
        return MockMvcBuilders.standaloneSetup(AssistantMessageController(service, admission, agentAdmission, agent, mapper))
            .setCustomArgumentResolvers(AuthenticatedAccountArgumentResolver { sessionService })
            .setControllerAdvice(ApiExceptionHandler()).setValidator(validator)
            .setMessageConverters(JacksonJsonHttpMessageConverter(mapper)).build()
    }

    private fun body(vararg overrides: Pair<String, Any?>): String {
        val json = linkedMapOf<String, Any?>(
            "message" to "점수가 무슨 뜻이야?",
            "conversationId" to "8f1c2d3e-4b5a-4c6d-8e7f-9a0b1c2d3e4f",
            "context" to mapOf("route" to "/app/chat", "programSelected" to false),
        )
        overrides.forEach { (key, value) -> json[key] = value }
        return mapper.writeValueAsString(json)
    }

    private fun request(json: String = body(), cookie: String? = null) =
        post("/api/v1/assistant/messages").contentType(MediaType.APPLICATION_JSON).content(json)
            .with { it.remoteAddr = "192.0.2.1"; if (cookie != null) it.setCookies(Cookie(SessionCookieHelper.COOKIE_NAME, cookie)); it }

    private val EMPTY_QUESTION = AssistantQuestion("", "", AssistantScreenContext("/", false))

    /** Kotlin은 null 매처를 non-null 파라미터에 넘길 수 없어 매처를 등록한 뒤 빈 질문으로 대신 채웁니다. */
    private fun anyQuestion(): AssistantQuestion = any(AssistantQuestion::class.java) ?: EMPTY_QUESTION

    private fun answer() = AssistantAnswer(
        AssistantIntent.PRODUCT_HELP, "점수는 관련도입니다.", listOf("search-score-meaning"), null, null, null,
        AssistantNavigation("검색 화면 열기", "/app/chat"),
    )

    @Test
    fun answersGuestsWithoutASessionAndReturnsTheServiceAnswerAsJson() {
        val asked = mutableListOf<AssistantQuestion>()
        `when`(service.answer(isNull(), anyQuestion())).thenAnswer { asked += it.getArgument<AssistantQuestion>(1); answer() }
        mvc().perform(request()).andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.intent").value("PRODUCT_HELP"))
            .andExpect(jsonPath("$.answer").value("점수는 관련도입니다."))
            .andExpect(jsonPath("$.citations[0]").value("search-score-meaning"))
            .andExpect(jsonPath("$.clarificationQuestion").value(null))
            .andExpect(jsonPath("$.searchQuery").value(null))
            .andExpect(jsonPath("$.accountTopic").value(null))
            .andExpect(jsonPath("$.navigation.label").value("검색 화면 열기"))
            .andExpect(jsonPath("$.navigation.to").value("/app/chat"))
        val question = asked.single()
        assertEquals("점수가 무슨 뜻이야?", question.message)
        assertEquals("8f1c2d3e-4b5a-4c6d-8e7f-9a0b1c2d3e4f", question.conversationId)
        assertEquals("/app/chat", question.context.route)
        Mockito.verifyNoInteractions(sessionService)
    }

    @Test
    fun ignoresHelpTextSentByTheBrowserBecauseTheCatalogBelongsToCore() {
        val asked = mutableListOf<AssistantQuestion>()
        `when`(service.answer(isNull(), anyQuestion())).thenAnswer { asked += it.getArgument<AssistantQuestion>(1); answer() }
        val forged = body("helpEntries" to listOf(mapOf("id" to "search-score-meaning", "summary" to "조작한 근거")))
        mvc().perform(request(forged)).andExpect(status().isOk)
        // 질문 도메인에는 도움말 자리가 없어 조작한 문장이 AI 요청까지 갈 길이 없습니다.
        assertEquals(AssistantQuestion("점수가 무슨 뜻이야?", "8f1c2d3e-4b5a-4c6d-8e7f-9a0b1c2d3e4f", AssistantScreenContext("/app/chat", false)), asked.single())
    }

    @Test
    fun passesTheSessionAccountWhenACookieIsPresent() {
        `when`(sessionService.requireAccount("session-token")).thenReturn(member)
        `when`(service.answer(any(Account::class.java), anyQuestion())).thenReturn(answer())
        mvc().perform(request(cookie = "session-token")).andExpect(status().isOk)
        Mockito.verify(service).answer(Mockito.eq(member), anyQuestion())
    }

    @Test
    fun rejectsOversizedMalformedOrDuplicateInputBeforeCallingTheService() {
        val cases = listOf(
            body("message" to ""),
            body("message" to "가".repeat(501)),
            body("message" to "제어" + 7.toChar() + "문자"),
            body("conversationId" to ""),
            body("conversationId" to "not-a-uuid"),
            body("conversationId" to "8F1C2D3E-4B5A-4C6D-8E7F-9A0B1C2D3E4F"),
            body("conversationId" to null),
            body("context" to mapOf("route" to "/app/chat?x=1", "programSelected" to false)),
            body("context" to mapOf("route" to "/app/chat", "programSelected" to null)),
        )
        cases.forEach { json ->
            val response = mvc().perform(request(json)).andReturn().response
            assertEquals(400, response.status, json)
        }
        Mockito.verifyNoInteractions(service)
    }

    @Test
    fun rendersAgentCardsWithTheirRoutes() {
        `when`(service.answer(isNull(), anyQuestion())).thenReturn(
            AssistantAnswer(
                AssistantIntent.PARTNER_MATCH, "맞는 모집글 한 건이에요.", emptyList(), null, null, null,
                AssistantNavigation("파트너 모집 열기", "/app/partners"),
                listOf(AssistantCard(AssistantCardKind.RECRUITMENT, "21", "AI 실증 참여기관 구합니다", "서울AI 주식회사 · 서울", "지역과 역할이 맞습니다.", "/app/partners/detail?recruitmentId=21")),
            ),
        )
        mvc().perform(request()).andExpect(status().isOk)
            .andExpect(jsonPath("$.intent").value("PARTNER_MATCH"))
            .andExpect(jsonPath("$.cards.length()").value(1))
            .andExpect(jsonPath("$.cards[0].kind").value("RECRUITMENT"))
            .andExpect(jsonPath("$.cards[0].id").value("21"))
            .andExpect(jsonPath("$.cards[0].subtitle").value("서울AI 주식회사 · 서울"))
            .andExpect(jsonPath("$.cards[0].to").value("/app/partners/detail?recruitmentId=21"))
            .andExpect(jsonPath("$.cards[0].quote").doesNotExist())
            .andExpect(jsonPath("$.navigation.to").value("/app/partners"))
    }

    @Test
    fun memberQuestionsThatCanUseToolsHaveTheirOwnPerClientLimit() {
        `when`(sessionService.requireAccount("session-token")).thenReturn(member)
        `when`(service.answer(any(Account::class.java), anyQuestion())).thenReturn(answer())
        `when`(service.answer(isNull(), anyQuestion())).thenReturn(answer())
        val tools = AssistantAgentProperties(toolsSecret = "assistant-tools-secret-for-tests-0123456789")

        val withTools = mvc(agent = tools, agentPerClient = 1)
        withTools.perform(request(cookie = "session-token")).andExpect(status().isOk).andExpect(jsonPath("$.cards").isArray)
        withTools.perform(request(cookie = "session-token")).andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.code").value("SUPPORT_PROGRAM_RATE_LIMITED"))

        // 비로그인은 도구를 쓰지 않으므로, 도구 비밀이 없으면 회원도 추가 한도를 걸지 않습니다.
        val guests = mvc(agent = tools, agentPerClient = 1)
        guests.perform(request()).andExpect(status().isOk)
        guests.perform(request()).andExpect(status().isOk)
        val withoutTools = mvc(agentPerClient = 1)
        withoutTools.perform(request(cookie = "session-token")).andExpect(status().isOk)
        withoutTools.perform(request(cookie = "session-token")).andExpect(status().isOk)
    }

    @Test
    fun rateLimitsByClientAddressWithTheSharedAdmissionRules() {
        `when`(service.answer(isNull(), anyQuestion())).thenReturn(answer())
        val mvc = mvc(perClient = 1)
        mvc.perform(request()).andExpect(status().isOk)
        mvc.perform(request()).andExpect(status().isTooManyRequests)
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.code").value("SUPPORT_PROGRAM_RATE_LIMITED"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["unavailable", "timeout", "invalidResponse"])
    fun mapsAiServiceFailuresToTheSharedProblemCodes(kind: String) {
        val error = when (kind) {
            "unavailable" -> AiServiceCallException.unavailable(null)
            "timeout" -> AiServiceCallException.timeout(null)
            else -> AiServiceCallException.invalidResponse("bad", null)
        }
        `when`(service.answer(isNull(), anyQuestion())).thenThrow(error)
        val expectedStatus = when (kind) { "unavailable" -> 503; "timeout" -> 504; else -> 502 }
        val expectedCode = when (kind) { "unavailable" -> "AI_SERVICE_UNAVAILABLE"; "timeout" -> "AI_SERVICE_TIMEOUT"; else -> "AI_SERVICE_INVALID_RESPONSE" }
        mvc().perform(request()).andExpect(status().`is`(expectedStatus)).andExpect(jsonPath("$.code").value(expectedCode))
    }

    private fun streamRequest(cookie: String? = null) = request(cookie = cookie).accept(MediaType.TEXT_EVENT_STREAM)

    /** SSE 본문을 빈 줄 기준으로 나눈 덩어리입니다. */
    private fun sseEvents(body: String): List<String> = body.split("\n\n").filter { it.isNotBlank() }

    private fun streamWith(vararg events: AssistantStreamEvent) {
        Mockito.doAnswer { invocation ->
            val sink = invocation.getArgument<(AssistantStreamEvent) -> Unit>(2)
            events.forEach(sink)
            null
        }.`when`(service).answerStreaming(isNull(), anyQuestion(), any() ?: {})
    }

    @Test
    fun streamsProgressTextAndTheFinalAnswerAsServerSentEvents() {
        streamWith(
            AssistantStreamEvent.Status(AssistantStreamPhase.THINKING),
            AssistantStreamEvent.Text("점수는 "),
            AssistantStreamEvent.Text("관련도입니다."),
            AssistantStreamEvent.Final(answer()),
        )

        val body = mvc().perform(streamRequest()).andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("X-Accel-Buffering", "no"))
            .andReturn().response.contentAsString

        val events = sseEvents(body)
        assertEquals(4, events.size)
        assertTrue(events[0].startsWith("event: status"))
        assertTrue(events[0].contains("\"phase\":\"THINKING\""))
        assertTrue(events[1].contains("\"delta\":\"점수는 \""))
        assertTrue(events[3].startsWith("event: final"))
        // 카드·이동 버튼이 실린 답은 마지막 이벤트에만 있습니다.
        assertTrue(events[3].contains("\"intent\":\"PRODUCT_HELP\"") && events[3].contains("/app/chat"))
        assertFalse(events[1].contains("navigation"))
    }

    @Test
    fun streamingFailuresBeforeTheFirstEventKeepTheNormalErrorResponse() {
        Mockito.doThrow(AiServiceCallException.unavailable(null))
            .`when`(service).answerStreaming(isNull(), anyQuestion(), any() ?: {})

        mvc().perform(streamRequest())
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.code").value("AI_SERVICE_UNAVAILABLE"))
    }

    @Test
    fun streamingFailuresAfterTheFirstEventBecomeAnErrorEvent() {
        Mockito.doAnswer { invocation ->
            invocation.getArgument<(AssistantStreamEvent) -> Unit>(2)(AssistantStreamEvent.Status(AssistantStreamPhase.THINKING))
            throw AiServiceCallException.timeout(null)
        }.`when`(service).answerStreaming(isNull(), anyQuestion(), any() ?: {})

        val body = mvc().perform(streamRequest()).andExpect(status().isOk).andReturn().response.contentAsString

        val events = sseEvents(body)
        assertTrue(events.last().startsWith("event: error"))
        assertTrue(events.last().contains("TIMEOUT"))
        assertFalse(body.contains("event: final"))
    }

    @Test
    fun streamingSharesTheSameRequestLimitAsTheJsonAnswer() {
        streamWith(AssistantStreamEvent.Final(answer()))
        val mvc = mvc(perClient = 1)
        mvc.perform(streamRequest()).andExpect(status().isOk)
        mvc.perform(streamRequest()).andExpect(status().isTooManyRequests)
    }
}
