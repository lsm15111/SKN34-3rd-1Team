package ai.govbiz.core.assistant.repository

import ai.govbiz.core.assistant.domain.AssistantHistoryMessage
import ai.govbiz.core.assistant.domain.AssistantHistoryRole
import ai.govbiz.core.assistant.repository.exception.AssistantConversationStoreException
import java.security.MessageDigest
import java.time.Duration
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper

/**
 * GovBiz 가이드 대화의 최근 턴을 Redis 목록 하나에 보관합니다. 서버가 확정한 질문(개인 정보를 가린 문장)과 답만 쌓으므로
 * 브라우저가 이전 대화를 꾸며 보낼 수 없습니다. 키는 계정(비로그인은 guest)과 대화 id를 묶은 해시라 다른 계정의 대화를 읽을 수 없습니다.
 * 마지막 질문 뒤 회원 24시간, 비로그인 1시간이 지나면 사라지며 MySQL에는 남기지 않습니다.
 */
@Repository
class AssistantConversationRepository(
    private val redis: StringRedisTemplate,
    private val json: JsonMapper,
) {
    fun recent(accountId: Long?, conversationId: String): List<AssistantHistoryMessage> = storeOperation {
        require(CONVERSATION_ID.matches(conversationId))
        redis.opsForList().range(key(accountId, conversationId), -MAX_ENTRIES.toLong(), -1).orEmpty().map { raw ->
            val node = json.readTree(raw)
            AssistantHistoryMessage(AssistantHistoryRole.valueOf(node.path("role").asString()), node.path("content").asString())
        }
    }

    /** 질문과 답을 한 번에 붙이고 최근 [MAX_ENTRIES]개만 남기며 만료를 새로 겁니다. */
    fun append(accountId: Long?, conversationId: String, question: String, answer: String) {
        require(CONVERSATION_ID.matches(conversationId))
        storeOperation {
            val ttl = if (accountId == null) GUEST_TTL else MEMBER_TTL
            redis.execute(
                APPEND, listOf(key(accountId, conversationId)),
                entry(AssistantHistoryRole.USER, question), entry(AssistantHistoryRole.ASSISTANT, answer),
                MAX_ENTRIES.toString(), ttl.toMillis().toString(),
            ) ?: throw AssistantConversationStoreException()
        }
    }

    private fun entry(role: AssistantHistoryRole, content: String): String =
        json.writeValueAsString(mapOf("role" to role.name, "content" to content.take(MAX_CONTENT)))

    private fun key(accountId: Long?, conversationId: String): String {
        val owner = accountId?.let { "account:$it" } ?: "guest"
        return "govbiz:assistant-conversation:v1:" +
            MessageDigest.getInstance("SHA-256").digest("$owner:$conversationId".toByteArray(Charsets.UTF_8)).toHexString()
    }

    private fun <T> storeOperation(operation: () -> T): T = try {
        operation()
    } catch (error: AssistantConversationStoreException) {
        throw error
    } catch (error: IllegalArgumentException) {
        throw error
    } catch (error: RuntimeException) {
        // 연결·시간 초과·손상된 JSON을 빈 대화로 바꾸지 않습니다. 이전 맥락 없이 답하면 엉뚱한 답이 되기 때문입니다.
        throw AssistantConversationStoreException(error)
    }

    companion object {
        /** 질문·답 한 쌍이 2개입니다. AI Service 계약의 최근 대화 상한(6)과 같습니다. */
        const val MAX_ENTRIES = 6
        const val MAX_CONTENT = 1000
        val MEMBER_TTL: Duration = Duration.ofHours(24)
        val GUEST_TTL: Duration = Duration.ofHours(1)
        val CONVERSATION_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        private val APPEND: RedisScript<Long> =
            RedisScript.of(ClassPathResource("redis/assistant/append-conversation.lua"), Long::class.javaObjectType)
    }
}
