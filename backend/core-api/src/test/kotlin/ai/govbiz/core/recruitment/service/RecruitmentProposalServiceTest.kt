package ai.govbiz.core.recruitment.service

import ai.govbiz.core.account.helper.AccountTestHelper
import ai.govbiz.core.account.repository.AccountRepository
import ai.govbiz.core.recruitment.domain.ProposalDecision
import ai.govbiz.core.recruitment.domain.ProposalStatus
import ai.govbiz.core.recruitment.helper.RecruitmentTestHelper
import ai.govbiz.core.recruitment.repository.RecruitmentPostRepository
import ai.govbiz.core.recruitment.repository.RecruitmentProposalRepository
import ai.govbiz.core.recruitment.service.exception.ContactInTextException
import ai.govbiz.core.recruitment.service.exception.NotPostOwnerException
import ai.govbiz.core.recruitment.service.exception.NotProposalOwnerException
import ai.govbiz.core.recruitment.service.exception.OwnPostProposalException
import ai.govbiz.core.recruitment.service.exception.ProposalAlreadyExistsException
import ai.govbiz.core.recruitment.service.exception.ProposalNotFoundException
import ai.govbiz.core.recruitment.service.exception.ProposalNotPendingException
import ai.govbiz.core.recruitment.service.exception.RecruitmentPostNotFoundException
import ai.govbiz.core.recruitment.service.exception.RecruitmentPostNotOpenException
import ai.govbiz.core.supportprogram.domain.CatalogSupportProgram
import ai.govbiz.core.supportprogram.repository.SupportProgramRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.dao.DuplicateKeyException

@ExtendWith(MockitoExtension::class)
class RecruitmentProposalServiceTest {

    @Mock
    private lateinit var proposalRepository: RecruitmentProposalRepository

    @Mock
    private lateinit var postRepository: RecruitmentPostRepository

    @Mock
    private lateinit var supportProgramRepository: SupportProgramRepository

    @Mock
    private lateinit var accountRepository: AccountRepository

    private lateinit var service: RecruitmentProposalService

    private val owner = AccountTestHelper.account(id = 1L)
    private val proposer = AccountTestHelper.account(id = 2L, email = "partner@vision.co.kr")
        .let { it.copy(company = RecruitmentTestHelper.otherCompany()) }
    private val stranger = AccountTestHelper.account(id = 3L, email = "third@else.co.kr")
        .let { it.copy(company = it.company.copy(id = 3L, businessNumber = "1058144880", companyName = "제3의 기업")) }

    @BeforeEach
    fun setUp() {
        service = RecruitmentProposalService(
            proposalRepository,
            postRepository,
            supportProgramRepository,
            accountRepository,
            AccountTestHelper.FIXED_CLOCK,
        )
    }

    @Test
    fun sendsAProposalToAnOpenPostOfAnotherCompany() {
        stubOpenPost()
        doReturn(RecruitmentTestHelper.storedProposal()).`when`(proposalRepository)
            .create(1L, 2L, 2L, RecruitmentTestHelper.proposal().message)

        val result = service.send(proposer, 1L, RecruitmentTestHelper.proposal().message)

        assertEquals(ProposalStatus.PENDING, result.status)
        assertEquals("비전솔루션", result.company.companyName)
        assertEquals("삼성전자(주)", result.postCompany.companyName)
        assertNull(result.contactEmail)
    }

    @Test
    fun rejectsOwnPostsClosedPostsMissingPostsAndDuplicates() {
        stubOpenPost()
        assertThrows(OwnPostProposalException::class.java) { service.send(owner, 1L, "우리 글에 제안") }

        doThrow(DuplicateKeyException("uk_recruitment_proposal_post_company")).`when`(proposalRepository)
            .create(1L, 2L, 2L, "두 번째 제안")
        assertThrows(ProposalAlreadyExistsException::class.java) { service.send(proposer, 1L, "두 번째 제안") }

        val closed = RecruitmentTestHelper.stored(RecruitmentTestHelper.post(closedEarlyAt = RecruitmentTestHelper.NOW))
        doReturn(closed).`when`(postRepository).findById(1L)
        assertThrows(RecruitmentPostNotOpenException::class.java) { service.send(proposer, 1L, "마감된 글에 제안") }

        doReturn(null).`when`(postRepository).findById(404L)
        assertThrows(RecruitmentPostNotFoundException::class.java) { service.send(proposer, 404L, "없는 글에 제안") }
    }

    @Test
    fun rejectsContactDetailsBeforeReadingAnything() {
        assertThrows(ContactInTextException::class.java) { service.send(proposer, 1L, "연락 010-1234-5678") }

        verifyNoInteractions(postRepository, proposalRepository, supportProgramRepository)
    }

