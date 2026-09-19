package ai.govbiz.core.assistant.service

import ai.govbiz.core._common.exception.AiServiceCallException
import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.assistant.client.AiAssistantClient
import ai.govbiz.core.assistant.client.dto.AiAssistantAnswerPayload
import ai.govbiz.core.assistant.client.dto.AiAssistantAnswerRequest
import ai.govbiz.core.assistant.client.dto.AiAssistantCardPayload
import ai.govbiz.core.assistant.client.dto.AiAssistantContext
import ai.govbiz.core.assistant.client.dto.AiAssistantHelpAction
import ai.govbiz.core.assistant.client.dto.AiAssistantHelpEntry
import ai.govbiz.core.assistant.client.dto.AiAssistantHistoryMessage
import ai.govbiz.core.assistant.client.dto.AiAssistantNavigationPayload
import ai.govbiz.core.assistant.client.dto.AiAssistantPrincipal
import ai.govbiz.core.assistant.client.dto.AiAssistantSession
import ai.govbiz.core.assistant.client.dto.AiAssistantToolCallPayload
import ai.govbiz.core.assistant.config.AssistantAgentProperties
import ai.govbiz.core.assistant.domain.AssistantAccountTopic
import ai.govbiz.core.assistant.domain.AssistantAnswer
import ai.govbiz.core.assistant.domain.AssistantCard
import ai.govbiz.core.assistant.domain.AssistantCardKind
import ai.govbiz.core.assistant.domain.AssistantIntent
import ai.govbiz.core.assistant.domain.AssistantNavigation
import ai.govbiz.core.assistant.domain.AssistantHistoryMessage
import ai.govbiz.core.assistant.domain.AssistantQuestion
import ai.govbiz.core.assistant.repository.AssistantConversationRepository
import ai.govbiz.core.partner.domain.PartnerProposalBox
import ai.govbiz.core.partner.domain.PartnerProposalStatus
import ai.govbiz.core.partner.service.PartnerProposalService
import ai.govbiz.core.supportprogram.domain.SavedSupportProgram
import ai.govbiz.core.supportprogram.service.saved.SavedSupportProgramService
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

/**
 * GovBiz 가이드 자유 질문 한 건을 처리합니다. 개인 정보를 가린 질문과 Core 도움말 카탈로그를 AI Service에 한 번 보내고,
 * 로그인 회원이면 이 요청에만 쓰는 계정 묶음 토큰을 함께 실어 AI Service의 읽기 도구가 회원 자료를 되부를 수 있게 합니다.
 * 돌아온 의도·답·카드는 형식·인용·경로 허용 목록으로 다시 검증하고, 회원 상태 템플릿·로그인 안내는 Core가 붙입니다.
 * 최근 대화는 [AssistantConversationRepository]가 서버에서 확정한 질문(개인 정보를 가린 문장)과 답만 짧게 보관하며 MySQL에는 남기지 않습니다.
 */
