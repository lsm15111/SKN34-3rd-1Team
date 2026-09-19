package ai.govbiz.core.assistant.service

import ai.govbiz.core._common.exception.AiServiceCallException
import ai.govbiz.core._common.exception.AiServiceFailure
import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.account.domain.AccountRole
import ai.govbiz.core.account.domain.CompanySummary
import ai.govbiz.core.assistant.client.AiAssistantClient
import ai.govbiz.core.assistant.client.dto.AiAssistantAnswerPayload
import ai.govbiz.core.assistant.client.dto.AiAssistantAnswerRequest
import ai.govbiz.core.applicationpreparation.service.ApplicationPreparationService
import ai.govbiz.core.assistant.client.dto.AiAssistantActionPayload
import ai.govbiz.core.assistant.client.dto.AiAssistantCardPayload
import ai.govbiz.core.assistant.client.dto.AiAssistantContext
import ai.govbiz.core.assistant.client.dto.AiAssistantNavigationPayload
import ai.govbiz.core.assistant.client.dto.AiAssistantSession
import ai.govbiz.core.assistant.client.dto.AiAssistantStreamEvent
import ai.govbiz.core.assistant.client.dto.AiAssistantToolCallPayload
import ai.govbiz.core.assistant.config.AssistantAgentProperties
import ai.govbiz.core.assistant.domain.AssistantAccountTopic
import ai.govbiz.core.assistant.domain.AssistantCard
import ai.govbiz.core.assistant.domain.AssistantCardKind
import ai.govbiz.core.assistant.domain.AssistantHelpEntry
import ai.govbiz.core.assistant.domain.AssistantHistoryMessage
import ai.govbiz.core.assistant.domain.AssistantHistoryRole
import ai.govbiz.core.assistant.domain.AssistantIntent
import ai.govbiz.core.assistant.domain.AssistantNavigation
import ai.govbiz.core.assistant.domain.AssistantQuestion
import ai.govbiz.core.assistant.domain.AssistantStreamEvent
import ai.govbiz.core.assistant.domain.AssistantStreamPhase
import ai.govbiz.core.assistant.domain.AssistantScreenContext
import ai.govbiz.core.assistant.repository.AssistantConversationRepository
import ai.govbiz.core.assistant.repository.exception.AssistantConversationStoreException
import ai.govbiz.core.partner.domain.PartnerProposal
import ai.govbiz.core.partner.domain.PartnerProposalBox
import ai.govbiz.core.partner.domain.PartnerProposalInput
import ai.govbiz.core.partner.domain.PartnerProposalParty
import ai.govbiz.core.partner.domain.PartnerProposalRecruitment
import ai.govbiz.core.partner.domain.PartnerRecruitmentProgram
import ai.govbiz.core.partner.domain.PartnerRecruitmentStatus
import ai.govbiz.core.partner.domain.PartnerProposalStatus
import ai.govbiz.core.partner.domain.PartnerProposalView
import ai.govbiz.core.partner.service.PartnerProposalService
import ai.govbiz.core.combinationreview.service.CombinationReviewService
import ai.govbiz.core.supportprogram.domain.SavedSupportProgram
import ai.govbiz.core.supportprogram.domain.SupportProgram
import ai.govbiz.core.supportprogram.domain.SupportProgramStatus
import ai.govbiz.core.supportprogram.repository.SupportProgramRepository
import ai.govbiz.core.supportprogram.service.saved.SavedSupportProgramService
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.`when`