    @Test
    fun onlyTheOwnerListsReceivedProposalsAndSeesTheEmailOnceAccepted() {
        stubOpenPost()
        val accepted = RecruitmentTestHelper.storedProposal(
            RecruitmentTestHelper.proposal(decision = ProposalDecision.ACCEPTED, decidedAt = RecruitmentTestHelper.NOW),
        )
        doReturn(listOf(accepted, RecruitmentTestHelper.storedProposal(RecruitmentTestHelper.proposal(id = 2L, companyId = 3L))))
            .`when`(proposalRepository).findByPostId(1L)

        assertThrows(NotPostOwnerException::class.java) { service.listReceived(proposer, 1L) }

        val received = service.listReceived(owner, 1L)

        assertEquals(listOf(ProposalStatus.ACCEPTED, ProposalStatus.PENDING), received.map { it.status })
        assertEquals("partner@vision.co.kr", received[0].contactEmail)
        assertNull(received[1].contactEmail)
        verifyNoInteractions(accountRepository)
    }

    @Test
    fun acceptsAndDeclinesOnlyPendingProposalsOfOwnPosts() {
        stubOpenPost()
        doReturn(RecruitmentTestHelper.storedProposal()).`when`(proposalRepository).findById(1L)
        doReturn(true).`when`(proposalRepository).decide(1L, ProposalDecision.ACCEPTED)
        val accepted = RecruitmentTestHelper.storedProposal(
            RecruitmentTestHelper.proposal(decision = ProposalDecision.ACCEPTED, decidedAt = RecruitmentTestHelper.NOW),
        )

        assertThrows(NotPostOwnerException::class.java) { service.accept(proposer, 1L) }
        assertThrows(NotPostOwnerException::class.java) { service.decline(stranger, 1L) }

        doReturn(RecruitmentTestHelper.storedProposal(), accepted).`when`(proposalRepository).findById(1L)
        val result = service.accept(owner, 1L)

        assertEquals(ProposalStatus.ACCEPTED, result.status)
        assertEquals("partner@vision.co.kr", result.contactEmail)
    }

    @Test
    fun refusesToDecideExpiredClosedOrAlreadyDecidedProposals() {
        stubOpenPost()
        val expired = RecruitmentTestHelper.storedProposal(RecruitmentTestHelper.proposal(createdAt = RecruitmentTestHelper.NOW.minusDays(8)))
        doReturn(expired).`when`(proposalRepository).findById(1L)
        assertThrows(ProposalNotPendingException::class.java) { service.accept(owner, 1L) }
        verify(proposalRepository, never()).decide(1L, ProposalDecision.ACCEPTED)

        doReturn(RecruitmentTestHelper.storedProposal()).`when`(proposalRepository).findById(1L)
        doReturn(false).`when`(proposalRepository).decide(1L, ProposalDecision.DECLINED)
        assertThrows(ProposalNotPendingException::class.java) { service.decline(owner, 1L) }

        doReturn(null).`when`(proposalRepository).findById(404L)
        assertThrows(ProposalNotFoundException::class.java) { service.accept(owner, 404L) }
    }

    @Test
    fun onlyTheProposerWithdrawsAPendingProposal() {
        stubOpenPost()
        doReturn(RecruitmentTestHelper.storedProposal()).`when`(proposalRepository).findById(1L)

        assertThrows(NotProposalOwnerException::class.java) { service.withdraw(owner, 1L) }
        assertThrows(NotProposalOwnerException::class.java) { service.withdraw(stranger, 1L) }

        val withdrawn = RecruitmentTestHelper.storedProposal(
            RecruitmentTestHelper.proposal(decision = ProposalDecision.WITHDRAWN, decidedAt = RecruitmentTestHelper.NOW),
        )
        doReturn(RecruitmentTestHelper.storedProposal(), withdrawn).`when`(proposalRepository).findById(1L)
        doReturn(true).`when`(proposalRepository).decide(1L, ProposalDecision.WITHDRAWN)

        assertEquals(ProposalStatus.WITHDRAWN, service.withdraw(proposer, 1L).status)
    }

    @Test
    fun listsSentProposalsWithThePostSummaryAndTheAuthorsEmailOnceAccepted() {
        stubOpenPost()
        val accepted = RecruitmentTestHelper.storedProposal(
            RecruitmentTestHelper.proposal(decision = ProposalDecision.ACCEPTED, decidedAt = RecruitmentTestHelper.NOW),
        )
        doReturn(listOf(accepted)).`when`(proposalRepository).findByCompanyId(2L)
        doReturn(owner).`when`(accountRepository).findById(1L)

        val sent = service.listSent(proposer)

        assertEquals(1, sent.size)
        assertEquals("AI 실증 과제 데이터 구축·라벨링 참여기관 구합니다", sent[0].post.draft.title)
        assertEquals("manager@company.co.kr", sent[0].contactEmail)
    }

    private fun stubOpenPost() {
        doReturn(RecruitmentTestHelper.stored()).`when`(postRepository).findById(1L)
        doReturn(CatalogSupportProgram(RecruitmentTestHelper.program(), "2026-08-21 10:00:00"))
            .`when`(supportProgramRepository).findPresentBySourceAndProgramId("BIZINFO", "PBLN_000000091203")
    }
}
