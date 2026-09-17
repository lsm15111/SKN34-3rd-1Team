package ai.govbiz.core.assistant.repository

import ai.govbiz.core._common.test.RedisTestConnection
import ai.govbiz.core.assistant.domain.AssistantHistoryMessage
import ai.govbiz.core.assistant.domain.AssistantHistoryRole
import ai.govbiz.core.assistant.repository.exception.AssistantConversationStoreException
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class AssistantConversationRepositoryTest {
    private val connection = RedisTestConnection()
    private val repository = AssistantConversationRepository(connection.redis, JsonMapper.builder().build())
    private val conversationId = UUID.randomUUID().toString()

    @AfterEach
    fun close() { connection.close() }

    @Test
    fun keepsOnlyTheLatestTurnsInOrderAndIsolatesOwners() {
        (1..4).forEach { repository.append(7L, conversationId, "질문 $it \"따옴표\" 😀", "답 $it") }

        assertEquals(
            listOf(
                AssistantHistoryMessage(AssistantHistoryRole.USER, "질문 2 \"따옴표\" 😀"), AssistantHistoryMessage(AssistantHistoryRole.ASSISTANT, "답 2"),
                AssistantHistoryMessage(AssistantHistoryRole.USER, "질문 3 \"따옴표\" 😀"), AssistantHistoryMessage(AssistantHistoryRole.ASSISTANT, "답 3"),
                AssistantHistoryMessage(AssistantHistoryRole.USER, "질문 4 \"따옴표\" 😀"), AssistantHistoryMessage(AssistantHistoryRole.ASSISTANT, "답 4"),
            ),
            repository.recent(7L, conversationId),
        )
        // 같은 대화 id라도 다른 계정·비로그인은 읽을 수 없습니다.
        assertEquals(emptyList<AssistantHistoryMessage>(), repository.recent(8L, conversationId))
        assertEquals(emptyList<AssistantHistoryMessage>(), repository.recent(null, conversationId))
        assertEquals(emptyList<AssistantHistoryMessage>(), repository.recent(7L, UUID.randomUUID().toString()))
    }

    @Test
    fun expiresMembersAfterADayAndGuestsAfterAnHourAndTruncatesLongAnswers() {
        repository.append(7L, conversationId, "질문", "가".repeat(1500))
        repository.append(null, conversationId, "질문", "답")
        val keys = connection.redis.keys("govbiz:assistant-conversation:v1:*").orEmpty()
        val ttls = keys.map { connection.redis.getExpire(it) }.sorted()
        assertTrue(ttls.first() in 3590..3600, ttls.toString())
        assertTrue(ttls.last() in 86390..86400, ttls.toString())
        assertEquals(1000, repository.recent(7L, conversationId).last().content.length)
    }

    @Test
    fun rejectsMalformedIdsAndSurfacesStoreFailures() {
        assertThrows(IllegalArgumentException::class.java) { repository.recent(7L, "not-a-uuid") }
        assertThrows(IllegalArgumentException::class.java) { repository.append(7L, "not-a-uuid", "질문", "답") }
        connection.close()
        assertThrows(AssistantConversationStoreException::class.java) { repository.recent(7L, conversationId) }
    }
}