/** GovBiz 가이드 한 경로입니다. 비로그인은 도구 없이 의도만, 로그인 회원은 계정 묶음 토큰으로 도구를 쓴 답과 카드를 받습니다. */
class AssistantMessageServiceTest {
    private val client = Mockito.mock(AiAssistantClient::class.java)
    private val savedPrograms = Mockito.mock(SavedSupportProgramService::class.java)
    private val proposals = Mockito.mock(PartnerProposalService::class.java)
    private val clock = Clock.fixed(Instant.parse("2026-09-13T03:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val properties = AssistantAgentProperties(toolsSecret = "assistant-tools-secret-for-tests-0123456789")
    private val tokens = AssistantToolTokenService(properties, clock)

    private val scoreEntry = AssistantHelpEntry(
        "search-score-meaning", "점수는 무엇을 뜻하나요", "점수는 무슨 뜻인가요?",
        "점수는 검색어와 공고의 관련도입니다.", listOf("점수는 순서를 정하는 값입니다."), "선정 가능성은 제공하지 않습니다.",
        "public", "available", AssistantNavigation("검색 화면 열기", "/app/chat"),
    )
    private val partnerEntry = AssistantHelpEntry(
        "partner-write-requires-company", "모집글을 쓰려면 기업 등록이 필요합니다", "모집글은 왜 못 쓰나요?",
        "기업을 등록한 회원만 모집글을 씁니다.", emptyList(), null, "member", "available", null,
    )
    private val catalog = AssistantHelpCatalog(listOf(scoreEntry, partnerEntry))
    private val conversations = Mockito.mock(AssistantConversationRepository::class.java)
    private val supportPrograms = Mockito.mock(SupportProgramRepository::class.java)
    private val preparations = Mockito.mock(ApplicationPreparationService::class.java)
    private val reviews = Mockito.mock(CombinationReviewService::class.java)
    private val actions = AssistantActionService(savedPrograms, supportPrograms, preparations, reviews)
    private val service = AssistantMessageService(client, savedPrograms, proposals, clock, catalog, properties, tokens, conversations, actions)
    private val conversationId = "8f1c2d3e-4b5a-4c6d-8e7f-9a0b1c2d3e4f"
    private val member = Account(7L, "member@example.com", AccountRole.USER, LocalDateTime.of(2026, 9, 1, 9, 0), null, LocalDateTime.of(2026, 9, 1, 9, 0))
    private val companyMember = member.copy(company = CompanySummary(3L, "데이터브릿지 주식회사", "1248100998"))

    private fun question(message: String = "점수가 무슨 뜻이야?", programSelected: Boolean = false) =
        AssistantQuestion(message, conversationId, AssistantScreenContext("/app/chat", programSelected))

    private fun payload(
        intent: String, answer: String? = null, citations: List<String?>? = emptyList(), clarification: String? = null,
        searchQuery: String? = null, accountTopic: String? = null, schemaVersion: String? = "govbiz-assistant-v2",
        cards: List<AiAssistantCardPayload?>? = emptyList(), navigation: AiAssistantNavigationPayload? = null,
        actions: List<AiAssistantActionPayload?>? = emptyList(), toolCalls: List<AiAssistantToolCallPayload?>? = emptyList(),
    ) = AiAssistantAnswerPayload(schemaVersion, intent, answer, citations, clarification, searchQuery, accountTopic, cards, navigation, actions, toolCalls)

    private fun recruitmentCard(id: String = "21", to: String = "/app/partners/detail?recruitmentId=$id", kind: String = "RECRUITMENT") =
        AiAssistantCardPayload(kind, id, "AI 실증 참여기관 구합니다", "서울AI 주식회사 · 서울", "지역과 역할이 맞습니다.", to)

    private fun programCard(sourceCode: String = "BIZINFO", programId: String = "PBLN_000000000000001") = AiAssistantCardPayload(
        "PROGRAM", "$sourceCode:$programId", "서울 AI 실증 지원사업", "서울경제진흥원 · 2026-09-30", "가장 빨리 마감됩니다.",
        "/app/support-programs/detail?sourceCode=$sourceCode&sourceProgramId=$programId",
    )

    private val partners = AiAssistantNavigationPayload("파트너 모집 열기", "/app/partners")
    private val profileTool = listOf(AiAssistantToolCallPayload("get_my_company_profile", 12))

    /** Kotlin은 null 매처를 non-null 파라미터에 넘길 수 없어 매처를 등록한 뒤 빈 요청으로 대신 채웁니다. 보낸 요청은 answer로 잡습니다. */
    private val sentRequests = mutableListOf<AiAssistantAnswerRequest>()

    private fun respondWith(payload: AiAssistantAnswerPayload) {
        `when`(client.answer(any(AiAssistantAnswerRequest::class.java) ?: EMPTY_REQUEST)).thenAnswer {
            sentRequests += it.getArgument<AiAssistantAnswerRequest>(0)
            payload
        }
    }

    private fun lastSent(): AiAssistantAnswerRequest = sentRequests.last()

    private fun program(id: String, title: String, endDate: LocalDate?) = SavedSupportProgram(
        LocalDateTime.of(2026, 9, 10, 10, 0),
        SupportProgram(id, "BIZINFO", title, "중소벤처기업부", "요약", emptyList(), emptyList(), "대상", "기간", null, endDate, SupportProgramStatus.OPEN, "기업마당", "https://example.com", emptyList()),
    )

    /** 응답 기한은 생성 시각 + 응답 창이므로 만료 시각에서 거꾸로 생성 시각을 정합니다. */
    private fun pendingView(expiresAt: LocalDateTime, status: PartnerProposalStatus = PartnerProposalStatus.PENDING): PartnerProposalView {
        val program = PartnerRecruitmentProgram(11L, "BIZINFO", "PBLN-1", "서울 AI 실증 지원사업", "서울경제진흥원", "요약", "대상", "2026-09-01 ~ 2026-09-30",
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "https://www.bizinfo.go.kr")
        val party = PartnerProposalParty(8L, "proposer@company.co.kr", "네이버 주식회사", "2208162517", "경기도", "정보통신업", 1999, null, isEmailVerified = true)
        val createdAt = expiresAt.minus(PartnerProposal.RESPONSE_WINDOW)
        val proposal = PartnerProposal(
            31L, PartnerProposalRecruitment(21L, "AI 실증 참여기관 구합니다", LocalDate.of(2026, 9, 29), null, program),
            party, party.copy(accountId = 7L), PartnerProposalInput("라벨링 운영을 맡겠습니다.", true), null, null, null, createdAt, createdAt,
        )
        return PartnerProposalView(proposal, status, PartnerRecruitmentStatus.OPEN, revealsContacts = false)
    }

    @Test
    fun masksPersonalIdentifiersAndSendsSessionAndTheServerCatalogToAiService() {
        respondWith(payload("OUT_OF_SCOPE", answer = "그 내용은 도와드리기 어렵습니다."))
        val history = listOf(AssistantHistoryMessage(AssistantHistoryRole.USER, "제 번호는 010-1234-5678이고 메일은 me@example.com"))
        `when`(conversations.recent(7L, conversationId)).thenReturn(history)
        service.answer(companyMember, question("사업자번호 123-45-67890으로 조회해 줘"))
        val sent = lastSent()
        assertEquals("govbiz-assistant-v2", sent.schemaVersion)
        assertEquals("사업자번호 [사업자등록번호]으로 조회해 줘", sent.message)
        assertEquals("제 번호는 [전화번호]이고 메일은 [이메일]", sent.history.single().content)
        assertEquals("USER", sent.history.single().role)
        assertTrue(sent.session.authenticated)
        assertTrue(sent.session.hasCompany)
        assertEquals("/app/chat", sent.context.route)
        assertEquals(listOf("search-score-meaning", "partner-write-requires-company"), sent.helpEntries.map { it.id })
        assertEquals("/app/chat", sent.helpEntries.first().action!!.to)
        assertNull(sent.helpEntries.last().action)
    }

    @Test
    fun guestSessionIsSentAsUnauthenticatedWithoutCompany() {
        respondWith(payload("UNCLEAR", clarification = "어떤 화면의 사용법이 궁금하신가요?"))
        val answer = service.answer(null, question("그거 어떻게 해?"))
        assertFalse(lastSent().session.authenticated)
        assertFalse(lastSent().session.hasCompany)
        assertEquals(AssistantIntent.UNCLEAR, answer.intent)
        assertEquals("어떤 화면의 사용법이 궁금하신가요?", answer.clarificationQuestion)
        assertNull(answer.answer)
        assertNull(answer.navigation)
    }

    @Test
    fun productHelpKeepsCitationsAndAttachesFirstCitedEntryAction() {
        respondWith(payload("PRODUCT_HELP", answer = "점수는 관련도입니다.", citations = listOf("partner-write-requires-company", "search-score-meaning")))
        val answer = service.answer(null, question())
        assertEquals(AssistantIntent.PRODUCT_HELP, answer.intent)
        assertEquals("점수는 관련도입니다.", answer.answer)
        assertEquals(listOf("partner-write-requires-company", "search-score-meaning"), answer.citations)
        assertEquals(AssistantNavigation("검색 화면 열기", "/app/chat"), answer.navigation)
    }

    @Test
    fun rejectsCitationsOutsideTheServerCatalog() {
        respondWith(payload("PRODUCT_HELP", answer = "점수는 관련도입니다.", citations = listOf("unknown-entry")))
        val error = assertThrows(AiServiceCallException::class.java) { service.answer(null, question()) }
        assertEquals(AiServiceFailure.INVALID_RESPONSE, error.failure)
    }

    @Test
    fun rejectsIntentFieldMismatchesUnknownIntentsAndWrongSchema() {
        listOf(
            payload("PRODUCT_HELP", answer = "인용이 없습니다."),
            payload("SEARCH", answer = "검색어 대신 답만 왔습니다."),
            payload("UNCLEAR", clarification = "질문", searchQuery = "둘 다"),
            payload("ACCOUNT_STATE", accountTopic = "UNKNOWN_TOPIC"),
            payload("ELIGIBILITY", answer = "없는 의도"),
            payload("OUT_OF_SCOPE", answer = "제어 문자 " + 7.toChar() + " 포함"),
            payload("OUT_OF_SCOPE", answer = "답", citations = null),
            payload("OUT_OF_SCOPE", answer = "답", schemaVersion = "govbiz-assistant-v1"),
            payload("OUT_OF_SCOPE", answer = "답", toolCalls = null),
            payload("OUT_OF_SCOPE", answer = "답", toolCalls = listOf(AiAssistantToolCallPayload("Bad Name", 1))),
            payload("OUT_OF_SCOPE", answer = "답", toolCalls = listOf(AiAssistantToolCallPayload("list_saved_programs", -1))),
            payload("OUT_OF_SCOPE", answer = "답", toolCalls = List(13) { AiAssistantToolCallPayload("list_saved_programs", 1) }),
        ).forEach { bad ->
            respondWith(bad)
            val error = assertThrows(AiServiceCallException::class.java, { service.answer(null, question()) }, bad.toString())
            assertEquals(AiServiceFailure.INVALID_RESPONSE, error.failure)
        }
    }

    @Test
    fun searchIntentPassesQueryAndPointsToTheSearchScreen() {
        respondWith(payload("SEARCH", searchQuery = "부산 수출 지원"))
        val answer = service.answer(null, question("부산 수출 지원 사업 찾아줘"))
        assertEquals(AssistantIntent.SEARCH, answer.intent)
        assertEquals("부산 수출 지원", answer.searchQuery)
        assertTrue(answer.answer!!.contains("'부산 수출 지원'"))
        assertEquals(AssistantNavigation("검색 화면에서 찾기", "/app/chat"), answer.navigation)
    }

    @Test
    fun programQuestionDependsOnWhetherAProgramIsOpen() {
        respondWith(payload("PROGRAM_QUESTION"))
        val onDetail = service.answer(null, question("이 공고 신청 서류 뭐야?", programSelected = true))
        assertEquals(AssistantAnswerTexts.PROGRAM_QUESTION_ON_DETAIL, onDetail.answer)
        assertNull(onDetail.navigation)

        val elsewhere = service.answer(null, question("이 공고 신청 서류 뭐야?", programSelected = false))
        assertEquals(AssistantAnswerTexts.PROGRAM_QUESTION_NO_PROGRAM, elsewhere.answer)
        assertEquals("/app/chat", elsewhere.navigation!!.to)
    }

    @Test
    fun accountStateForGuestsAsksToLogInWithoutReadingMemberData() {
        respondWith(payload("ACCOUNT_STATE", accountTopic = "SAVED_PROGRAMS"))
        val answer = service.answer(null, question("관심 공고 곧 마감인 거 있어?"))
        assertEquals(AssistantIntent.ACCOUNT_STATE, answer.intent)
        assertEquals(AssistantAccountTopic.SAVED_PROGRAMS, answer.accountTopic)
        assertEquals(AssistantAnswerTexts.loginRequired(AssistantAccountTopic.SAVED_PROGRAMS), answer.answer)
        assertNull(answer.navigation)
        Mockito.verifyNoInteractions(savedPrograms, proposals)
    }

    @Test
    fun savedProgramsAnswerCountsSoonDeadlinesAndNamesTheNearest() {
        respondWith(payload("ACCOUNT_STATE", accountTopic = "SAVED_PROGRAMS"))
        `when`(savedPrograms.list(7L)).thenReturn(listOf(
            program("P1", "지난 공고", LocalDate.of(2026, 9, 1)),
            program("P2", "가까운 공고", LocalDate.of(2026, 9, 16)),
            program("P3", "먼 공고", LocalDate.of(2026, 10, 30)),
            program("P4", "미정 공고", null),
        ))
        val answer = service.answer(member, question("관심 공고 곧 마감인 거 있어?"))
        assertEquals("관심 공고 4건 중 7일 안에 마감인 공고가 1건입니다. 가장 가까운 마감은 '가까운 공고'(9월 16일 마감, D-3)입니다.", answer.answer)
        assertEquals(AssistantNavigation("관심 공고함 열기", "/app/saved-programs"), answer.navigation)
    }

    @Test
    fun savedProgramsAnswerHandlesEmptyAndAllClosedBoxes() {
        respondWith(payload("ACCOUNT_STATE", accountTopic = "SAVED_PROGRAMS"))
        `when`(savedPrograms.list(7L)).thenReturn(emptyList())
        val empty = service.answer(member, question("관심 공고"))
        assertEquals(AssistantAnswerTexts.SAVED_NONE, empty.answer)
        assertEquals("/app/chat", empty.navigation!!.to)

        `when`(savedPrograms.list(7L)).thenReturn(listOf(program("P1", "지난 공고", LocalDate.of(2026, 9, 12))))
        val closed = service.answer(member, question("관심 공고"))
        assertEquals(AssistantAnswerTexts.savedAllClosed(1), closed.answer)
        assertEquals("/app/saved-programs", closed.navigation!!.to)
    }

    @Test
    fun receivedProposalsNeedACompanyAndOtherwiseCountPendingOnes() {
        respondWith(payload("ACCOUNT_STATE", accountTopic = "RECEIVED_PROPOSALS"))
        val noCompany = service.answer(member, question("받은 제안 있어?"))
        assertEquals(AssistantAnswerTexts.PROPOSALS_NEED_COMPANY, noCompany.answer)
        assertEquals("/app/profile", noCompany.navigation!!.to)
        Mockito.verifyNoInteractions(proposals)

        `when`(proposals.findBox(companyMember, PartnerProposalBox.RECEIVED)).thenReturn(listOf(
            pendingView(LocalDateTime.of(2026, 9, 20, 9, 0)),
            pendingView(LocalDateTime.of(2026, 9, 18, 18, 0)),
            pendingView(LocalDateTime.of(2026, 9, 14, 9, 0), PartnerProposalStatus.DECLINED),
        ))
        val withCompany = service.answer(companyMember, question("받은 제안 있어?"))
        assertEquals("응답을 기다리는 받은 제안이 2건입니다. 가장 빠른 응답 기한은 9월 18일입니다. 수락·거절은 제안함에서 합니다.", withCompany.answer)
        assertEquals(AssistantNavigation("제안함 열기", "/app/proposals"), withCompany.navigation)
    }

    @Test
    fun companyProfileAnswerNamesTheRegisteredCompany() {
        respondWith(payload("ACCOUNT_STATE", accountTopic = "COMPANY_PROFILE"))
        assertEquals(AssistantAnswerTexts.COMPANY_NONE, service.answer(member, question("내 기업 등록됐어?")).answer)
        val registered = service.answer(companyMember, question("내 기업 등록됐어?"))
        assertEquals(AssistantAnswerTexts.companyRegistered("데이터브릿지 주식회사"), registered.answer)
        assertEquals("/app/profile", registered.navigation!!.to)
    }


    @Test
    fun sendsAnAccountBoundTokenForMembersAndNoPrincipalForGuestsOrWithoutAToolSecret() {
        respondWith(payload("PARTNER_MATCH", answer = "맞는 모집글이 있습니다.", navigation = partners, toolCalls = profileTool))
        service.answer(companyMember, question("나한테 맞는 파트너 모집글 있어?"))
        val principal = lastSent().principal!!
        assertEquals(7L, principal.accountId)
        assertTrue(principal.hasCompany)
        assertTrue(tokens.verify(principal.toolToken, 7L), "발급한 토큰은 같은 계정으로만 통과합니다")
        assertFalse(tokens.verify(principal.toolToken, 8L))

        respondWith(payload("PARTNER_MATCH"))
        service.answer(null, question("나한테 맞는 파트너 모집글 있어?"))
        assertNull(lastSent().principal)

        val withoutSecret = AssistantMessageService(client, savedPrograms, proposals, clock, catalog, AssistantAgentProperties(), AssistantToolTokenService(AssistantAgentProperties(), clock), conversations, actions)
        withoutSecret.answer(companyMember, question("나한테 맞는 파트너 모집글 있어?"))
        assertNull(lastSent().principal)
    }

    @Test
    fun partnerMatchKeepsVerifiedCardsAndNavigation() {
        respondWith(payload(
            "PARTNER_MATCH", answer = "지역과 역할이 맞는 모집글 두 건이에요.", cards = listOf(recruitmentCard("21"), recruitmentCard("22")),
            navigation = partners, toolCalls = profileTool,
        ))
        val answer = service.answer(companyMember, question("나한테 맞는 파트너 모집글 있어?"))
        assertEquals(
            listOf(
                AssistantCard(AssistantCardKind.RECRUITMENT, "21", "AI 실증 참여기관 구합니다", "서울AI 주식회사 · 서울", "지역과 역할이 맞습니다.", "/app/partners/detail?recruitmentId=21"),
                AssistantCard(AssistantCardKind.RECRUITMENT, "22", "AI 실증 참여기관 구합니다", "서울AI 주식회사 · 서울", "지역과 역할이 맞습니다.", "/app/partners/detail?recruitmentId=22"),
            ),
            answer.cards,
        )
        assertEquals(AssistantNavigation("파트너 모집 열기", "/app/partners"), answer.navigation)
    }

    @Test
    fun savedProgramsQuestionUsesProgramCardsRebuiltFromTheIdentity() {
        respondWith(payload("SAVED_PROGRAMS_QUESTION", answer = "원문 질문에서 접수 방법을 확인해 주세요.", cards = listOf(programCard())))
        val answer = service.answer(member, question("담아둔 공고 중 온라인 접수 되는 거 있어?"))
        assertEquals("/app/support-programs/detail?sourceCode=BIZINFO&sourceProgramId=PBLN_000000000000001", answer.cards.single().to)
        assertNull(answer.navigation)
    }

    @Test
    fun rejectsCardsNavigationAndMemberAnswersOutsideTheContract() {
        listOf(
            payload("PARTNER_MATCH", answer = "답", cards = listOf(recruitmentCard(to = "/app/partners/detail?recruitmentId=99"))),
            payload("PARTNER_MATCH", answer = "답", cards = listOf(recruitmentCard(to = "https://evil.example/x"))),
            payload("PARTNER_MATCH", answer = "답", cards = listOf(recruitmentCard(id = "21a", to = "/app/partners/detail?recruitmentId=21a"))),
            payload("PARTNER_MATCH", answer = "답", cards = listOf(recruitmentCard(kind = "COMPANY"))),
            payload("PARTNER_MATCH", answer = "답", cards = listOf(programCard("bizinfo"))),
            payload("PARTNER_MATCH", answer = "답", cards = listOf(recruitmentCard("21"), recruitmentCard("21"))),
            payload("PARTNER_MATCH", answer = "답", cards = (1..6).map { recruitmentCard("$it") }),
            payload("PARTNER_MATCH", answer = "답", cards = listOf(null)),
            payload("PARTNER_MATCH", answer = "답", cards = null),
            payload("PARTNER_MATCH", answer = "답", navigation = AiAssistantNavigationPayload("관리자", "/app/admin")),
            payload("PARTNER_MATCH", answer = "답", navigation = AiAssistantNavigationPayload("", "/app/partners")),
            payload("PARTNER_MATCH", cards = listOf(recruitmentCard())),
            payload("PARTNER_MATCH", navigation = partners),
            payload("OUT_OF_SCOPE", answer = "답", cards = listOf(recruitmentCard())),
            payload("PRODUCT_HELP", answer = "답", citations = listOf("search-score-meaning"), navigation = partners),
            payload("PARTNER_MATCH", answer = "답", searchQuery = "검색어까지"),
            payload("ACCOUNT_STATE", answer = "답"),
        ).forEach { bad ->
            respondWith(bad)
            val error = assertThrows(AiServiceCallException::class.java, { service.answer(companyMember, question()) }, bad.toString())
            assertEquals(AiServiceFailure.INVALID_RESPONSE, error.failure, bad.toString())
        }
    }

    @Test
    fun guestsNeverReceiveMemberDataAnswersFromTheAiService() {
        listOf(
            payload("PARTNER_MATCH", answer = "모집글 두 건이에요.", navigation = partners),
            payload("ACCOUNT_STATE", accountTopic = "SAVED_PROGRAMS", answer = "관심 공고 3건이에요."),
            payload("SAVED_PROGRAMS_QUESTION", answer = "담은 공고 중 하나예요."),
        ).forEach { bad ->
            respondWith(bad)
            val error = assertThrows(AiServiceCallException::class.java, { service.answer(null, question()) }, bad.toString())
            assertEquals(AiServiceFailure.INVALID_RESPONSE, error.failure)
        }
    }

    @Test
    fun guestsGetLoginGuidanceAndMembersWithoutAnAnswerAreSentToTheScreen() {
        respondWith(payload("PARTNER_MATCH"))
        val guest = service.answer(null, question())
        assertEquals(AssistantAnswerTexts.loginRequired(AssistantIntent.PARTNER_MATCH), guest.answer)
        assertNull(guest.navigation)
        assertTrue(guest.cards.isEmpty())

        val withCompany = service.answer(companyMember, question())
        assertEquals(AssistantAnswerTexts.agentNoAnswer(AssistantIntent.PARTNER_MATCH), withCompany.answer)
        assertEquals(AssistantNavigation(AssistantAnswerTexts.OPEN_PARTNERS, "/app/partners"), withCompany.navigation)

        val withoutCompany = service.answer(member, question())
        assertEquals(AssistantAnswerTexts.PARTNER_MATCH_NEEDS_COMPANY, withoutCompany.answer)
        assertEquals(AssistantNavigation(AssistantAnswerTexts.OPEN_PROFILE, "/app/profile"), withoutCompany.navigation)

        respondWith(payload("SAVED_PROGRAMS_QUESTION"))
        assertEquals(AssistantAnswerTexts.loginRequired(AssistantIntent.SAVED_PROGRAMS_QUESTION), service.answer(null, question()).answer)
        assertEquals("/app/saved-programs", service.answer(this.member, question()).navigation!!.to)
    }

    @Test
    fun accountStateUsesTheToolAnswerWhenPresentAndCoreDataOtherwise() {
        respondWith(payload(
            "ACCOUNT_STATE", accountTopic = "SAVED_PROGRAMS", answer = "관심 공고 두 건 중 하나가 9월 30일에 마감돼요.",
            cards = listOf(programCard()), navigation = AiAssistantNavigationPayload("관심 공고함 열기", "/app/saved-programs"),
        ))
        val fromTools = service.answer(member, question("관심 공고 마감 언제야?"))
        assertEquals("관심 공고 두 건 중 하나가 9월 30일에 마감돼요.", fromTools.answer)
        assertEquals(1, fromTools.cards.size)
        Mockito.verifyNoInteractions(savedPrograms)
    }

    @Test
    fun readsHistoryFromTheServerStoreAndAppendsOnlyTheMaskedQuestionWithTheShownAnswer() {
        `when`(conversations.recent(null, conversationId)).thenReturn(listOf(
            AssistantHistoryMessage(AssistantHistoryRole.USER, "점수가 뭐야?"),
            AssistantHistoryMessage(AssistantHistoryRole.ASSISTANT, "점수는 관련도입니다."),
        ))
        respondWith(payload("UNCLEAR", clarification = "어떤 점수가 궁금하세요?"))
        service.answer(null, question("그거 010-1234-5678로 알려줘"))

        assertEquals(listOf("USER" to "점수가 뭐야?", "ASSISTANT" to "점수는 관련도입니다."), lastSent().history.map { it.role to it.content })
        Mockito.verify(conversations).append(null, conversationId, "그거 [전화번호]로 알려줘", "어떤 점수가 궁금하세요?")

        respondWith(payload("PARTNER_MATCH"))
        service.answer(member, question("모집글 찾아줘"))
        Mockito.verify(conversations).append(7L, conversationId, "모집글 찾아줘", AssistantAnswerTexts.PARTNER_MATCH_NEEDS_COMPANY)
    }

    @Test
    fun failedAnswersAreNotRememberedAndStoreFailuresAreNotHiddenAsEmptyConversations() {
        respondWith(payload("PRODUCT_HELP", answer = "인용이 없는 답"))
        assertThrows(AiServiceCallException::class.java) { service.answer(null, question()) }
        Mockito.verify(conversations, Mockito.never()).append(Mockito.isNull(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString())

        `when`(conversations.recent(7L, conversationId)).thenThrow(AssistantConversationStoreException())
        assertThrows(AssistantConversationStoreException::class.java) { service.answer(member, question()) }
        Mockito.verify(client, Mockito.times(1)).answer(any(AiAssistantAnswerRequest::class.java) ?: EMPTY_REQUEST)
    }

    @Test
    fun searchKeepsTheAgentProgramCardsAndAlwaysOffersThePrefilledSearchScreen() {
        respondWith(payload(
            "SEARCH", answer = "모집 중인 창업 지원사업 두 건을 찾았습니다.", searchQuery = "서울 창업 지원금",
            cards = listOf(programCard()), navigation = AiAssistantNavigationPayload("파트너 모집 열기", "/app/partners"),
            toolCalls = listOf(AiAssistantToolCallPayload("find_programs", 30)),
        ))

        val answer = service.answer(member, question("서울 창업 지원금 찾아줘"))

        assertEquals("모집 중인 창업 지원사업 두 건을 찾았습니다.", answer.answer)
        assertEquals("서울 창업 지원금", answer.searchQuery)
        assertEquals(listOf("BIZINFO:PBLN_000000000000001"), answer.cards.map { it.id })
        // 이동 버튼은 모델이 고른 화면이 아니라 검색어를 미리 채우는 검색 화면으로 고정합니다.
        assertEquals(AssistantNavigation("검색 화면에서 찾기", "/app/chat"), answer.navigation)
    }

    @Test
    fun guestSearchDropsTheAgentAnswerAndCards() {
        respondWith(payload("SEARCH", answer = "두 건을 찾았습니다.", searchQuery = "창업 지원금", cards = listOf(programCard())))
        val error = assertThrows(AiServiceCallException::class.java) { service.answer(null, question("창업 지원금 찾아줘")) }
        assertEquals(AiServiceFailure.INVALID_RESPONSE, error.failure)
    }

    @Test
    fun workStatusTopicsUseTheAgentAnswerWithRebuiltCardRoutes() {
        respondWith(payload(
            "ACCOUNT_STATE", answer = "준비 중 1건, 신청 완료 1건입니다.", accountTopic = "APPLICATION_PREPARATIONS",
            cards = listOf(AiAssistantCardPayload("PREPARATION", "31", "서울 AI 실증 지원사업", "준비 중 · 2026-09-16", "아직 준비 중입니다.", "/app/application-preparations/31")),
            navigation = AiAssistantNavigationPayload("신청 준비 열기", "/app/application-preparations"),
            toolCalls = listOf(AiAssistantToolCallPayload("list_application_preparations", 21)),
        ))

        val answer = service.answer(member, question("신청 준비 어디까지 했지?"))

        assertEquals(AssistantAccountTopic.APPLICATION_PREPARATIONS, answer.accountTopic)
        assertEquals(listOf(AssistantCardKind.PREPARATION), answer.cards.map { it.kind })
        assertEquals("/app/application-preparations/31", answer.cards[0].to)
        assertEquals("/app/application-preparations", answer.navigation!!.to)
    }

    @Test
    fun workStatusWithoutAnAgentAnswerOpensTheScreenInsteadOfGuessingNumbers() {
        respondWith(payload("ACCOUNT_STATE", accountTopic = "COMBINATION_REVIEWS"))
        val member = service.answer(member, question("중복 검토 끝났어?"))
        assertEquals(AssistantAnswerTexts.workStatusUnavailable(AssistantAccountTopic.COMBINATION_REVIEWS), member.answer)
        assertEquals("/app/combination-reviews", member.navigation!!.to)

        respondWith(payload("ACCOUNT_STATE", accountTopic = "DAILY_REPORT"))
        val guest = service.answer(null, question("리포트 오고 있어?"))
        assertEquals(AssistantAnswerTexts.loginRequired(AssistantAccountTopic.DAILY_REPORT), guest.answer)
    }

    @Test
    fun rejectsWorkCardsWhoseIdOrRouteDoesNotMatchTheRebuiltRoute() {
        listOf(
            AiAssistantCardPayload("PREPARATION", "31", "제목", null, "이유", "/app/application-preparations/32"),
            AiAssistantCardPayload("PREPARATION", "0", "제목", null, "이유", "/app/application-preparations/0"),
            AiAssistantCardPayload("REVIEW", "41", "제목", null, "이유", "/app/combination-reviews/41?run=77"),
            AiAssistantCardPayload("REVIEW", "BIZINFO:1", "제목", null, "이유", "/app/combination-reviews/BIZINFO:1"),
        ).forEach { card ->
            respondWith(payload(
                "ACCOUNT_STATE", answer = "진행 상황입니다.", accountTopic = "APPLICATION_PREPARATIONS", cards = listOf(card),
                toolCalls = listOf(AiAssistantToolCallPayload("list_application_preparations", 10)),
            ))
            val error = assertThrows(AiServiceCallException::class.java, { service.answer(member, question("신청 준비 어디까지?")) }, card.toString())
            assertEquals(AiServiceFailure.INVALID_RESPONSE, error.failure)
        }
    }

    @Test
    fun executionSuggestionsBecomeConfirmButtonsWithCoreOwnedWording() {
        `when`(supportPrograms.findPresentBySourceAndProgramId("KSTARTUP", "174520")).thenReturn(
            ai.govbiz.core.supportprogram.domain.CatalogSupportProgram(
                SupportProgram("174520", "KSTARTUP", "예비창업패키지", "창업진흥원", "요약", emptyList(), emptyList(), "대상", "기간",
                    null, LocalDate.of(2026, 10, 10), SupportProgramStatus.OPEN, "K-스타트업", "https://example.com", emptyList()),
                "2026-09-10T10:00:00",
            ),
        )
        `when`(savedPrograms.isSaved(7L, "KSTARTUP", "174520")).thenReturn(false)
        respondWith(payload(
            "SEARCH", answer = "예비창업패키지가 모집 중입니다.", searchQuery = "예비창업패키지",
            actions = listOf(AiAssistantActionPayload("SAVE_PROGRAM", "KSTARTUP:174520", null)),
            toolCalls = listOf(AiAssistantToolCallPayload("find_programs", 20)),
        ))

        val answer = service.answer(member, question("예비창업패키지 담아줘"))

        val action = answer.actions.single()
        assertEquals(AssistantActionTexts.SAVE_LABEL, action.label)
        assertEquals("KSTARTUP", action.sourceCode)
        assertEquals("174520", action.sourceProgramId)
    }

    @Test
    fun rejectsExecutionSuggestionsThatBreakTheContract() {
        listOf(
            // 종류가 없거나 모르는 값
            payload("SEARCH", answer = "답", searchQuery = "창업", actions = listOf(AiAssistantActionPayload("DELETE_ACCOUNT", "1", null))),
            payload("SEARCH", answer = "답", searchQuery = "창업", actions = listOf(AiAssistantActionPayload(null, "1", null))),
            // 대상 형식이 카드 id가 아님
            payload("SEARCH", answer = "답", searchQuery = "창업", actions = listOf(AiAssistantActionPayload("SAVE_PROGRAM", "../etc", null))),
            // 진행 단계는 단계 변경에만 붙습니다
            payload("SEARCH", answer = "답", searchQuery = "창업", actions = listOf(AiAssistantActionPayload("SAVE_PROGRAM", "A:1", "APPLIED"))),
            payload("SEARCH", answer = "답", searchQuery = "창업", actions = listOf(AiAssistantActionPayload("SET_PREPARATION_STAGE", "31", null))),
            // 같은 제안 반복, 상한 초과, 필드 자체가 빠진 응답
            payload("SEARCH", answer = "답", searchQuery = "창업", actions = List(2) { AiAssistantActionPayload("SAVE_PROGRAM", "A:1", null) }),
            payload("SEARCH", answer = "답", searchQuery = "창업", actions = List(3) { AiAssistantActionPayload("SAVE_PROGRAM", "A:$it", null) }),
            payload("SEARCH", answer = "답", searchQuery = "창업", actions = null),
            // 도구 의도가 아니거나 답이 없는 응답에는 붙을 수 없습니다
            payload("OUT_OF_SCOPE", answer = "답", actions = listOf(AiAssistantActionPayload("SAVE_PROGRAM", "A:1", null))),
            payload("SEARCH", searchQuery = "창업", actions = listOf(AiAssistantActionPayload("SAVE_PROGRAM", "A:1", null))),
        ).forEach { bad ->
            respondWith(bad)
            val error = assertThrows(AiServiceCallException::class.java, { service.answer(member, question("창업 지원금 담아줘")) }, bad.toString())
            assertEquals(AiServiceFailure.INVALID_RESPONSE, error.failure)
        }
    }

    @Test
    fun guestsNeverGetExecutionButtons() {
        // 비로그인에게 실행 제안이 오는 응답은 답이 있든 없든 계약 위반입니다. Core는 버튼을 지우는 대신 오류로 끝냅니다.
        listOf(
            payload("SEARCH", searchQuery = "창업 지원금", actions = listOf(AiAssistantActionPayload("SAVE_PROGRAM", "KSTARTUP:174520", null))),
            payload("SEARCH", answer = "담아 드릴까요?", searchQuery = "창업 지원금",
                actions = listOf(AiAssistantActionPayload("SAVE_PROGRAM", "KSTARTUP:174520", null))),
        ).forEach { bad ->
            respondWith(bad)
            val error = assertThrows(AiServiceCallException::class.java) { service.answer(null, question("창업 지원금 담아줘")) }
            assertEquals(AiServiceFailure.INVALID_RESPONSE, error.failure)
        }
        Mockito.verifyNoInteractions(supportPrograms)
    }

    private fun streamWith(vararg events: AiAssistantStreamEvent) {
        Mockito.doAnswer { invocation ->
            sentRequests += invocation.getArgument<AiAssistantAnswerRequest>(0)
            val sink = invocation.getArgument<(AiAssistantStreamEvent) -> Unit>(1)
            events.forEach(sink)
            null
        }.`when`(client).stream(any(AiAssistantAnswerRequest::class.java) ?: EMPTY_REQUEST, any() ?: {})
    }

    private fun collect(account: Account?, question: AssistantQuestion): List<AssistantStreamEvent> =
        buildList { service.answerStreaming(account, question) { add(it) } }

    @Test
    fun streamingRelaysProgressAndTextAndEndsWithTheVerifiedAnswer() {
        streamWith(
            AiAssistantStreamEvent.Status("thinking", null),
            AiAssistantStreamEvent.Status("reading", "list_saved_programs"),
            AiAssistantStreamEvent.Text("관심 공고 2건 중 "),
            AiAssistantStreamEvent.Text("가장 빠른 마감은 9월 30일입니다."),
            AiAssistantStreamEvent.Final(payload(
                "ACCOUNT_STATE", answer = "관심 공고 2건 중 가장 빠른 마감은 9월 30일입니다.", accountTopic = "SAVED_PROGRAMS",
                cards = listOf(programCard()), navigation = AiAssistantNavigationPayload("관심 공고함 열기", "/app/saved-programs"),
                toolCalls = listOf(AiAssistantToolCallPayload("list_saved_programs", 15)),
            )),
        )

        val events = collect(member, question("관심 공고 마감 언제야?"))

        assertEquals(
            listOf(AssistantStreamPhase.THINKING, AssistantStreamPhase.READING),
            events.filterIsInstance<AssistantStreamEvent.Status>().map { it.phase },
        )
        assertEquals("관심 공고 2건 중 가장 빠른 마감은 9월 30일입니다.", events.filterIsInstance<AssistantStreamEvent.Text>().joinToString("") { it.delta })
        val last = events.last()
        assertTrue(last is AssistantStreamEvent.Final)
        val answer = (last as AssistantStreamEvent.Final).answer
        assertEquals(listOf("BIZINFO:PBLN_000000000000001"), answer.cards.map { it.id })
        assertEquals("/app/saved-programs", answer.navigation!!.to)
        // 보여 준 답만 다음 질문의 맥락으로 남습니다. 조각이 아니라 확정된 문장을 남깁니다.
        Mockito.verify(conversations).append(7L, conversationId, "관심 공고 마감 언제야?", answer.answer!!)
    }

    @Test
    fun streamingCutsTextAtTheAnswerLimitAndDropsControlCharacters() {
        streamWith(
            AiAssistantStreamEvent.Text("가".repeat(700)),
            AiAssistantStreamEvent.Text("넘친 뒤 조각"),
            AiAssistantStreamEvent.Text("제어 " + 7.toChar() + " 문자"),
            AiAssistantStreamEvent.Final(payload("OUT_OF_SCOPE", answer = "여기서는 할 수 없는 일입니다.")),
        )

        val streamed = collect(null, question("날씨 알려줘")).filterIsInstance<AssistantStreamEvent.Text>().joinToString("") { it.delta }

        assertEquals(AssistantMessageService.ANSWER_MAX, streamed.length)
        assertEquals("가".repeat(AssistantMessageService.ANSWER_MAX), streamed)
    }

    @Test
    fun streamingWithoutAFinalOrWithAFailureEventDoesNotInventAnAnswer() {
        streamWith(AiAssistantStreamEvent.Status("thinking", null), AiAssistantStreamEvent.Text("쓰다 말았습니다"))
        val missing = assertThrows(AiServiceCallException::class.java) { collect(null, question()) }
        assertEquals(AiServiceFailure.INVALID_RESPONSE, missing.failure)

        streamWith(AiAssistantStreamEvent.Failure("execution"))
        assertEquals(AiServiceFailure.UNAVAILABLE, assertThrows(AiServiceCallException::class.java) { collect(null, question()) }.failure)

        streamWith(AiAssistantStreamEvent.Failure("timeout"))
        assertEquals(AiServiceFailure.TIMEOUT, assertThrows(AiServiceCallException::class.java) { collect(null, question()) }.failure)

        // 계약을 어긴 마지막 결과도 그대로 실패입니다.
        streamWith(AiAssistantStreamEvent.Final(payload("PRODUCT_HELP", answer = "인용이 없습니다.")))
        assertEquals(AiServiceFailure.INVALID_RESPONSE, assertThrows(AiServiceCallException::class.java) { collect(null, question()) }.failure)
    }

    private companion object {
        val EMPTY_REQUEST = AiAssistantAnswerRequest("", "", emptyList(), AiAssistantSession(false, false), AiAssistantContext("/", false), emptyList(), null)
    }
}