@Service
class AssistantMessageService(
    private val client: AiAssistantClient,
    private val savedSupportProgramService: SavedSupportProgramService,
    private val partnerProposalService: PartnerProposalService,
    @param:Qualifier("seoulClock") private val clock: Clock,
    private val catalog: AssistantHelpCatalog,
    private val properties: AssistantAgentProperties,
    private val tokenService: AssistantToolTokenService,
    private val conversations: AssistantConversationRepository,
) {
    fun answer(account: Account?, question: AssistantQuestion): AssistantAnswer {
        val history = conversations.recent(account?.id, question.conversationId)
        val request = toRequest(account, question, history)
        val answer = answerFor(verify(client.answer(request), account), account, question)
        // 사용자에게 보인 답만 다음 질문의 맥락으로 남깁니다. AI 장애·계약 위반으로 끝난 질문은 남기지 않습니다.
        (answer.answer ?: answer.clarificationQuestion)?.let { conversations.append(account?.id, question.conversationId, request.message, it) }
        return answer
    }

    private fun answerFor(verified: VerifiedPayload, account: Account?, question: AssistantQuestion): AssistantAnswer =
        when (verified.intent) {
            AssistantIntent.PRODUCT_HELP -> productHelp(verified)
            AssistantIntent.ACCOUNT_STATE -> accountState(verified, account)
            AssistantIntent.SEARCH -> search(verified, account)
            AssistantIntent.PROGRAM_QUESTION -> programQuestion(question)
            AssistantIntent.OUT_OF_SCOPE -> AssistantAnswer(verified.intent, verified.answer, emptyList(), null, null, null, null)
            AssistantIntent.UNCLEAR -> AssistantAnswer(verified.intent, null, emptyList(), verified.clarificationQuestion, null, null, null)
            AssistantIntent.PARTNER_MATCH, AssistantIntent.SAVED_PROGRAMS_QUESTION -> toolAnswer(verified, account)
        }

    /** 도구 비밀이 설정된 경우에만 회원 토큰을 싣습니다. 없으면 AI Service도 도구를 숨기고 의도만 돌려줍니다. */
    private fun toRequest(account: Account?, question: AssistantQuestion, history: List<AssistantHistoryMessage>): AiAssistantAnswerRequest =
        AiAssistantAnswerRequest(
            SCHEMA_VERSION,
            AssistantPiiMasker.mask(question.message).take(MESSAGE_MAX),
            history.map { AiAssistantHistoryMessage(it.role.name, AssistantPiiMasker.mask(it.content).take(HISTORY_MAX)) },
            AiAssistantSession(account != null, account?.company != null),
            AiAssistantContext(question.context.route, question.context.programSelected),
            catalog.entries.map {
                AiAssistantHelpEntry(
                    it.id, it.title, it.question, it.summary, it.body, it.limitation, it.audience, it.status,
                    it.action?.let { action -> AiAssistantHelpAction(action.label, action.to) },
                )
            },
            account?.takeIf { properties.toolsEnabled }?.let {
                AiAssistantPrincipal(it.id, tokenService.issue(it.id).value, it.company != null)
            },
        )

    /**
     * AI Service와 같은 의도별 필드 규칙을 Core에서 다시 확인합니다. 어긋나면 답을 고치지 않고 502로 끝냅니다.
     * 인용은 카탈로그 항목만, 카드는 id·경로 형식, 이동 버튼은 허용 목록 안의 화면만 인정합니다.
     * 비로그인에게 회원 자료 답이 오면 계약 위반입니다.
     */
    private fun verify(payload: AiAssistantAnswerPayload, account: Account?): VerifiedPayload {
        if (payload.schemaVersion != SCHEMA_VERSION) invalidResponse()
        val intent = AssistantIntent.entries.firstOrNull { it.name == payload.intent } ?: invalidResponse()
        val citations = payload.citations ?: invalidResponse()
        if (citations.size > MAX_CITATIONS || citations.any { it == null } || citations.toSet().size != citations.size) invalidResponse()
        val citedIds = citations.map { it!! }
        if (citedIds.any { it !in catalog.ids }) invalidResponse()
        val answer = payload.answer?.also { if (!validText(it, ANSWER_MAX, multiline = true)) invalidResponse() }
        val clarification = payload.clarificationQuestion?.also { if (!validText(it, SHORT_MAX)) invalidResponse() }
        val searchQuery = payload.searchQuery?.also { if (!validText(it, MESSAGE_MAX, multiline = true)) invalidResponse() }
        val accountTopic = payload.accountTopic?.let { name ->
            AssistantAccountTopic.entries.firstOrNull { it.name == name } ?: invalidResponse()
        }
        val present = buildSet {
            if (answer != null) add("answer")
            if (citedIds.isNotEmpty()) add("citations")
            if (clarification != null) add("clarificationQuestion")
            if (searchQuery != null) add("searchQuery")
            if (accountTopic != null) add("accountTopic")
        }
        val (required, optional) = when (intent) {
            AssistantIntent.PRODUCT_HELP -> setOf("answer", "citations") to emptySet()
            AssistantIntent.ACCOUNT_STATE -> setOf("accountTopic") to setOf("answer")
            AssistantIntent.SEARCH -> setOf("searchQuery") to setOf("answer")
            AssistantIntent.PROGRAM_QUESTION -> emptySet<String>() to emptySet()
            AssistantIntent.OUT_OF_SCOPE -> setOf("answer") to emptySet()
            AssistantIntent.UNCLEAR -> setOf("clarificationQuestion") to emptySet()
            AssistantIntent.PARTNER_MATCH, AssistantIntent.SAVED_PROGRAMS_QUESTION -> emptySet<String>() to setOf("answer")
        }
        if (!present.containsAll(required) || !(required + optional).containsAll(present)) invalidResponse()
        val cards = verifyCards(payload.cards)
        val navigation = payload.navigation?.let(::verifyNavigation)
        if ((cards.isNotEmpty() || navigation != null) && (!intent.usesTools || answer == null)) invalidResponse()
        if (account == null && intent.usesTools && answer != null) invalidResponse()
        verifyToolCalls(payload.toolCalls)
        return VerifiedPayload(intent, answer, citedIds, clarification, searchQuery, accountTopic, navigation, cards)
    }

    private fun verifyToolCalls(toolCalls: List<AiAssistantToolCallPayload?>?) {
        if (toolCalls == null || toolCalls.size > MAX_TOOL_CALLS) invalidResponse()
        toolCalls.forEach { call ->
            val name = call?.name ?: invalidResponse()
            val ms = call.ms ?: invalidResponse()
            if (!TOOL_NAME.matches(name) || ms < 0) invalidResponse()
        }
    }

    private fun verifyCards(cards: List<AiAssistantCardPayload?>?): List<AssistantCard> {
        if (cards == null) invalidResponse()
        if (cards.size > MAX_CARDS || cards.any { it == null }) invalidResponse()
        val verified = cards.map { card ->
            val kind = AssistantCardKind.entries.firstOrNull { it.name == card!!.kind } ?: invalidResponse()
            val id = card!!.id?.takeIf { CARD_ID.matches(it) } ?: invalidResponse()
            val title = card.title?.takeIf { validText(it, SHORT_MAX) } ?: invalidResponse()
            val subtitle = card.subtitle?.also { if (!validText(it, SHORT_MAX)) invalidResponse() }
            val reason = card.reason?.takeIf { validText(it, REASON_MAX) } ?: invalidResponse()
            val to = card.to ?: invalidResponse()
            if (to != expectedCardRoute(kind, id)) invalidResponse()
            AssistantCard(kind, id, title, subtitle, reason, to)
        }
        if (verified.map { it.kind to it.id }.toSet().size != verified.size) invalidResponse()
        return verified
    }

    /** 카드 경로는 모델 문자열을 믿지 않고 종류·id에서 다시 만든 값과 같을 때만 통과합니다. */
    private fun expectedCardRoute(kind: AssistantCardKind, id: String): String = when (kind) {
        AssistantCardKind.RECRUITMENT -> {
            if (!NUMERIC_ID.matches(id)) invalidResponse()
            "${InternalRoutes.PARTNER_DETAIL}?recruitmentId=$id"
        }
        AssistantCardKind.PREPARATION -> {
            if (!NUMERIC_ID.matches(id)) invalidResponse()
            "${InternalRoutes.APPLICATION_PREPARATIONS}/$id"
        }
        AssistantCardKind.REVIEW -> {
            if (!NUMERIC_ID.matches(id)) invalidResponse()
            "${InternalRoutes.COMBINATION_REVIEWS}/$id"
        }
        AssistantCardKind.PROGRAM -> {
            val separator = id.indexOf(':')
            if (separator <= 0 || separator == id.lastIndex) invalidResponse()
            val sourceCode = id.substring(0, separator)
            val sourceProgramId = id.substring(separator + 1)
            if (!SOURCE_CODE.matches(sourceCode)) invalidResponse()
            "${InternalRoutes.PROGRAM_DETAIL}?sourceCode=${encode(sourceCode)}&sourceProgramId=${encode(sourceProgramId)}"
        }
    }

    private fun verifyNavigation(navigation: AiAssistantNavigationPayload): AssistantNavigation {
        val label = navigation.label?.takeIf { validText(it, SHORT_MAX) } ?: invalidResponse()
        val to = navigation.to?.takeIf { it in InternalRoutes.NAVIGABLE } ?: invalidResponse()
        return AssistantNavigation(label, to)
    }

    /** 사용법 답입니다. 첫 인용 항목의 행동 버튼을 붙입니다. 경로는 Core 카탈로그가 정한 값입니다. */
    private fun productHelp(payload: VerifiedPayload): AssistantAnswer {
        val navigation = payload.citations.asSequence()
            .mapNotNull { id -> catalog.find(id)?.action }
            .firstOrNull()
        return AssistantAnswer(AssistantIntent.PRODUCT_HELP, payload.answer, payload.citations, null, null, null, navigation)
    }

    /**
     * 검색 요청입니다. 로그인 회원은 AI Service가 공개 공고를 찾아 답과 카드를 만들고, 그러지 못했거나 비로그인이면
     * Core가 검색어를 안내합니다. 이동 버튼은 어느 경우에도 검색어를 미리 채우는 검색 화면입니다.
     */
    private fun search(payload: VerifiedPayload, account: Account?): AssistantAnswer {
        val query = payload.searchQuery!!
        val useAgentAnswer = account != null && payload.answer != null
        return AssistantAnswer(
            AssistantIntent.SEARCH,
            if (useAgentAnswer) payload.answer else AssistantAnswerTexts.search(query),
            emptyList(), null, query, null,
            AssistantNavigation(AssistantAnswerTexts.OPEN_SEARCH_FOR_QUERY, InternalRoutes.CHAT),
            if (useAgentAnswer) payload.cards else emptyList(),
        )
    }

    /** 원문 질문은 공고 문서를 근거로 답하는 기존 화면이 맡습니다. 공고 상세에 있으면 프런트가 그 공고의 질문 화면 버튼을 붙입니다. */
    private fun programQuestion(question: AssistantQuestion): AssistantAnswer =
        if (question.context.programSelected) {
            AssistantAnswer(AssistantIntent.PROGRAM_QUESTION, AssistantAnswerTexts.PROGRAM_QUESTION_ON_DETAIL, emptyList(), null, null, null, null)
        } else {
            AssistantAnswer(
                AssistantIntent.PROGRAM_QUESTION, AssistantAnswerTexts.PROGRAM_QUESTION_NO_PROGRAM, emptyList(), null, null, null,
                AssistantNavigation(AssistantAnswerTexts.OPEN_SEARCH, InternalRoutes.CHAT),
            )
        }

    /** AI Service가 회원 자료 도구로 답을 만들었으면 그 답과 카드를 쓰고, 아니면 Core가 자료를 읽어 답합니다. */
    private fun accountState(payload: VerifiedPayload, account: Account?): AssistantAnswer {
        val topic = payload.accountTopic!!
        if (account != null && payload.answer != null) {
            return AssistantAnswer(AssistantIntent.ACCOUNT_STATE, payload.answer, emptyList(), null, null, topic, payload.navigation, payload.cards)
        }
        val (answer, navigation) = when {
            account == null -> AssistantAnswerTexts.loginRequired(topic) to null
            topic == AssistantAccountTopic.SAVED_PROGRAMS -> savedPrograms(account)
            topic == AssistantAccountTopic.RECEIVED_PROPOSALS -> receivedProposals(account)
            topic == AssistantAccountTopic.COMPANY_PROFILE -> companyProfile(account)
            else -> workStatus(topic)
        }
        return AssistantAnswer(AssistantIntent.ACCOUNT_STATE, answer, emptyList(), null, null, topic, navigation)
    }

    /** 모집글 매칭·관심 공고 묶음 질문입니다. 비로그인은 로그인 안내, 답이 없으면 화면 안내로 끝냅니다. */
    private fun toolAnswer(payload: VerifiedPayload, account: Account?): AssistantAnswer = when {
        account == null -> AssistantAnswer(payload.intent, AssistantAnswerTexts.loginRequired(payload.intent), emptyList(), null, null, null, null)
        // 모집글 매칭은 기업 프로필이 기준이라, 기업이 없으면 AI 답과 무관하게 등록부터 안내합니다.
        payload.intent == AssistantIntent.PARTNER_MATCH && account.company == null -> AssistantAnswer(
            payload.intent, AssistantAnswerTexts.PARTNER_MATCH_NEEDS_COMPANY, emptyList(), null, null, null,
            AssistantNavigation(AssistantAnswerTexts.OPEN_PROFILE, InternalRoutes.PROFILE),
        )
        payload.answer == null -> AssistantAnswer(
            payload.intent, AssistantAnswerTexts.agentNoAnswer(payload.intent), emptyList(), null, null, null,
            if (payload.intent == AssistantIntent.PARTNER_MATCH) {
                AssistantNavigation(AssistantAnswerTexts.OPEN_PARTNERS, InternalRoutes.PARTNERS)
            } else {
                AssistantNavigation(AssistantAnswerTexts.OPEN_SAVED, InternalRoutes.SAVED_PROGRAMS)
            },
        )
        else -> AssistantAnswer(payload.intent, payload.answer, emptyList(), null, null, null, payload.navigation, payload.cards)
    }

    private fun savedPrograms(account: Account): Pair<String, AssistantNavigation?> {
        val saved = savedSupportProgramService.list(account.id)
        if (saved.isEmpty()) {
            return AssistantAnswerTexts.SAVED_NONE to AssistantNavigation(AssistantAnswerTexts.OPEN_SEARCH, InternalRoutes.CHAT)
        }
        val today = LocalDate.now(clock)
        val upcoming = saved.filter { it.endDate == null || !it.endDate!!.isBefore(today) }
        val nearest = upcoming.filter { it.endDate != null }.minByOrNull { it.endDate!! }
        val soon = upcoming.count { it.endDate != null && ChronoUnit.DAYS.between(today, it.endDate) <= SOON_DAYS }
        val answer = when {
            upcoming.isEmpty() -> AssistantAnswerTexts.savedAllClosed(saved.size)
            nearest == null -> AssistantAnswerTexts.savedSummary(saved.size, soon, null)
            else -> AssistantAnswerTexts.savedSummary(
                saved.size, soon,
                AssistantAnswerTexts.Deadline(nearest.program.title, nearest.endDate!!, ChronoUnit.DAYS.between(today, nearest.endDate).toInt()),
            )
        }
        return answer to AssistantNavigation(AssistantAnswerTexts.OPEN_SAVED, InternalRoutes.SAVED_PROGRAMS)
    }

    private fun receivedProposals(account: Account): Pair<String, AssistantNavigation?> {
        if (account.company == null) {
            return AssistantAnswerTexts.PROPOSALS_NEED_COMPANY to AssistantNavigation(AssistantAnswerTexts.OPEN_PROFILE, InternalRoutes.PROFILE)
        }
        val pending = partnerProposalService.findBox(account, PartnerProposalBox.RECEIVED)
            .filter { it.status == PartnerProposalStatus.PENDING }
        val earliest = pending.minOfOrNull { it.proposal.expiresAt }?.toLocalDate()
        val answer = if (pending.isEmpty()) AssistantAnswerTexts.PROPOSALS_NONE else AssistantAnswerTexts.proposalsSummary(pending.size, earliest)
        return answer to AssistantNavigation(AssistantAnswerTexts.OPEN_PROPOSALS, InternalRoutes.PROPOSALS)
    }

    /** 신청 준비·중복 검토·리포트는 Core가 따로 읽지 않습니다. AI 답이 없으면 해당 화면을 열어 직접 보게 안내합니다. */
    private fun workStatus(topic: AssistantAccountTopic): Pair<String, AssistantNavigation?> {
        val navigation = when (topic) {
            AssistantAccountTopic.APPLICATION_PREPARATIONS ->
                AssistantNavigation(AssistantAnswerTexts.OPEN_PREPARATIONS, InternalRoutes.APPLICATION_PREPARATIONS)
            AssistantAccountTopic.COMBINATION_REVIEWS ->
                AssistantNavigation(AssistantAnswerTexts.OPEN_REVIEWS, InternalRoutes.COMBINATION_REVIEWS)
            else -> AssistantNavigation(AssistantAnswerTexts.OPEN_REPORTS, InternalRoutes.REPORTS)
        }
        return AssistantAnswerTexts.workStatusUnavailable(topic) to navigation
    }

    private fun companyProfile(account: Account): Pair<String, AssistantNavigation?> {
        val company = account.company
        val answer = if (company == null) AssistantAnswerTexts.COMPANY_NONE else AssistantAnswerTexts.companyRegistered(company.companyName)
        return answer to AssistantNavigation(AssistantAnswerTexts.OPEN_PROFILE, InternalRoutes.PROFILE)
    }

    private val SavedSupportProgram.endDate: LocalDate?
        get() = program.applicationEndDate

    private fun validText(value: String, maximum: Int, multiline: Boolean = false): Boolean =
        value.isNotBlank() && value.length <= maximum && !(if (multiline) UNSUPPORTED_LAYOUT_TEXT else UNSUPPORTED_TEXT).containsMatchIn(value)

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun invalidResponse(): Nothing =
        throw AiServiceCallException.invalidResponse("AI assistant response violated the internal contract", null)

    private data class VerifiedPayload(
        val intent: AssistantIntent,
        val answer: String?,
        val citations: List<String>,
        val clarificationQuestion: String?,
        val searchQuery: String?,
        val accountTopic: AssistantAccountTopic?,
        val navigation: AssistantNavigation? = null,
        val cards: List<AssistantCard> = emptyList(),
    )

    /** 답변 버튼·카드가 열 수 있는 내부 화면입니다. 프런트 `appPaths`의 값과 같아야 합니다. */
    object InternalRoutes {
        const val CHAT = "/app/chat"
        const val SAVED_PROGRAMS = "/app/saved-programs"
        const val PROPOSALS = "/app/proposals"
        const val PROFILE = "/app/profile"
        const val PARTNERS = "/app/partners"
        const val PARTNER_DETAIL = "/app/partners/detail"
        const val PROGRAM_DETAIL = "/app/support-programs/detail"
        const val APPLICATION_PREPARATIONS = "/app/application-preparations"
        const val COMBINATION_REVIEWS = "/app/combination-reviews"
        const val REPORTS = "/app/reports"

        /** 에이전트의 이동 버튼이 가리킬 수 있는 화면입니다. AI Service `NAVIGATIONS`와 같습니다. */
        val NAVIGABLE: Set<String> = setOf(
            CHAT, SAVED_PROGRAMS, PROPOSALS, PROFILE, PARTNERS, APPLICATION_PREPARATIONS, COMBINATION_REVIEWS, REPORTS,
        )
    }

    companion object {
        const val SCHEMA_VERSION = "govbiz-assistant-v2"
        const val MESSAGE_MAX = 500
        const val HISTORY_MAX = 1000
        const val ANSWER_MAX = 600
        const val SHORT_MAX = 160
        const val REASON_MAX = 200
        const val MAX_CITATIONS = 3
        const val MAX_CARDS = 5
        const val MAX_TOOL_CALLS = 12
        const val SOON_DAYS = 7L
        private val TOOL_NAME = Regex("[a-z_]{1,64}")
        private val UNSUPPORTED_TEXT = Regex("\\p{C}")
        private val UNSUPPORTED_LAYOUT_TEXT = Regex("[\\p{C}&&[^\\n\\r\\t]]")
        private val CARD_ID = Regex("[A-Za-z0-9_:.-]{1,80}")
        private val NUMERIC_ID = Regex("[1-9][0-9]{0,18}")
        private val SOURCE_CODE = Regex("[A-Z][A-Z0-9_]{0,39}")
    }
}
