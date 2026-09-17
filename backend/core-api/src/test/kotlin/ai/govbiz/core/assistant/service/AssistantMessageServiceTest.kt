package ai.govbiz.core.assistant.service

import ai.govbiz.core._common.exception.AiServiceCallException
import ai.govbiz.core._common.exception.AiServiceFailure
import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.account.domain.AccountRole
import ai.govbiz.core.account.domain.CompanySummary
import ai.govbiz.core.assistant.client.AiAssistantClient
import ai.govbiz.core.assistant.client.dto.AiAssistantAnswerPayload
import ai.govbiz.core.assistant.client.dto.AiAssistantAnswerRequest
import ai.govbiz.core.assistant.client.dto.AiAssistantCardPayload
import ai.govbiz.core.assistant.client.dto.AiAssistantContext
import ai.govbiz.core.assistant.client.dto.AiAssistantNavigationPayload
import ai.govbiz.core.assistant.client.dto.AiAssistantSession
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
import ai.govbiz.core.assistant.domain.AssistantScreenContext
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
import ai.govbiz.core.supportprogram.domain.SavedSupportProgram
import ai.govbiz.core.supportprogram.domain.SupportProgram
import ai.govbiz.core.supportprogram.domain.SupportProgramStatus
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
    private val service = AssistantMessageService(client, savedPrograms, proposals, clock, catalog, properties, tokens)
    private val member = Account(7L, "member@example.com", AccountRole.USER, LocalDateTime.of(2026, 9, 1, 9, 0), null, LocalDateTime.of(2026, 9, 1, 9, 0))
    private val companyMember = member.copy(company = CompanySummary(3L, "데이터브릿지 주식회사", "1248100998"))

    private fun question(message: String = "점수가 무슨 뜻이야?", programSelected: Boolean = false, history: List<AssistantHistoryMessage> = emptyList()) =
        AssistantQuestion(message, history, AssistantScreenContext("/app/chat", programSelected))

    private fun payload(
        intent: String, answer: String? = null, citations: List<String?>? = emptyList(), clarification: String? = null,
        searchQuery: String? = null, accountTopic: String? = null, schemaVersion: String? = "govbiz-assistant-v2",
        cards: List<AiAssistantCardPayload?>? = emptyList(), navigation: AiAssistantNavigationPayload? = null,
        toolCalls: List<AiAssistantToolCallPayload?>? = emptyList(),
    ) = AiAssistantAnswerPayload(schemaVersion, intent, answer, citations, clarification, searchQuery, accountTopic, cards, navigation, toolCalls)

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
        service.answer(companyMember, question("사업자번호 123-45-67890으로 조회해 줘", history = history))
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

        val withoutSecret = AssistantMessageService(client, savedPrograms, proposals, clock, catalog, AssistantAgentProperties(), AssistantToolTokenService(AssistantAgentProperties(), clock))
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

    private companion object {
        val EMPTY_REQUEST = AiAssistantAnswerRequest("", "", emptyList(), AiAssistantSession(false, false), AiAssistantContext("/", false), emptyList(), null)
    }
}
