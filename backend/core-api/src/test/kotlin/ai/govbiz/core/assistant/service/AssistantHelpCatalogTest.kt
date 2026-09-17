package ai.govbiz.core.assistant.service

import ai.govbiz.core.assistant.domain.AssistantHelpEntry
import ai.govbiz.core.assistant.domain.AssistantNavigation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class AssistantHelpCatalogTest {
    private fun entry(id: String = "search-score-meaning", title: String = "점수는 무엇을 뜻하나요", action: AssistantNavigation? = AssistantNavigation("검색 화면 열기", "/app/chat")) =
        AssistantHelpEntry(id, title, "점수는 무슨 뜻인가요?", "점수는 관련도입니다.", listOf("순서를 정하는 값입니다."), null, "public", "available", action)

    @Test
    fun loadsTheShippedCatalogThatTheFrontendContractTestKeepsInSync() {
        val catalog = AssistantHelpCatalog.load(JsonMapper.builder().build())
        assertTrue(catalog.entries.size in 10..40)
        assertEquals("/app/chat", catalog.find("search-confirm-card")!!.action!!.to)
        assertTrue(catalog.entries.all { it.action == null || !it.action!!.to.contains('?') })
        assertNull(catalog.find("unknown"))
    }

    @Test
    fun rejectsEntriesThatWouldBreakTheAiServiceContract() {
        listOf(
            { AssistantHelpCatalog(emptyList()) },
            { AssistantHelpCatalog(listOf(entry(), entry())) },
            { AssistantHelpCatalog(listOf(entry(id = "Bad Id"))) },
            { AssistantHelpCatalog(listOf(entry(title = " "))) },
            { AssistantHelpCatalog(listOf(entry(title = "제어" + 7.toChar()))) },
            { AssistantHelpCatalog(listOf(entry(title = "가".repeat(161)))) },
            { AssistantHelpCatalog(listOf(entry(action = AssistantNavigation("열기", "https://evil.example")))) },
            { AssistantHelpCatalog(listOf(entry(action = AssistantNavigation("열기", "/app/chat?mode=filter")))) },
            { AssistantHelpCatalog(listOf(entry().copy(audience = "guest"))) },
            { AssistantHelpCatalog(listOf(entry().copy(body = List(11) { "문단" }))) },
            { AssistantHelpCatalog((1..41).map { entry(id = "entry-$it") }) },
        ).forEachIndexed { index, build ->
            assertThrows(IllegalArgumentException::class.java, { build() }, "case $index")
        }
    }
}
