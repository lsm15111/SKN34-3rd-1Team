package ai.govbiz.core.assistant.service

import ai.govbiz.core.assistant.domain.AssistantHelpEntry
import ai.govbiz.core.assistant.domain.AssistantNavigation
import org.springframework.core.io.ClassPathResource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

/**
 * GovBiz 가이드 답변의 근거가 되는 도움말 카탈로그입니다. 브라우저가 보낸 문장을 근거로 삼지 않도록 Core가 원본을 가집니다.
 * 화면의 도움말(`frontend/.../helpContent.ts`)과 내용이 같아야 하며, 프런트 계약 테스트가 두 파일을 맞춥니다.
 * 형식이 AI Service 계약을 어기면 앱이 뜨지 않습니다.
 */
class AssistantHelpCatalog(val entries: List<AssistantHelpEntry>) {
    private val byId = entries.associateBy { it.id }

    init {
        require(entries.size in 1..MAX_ENTRIES) { "assistant help catalog must have 1~$MAX_ENTRIES entries" }
        require(byId.size == entries.size) { "assistant help catalog ids must be unique" }
        entries.forEach(::validate)
    }

    fun find(id: String): AssistantHelpEntry? = byId[id]

    val ids: Set<String>
        get() = byId.keys

    private fun validate(entry: AssistantHelpEntry) {
        val name = "assistant help entry ${entry.id}"
        require(ID.matches(entry.id)) { "$name has an invalid id" }
        require(text(entry.title, SHORT_MAX) && text(entry.question, SHORT_MAX)) { "$name has an invalid title or question" }
        require(text(entry.summary, PARAGRAPH_MAX, layout = true)) { "$name has an invalid summary" }
        require(entry.body.size <= MAX_BODY && entry.body.all { text(it, PARAGRAPH_MAX, layout = true) }) { "$name has an invalid body" }
        require(entry.limitation == null || text(entry.limitation, PARAGRAPH_MAX, layout = true)) { "$name has an invalid limitation" }
        require(entry.audience in AUDIENCES && entry.status in STATUSES) { "$name has an invalid audience or status" }
        entry.action?.let { require(text(it.label, SHORT_MAX) && it.to.length <= ROUTE_MAX && ROUTE.matches(it.to)) { "$name has an invalid action" } }
    }

    private fun text(value: String, maximum: Int, layout: Boolean = false): Boolean =
        value.isNotBlank() && value.length <= maximum && !(if (layout) LAYOUT_CONTROL else CONTROL).containsMatchIn(value)

    companion object {
        const val RESOURCE = "assistant/help-catalog.json"
        private const val MAX_ENTRIES = 40
        private const val MAX_BODY = 10
        private const val SHORT_MAX = 160
        private const val PARAGRAPH_MAX = 600
        private const val ROUTE_MAX = 200
        private val ID = Regex("[a-z0-9]+(-[a-z0-9]+)*")
        private val ROUTE = Regex("/[A-Za-z0-9._~!$&'()*+,;=:@%/-]*")
        private val CONTROL = Regex("\\p{C}")
        private val LAYOUT_CONTROL = Regex("[\\p{C}&&[^\\n\\r\\t]]")
        private val AUDIENCES = setOf("public", "member", "company", "admin")
        private val STATUSES = setOf("available", "demo", "planned")

        fun load(mapper: ObjectMapper, resource: String = RESOURCE): AssistantHelpCatalog {
            val root = ClassPathResource(resource).inputStream.use(mapper::readTree)
            val entries = root.path("entries")
            require(entries.isArray) { "assistant help catalog must have an entries array" }
            return AssistantHelpCatalog(entries.iterator().asSequence().map(::entry).toList())
        }

        private fun entry(node: JsonNode): AssistantHelpEntry {
            val action = node.path("action")
            return AssistantHelpEntry(
                id = string(node, "id"),
                title = string(node, "title"),
                question = string(node, "question"),
                summary = string(node, "summary"),
                body = node.path("body").also { require(it.isArray) { "assistant help entry body must be an array" } }
                    .iterator().asSequence().map { require(it.isString) { "assistant help entry body must be text" }; it.asString() }.toList(),
                limitation = node.path("limitation").takeUnless { it.isNull || it.isMissingNode }?.let {
                    require(it.isString) { "assistant help entry limitation must be text" }
                    it.asString()
                },
                audience = string(node, "audience"),
                status = string(node, "status"),
                action = if (action.isNull || action.isMissingNode) null else AssistantNavigation(string(action, "label"), string(action, "to")),
            )
        }

        private fun string(node: JsonNode, field: String): String {
            val value = node.path(field)
            require(value.isString) { "assistant help catalog field $field must be text" }
            return value.asString()
        }
    }
}
