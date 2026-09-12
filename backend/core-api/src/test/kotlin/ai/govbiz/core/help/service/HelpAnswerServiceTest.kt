package ai.govbiz.core.help.service

import ai.govbiz.core._common.exception.AiServiceCallException
import ai.govbiz.core.help.client.ai.AiHelpAnswerClient
import ai.govbiz.core.help.client.ai.dto.AiHelpAnswerPayload
import ai.govbiz.core.help.client.ai.dto.AiHelpAnswerRequest
import ai.govbiz.core.help.controller.dto.HelpAnswerRequest
import ai.govbiz.core.help.controller.dto.HelpEntryRequest
import ai.govbiz.core.help.service.dto.HelpAnswerStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito

class HelpAnswerServiceTest {
    private val client = Mockito.mock(AiHelpAnswerClient::class.java)
    private val service = HelpAnswerService(client)

    private val request = HelpAnswerRequest(
        question = "점수는 무슨 뜻인가요?",
        entries = listOf(
            HelpEntryRequest(
                id = "relevance-score-meaning",
                title = "점수는 무엇을 뜻하나요",
                summary = "점수는 검색어와 공고의 관련도입니다.",
                body = listOf("검색 문장과 공고 내용이 얼마나 가까운지를 나타냅니다."),
                limitation = "다른 검색의 점수와 비교할 수 없습니다.",
                status = "available",
            ),
        ),
    )

    @Test
    fun `보낸 항목을 그대로 AI Service에 전달하고 검증한 답변을 돌려준다`() {
        var sent: AiHelpAnswerRequest? = null
        Mockito.`when`(client.answer(anyValue())).thenAnswer { invocation ->
            sent = invocation.getArgument(0)
            AiHelpAnswerPayload("점수는 관련도입니다.", "ANSWERED", listOf("relevance-score-meaning"))
        }

        val result = service.answer(request)

        assertEquals("점수는 무슨 뜻인가요?", sent?.question)
        assertEquals(listOf("relevance-score-meaning"), sent?.entries?.map { it.id })
        assertEquals(HelpAnswerStatus.ANSWERED, result.answerStatus)
        assertEquals(listOf("relevance-score-meaning"), result.citationEntryIds)
    }

    @ParameterizedTest
    @ValueSource(strings = ["OUT_OF_SCOPE_PROGRAM", "OUT_OF_SCOPE_GENERAL", "NOT_IN_HELP"])
    fun `기권하면 AI가 쓴 문장을 버리고 화면 문구에 맡긴다`(status: String) {
        Mockito.`when`(client.answer(anyValue())).thenReturn(
            AiHelpAnswerPayload("모델이 지어낸 안내 문구", status, emptyList()),
        )

        val result = service.answer(request)

        assertEquals("", result.answer)
        assertEquals(HelpAnswerStatus.valueOf(status), result.answerStatus)
        assertTrue(result.citationEntryIds.isEmpty())
    }

    @Test
    fun `보내지 않은 항목을 인용하면 답변으로 내보내지 않는다`() {
        Mockito.`when`(client.answer(anyValue())).thenReturn(
            AiHelpAnswerPayload("지어낸 답", "ANSWERED", listOf("made-up-entry")),
        )

        assertThrows(AiServiceCallException::class.java) { service.answer(request) }
    }

    @Test
    fun `근거 없이 답하면 실패로 처리한다`() {
        Mockito.`when`(client.answer(anyValue())).thenReturn(
            AiHelpAnswerPayload("지어낸 답", "ANSWERED", emptyList()),
        )

        assertThrows(AiServiceCallException::class.java) { service.answer(request) }
    }

    @Test
    fun `기권하면서 인용을 붙이면 실패로 처리한다`() {
        Mockito.`when`(client.answer(anyValue())).thenReturn(
            AiHelpAnswerPayload("", "NOT_IN_HELP", listOf("relevance-score-meaning")),
        )

        assertThrows(AiServiceCallException::class.java) { service.answer(request) }
    }

    @Test
    fun `모르는 상태 값은 실패로 처리한다`() {
        Mockito.`when`(client.answer(anyValue())).thenReturn(AiHelpAnswerPayload("답", "SOMETHING_ELSE", emptyList()))

        assertThrows(AiServiceCallException::class.java) { service.answer(request) }
    }
}

/** Kotlin의 non-null 파라미터에 Mockito matcher를 넘기기 위한 테스트 전용 helper입니다. */
@Suppress("UNCHECKED_CAST")
private fun <T> anyValue(): T = Mockito.any<T>() as T
