package ai.govbiz.core.help.controller

import ai.govbiz.core._common.config.JsonDeserializationConfig
import ai.govbiz.core._common.exception.AiServiceCallException
import ai.govbiz.core._common.exception.ApiExceptionHandler
import ai.govbiz.core.help.service.HelpAnswerService
import ai.govbiz.core.help.service.dto.HelpAnswerResult
import ai.govbiz.core.help.service.dto.HelpAnswerStatus
import ai.govbiz.core.supportprogram.service.admission.SupportProgramRequestAdmissionService
import ai.govbiz.core.supportprogram.service.admission.config.SupportProgramRequestAdmissionProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

class HelpAnswerControllerTest {
    private val service = Mockito.mock(HelpAnswerService::class.java)
    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).also {
        JsonDeserializationConfig().strictJsonRequestTypes().customize(it)
    }.build()
    private val validator = LocalValidatorFactoryBean().apply { afterPropertiesSet() }

    @AfterEach
    fun closeValidator() {
        validator.close()
    }

    private fun mvc(perClient: Int = 100, global: Int = 100, concurrent: Int = 4): MockMvc {
        val admission = SupportProgramRequestAdmissionService(
            SupportProgramRequestAdmissionProperties(perClient, global, concurrent),
        ) { 0L }
        return MockMvcBuilders.standaloneSetup(HelpAnswerController(service, admission))
            .setControllerAdvice(ApiExceptionHandler())
            .setValidator(validator)
            .setMessageConverters(JacksonJsonHttpMessageConverter(mapper))
            .build()
    }

    private fun body(question: String = "점수는 무슨 뜻인가요?", entries: String = ENTRY) =
        """{"question":"$question","entries":[$entries]}"""

    @Test
    fun `검증한 답변과 인용 항목을 돌려준다`() {
        Mockito.`when`(service.answer(anyValue())).thenReturn(
            HelpAnswerResult("점수는 관련도입니다.", HelpAnswerStatus.ANSWERED, listOf("relevance-score-meaning")),
        )

        mvc().perform(post("/api/v1/help/answers").contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.answerStatus").value("ANSWERED"))
            .andExpect(jsonPath("$.citationEntryIds[0]").value("relevance-score-meaning"))
    }

    @Test
    fun `빈 질문은 AI를 호출하지 않고 거절한다`() {
        mvc().perform(post("/api/v1/help/answers").contentType(MediaType.APPLICATION_JSON).content(body(question = " ")))
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(service)
    }

    @Test
    fun `항목이 없으면 AI를 호출하지 않고 거절한다`() {
        mvc().perform(
            post("/api/v1/help/answers").contentType(MediaType.APPLICATION_JSON)
                .content("""{"question":"점수는 무슨 뜻인가요?","entries":[]}"""),
        ).andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(service)
    }

    @Test
    fun `요청량 한도를 넘으면 AI를 호출하지 않고 429로 거절한다`() {
        Mockito.`when`(service.answer(anyValue())).thenReturn(
            HelpAnswerResult("점수는 관련도입니다.", HelpAnswerStatus.ANSWERED, listOf("relevance-score-meaning")),
        )
        val mvc = mvc(perClient = 1)

        mvc.perform(post("/api/v1/help/answers").contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isOk)
        mvc.perform(post("/api/v1/help/answers").contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isTooManyRequests)

        Mockito.verify(service, Mockito.times(1)).answer(anyValue())
    }

    @Test
    fun `AI Service 장애는 안내 문구 없이 503으로 알린다`() {
        Mockito.`when`(service.answer(anyValue())).thenThrow(AiServiceCallException.unavailable(null))

        mvc().perform(post("/api/v1/help/answers").contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isServiceUnavailable)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
    }

    private companion object {
        const val ENTRY = """{"id":"relevance-score-meaning","title":"점수는 무엇을 뜻하나요",""" +
            """"summary":"점수는 검색어와 공고의 관련도입니다.","body":["검색 문장과 공고 내용이 얼마나 가까운지를 나타냅니다."],""" +
            """"limitation":"다른 검색의 점수와 비교할 수 없습니다.","status":"available"}"""
    }
}

/** Kotlin의 non-null 파라미터에 Mockito matcher를 넘기기 위한 테스트 전용 helper입니다. */
@Suppress("UNCHECKED_CAST")
private fun <T> anyValue(): T = Mockito.any<T>() as T
