package ai.govbiz.core.recruitment.service

import ai.govbiz.core.account.helper.AccountTestHelper
import ai.govbiz.core.recruitment.domain.ProposalStatus
import ai.govbiz.core.recruitment.domain.RecruitmentPostStatus
import ai.govbiz.core.recruitment.helper.RecruitmentTestHelper
import ai.govbiz.core.recruitment.repository.RecruitmentPostPage
import ai.govbiz.core.recruitment.repository.RecruitmentPostRepository
import ai.govbiz.core.recruitment.repository.RecruitmentProposalRepository
import ai.govbiz.core.recruitment.service.exception.ContactInTextException
import ai.govbiz.core.recruitment.service.exception.NotPostOwnerException
import ai.govbiz.core.recruitment.service.exception.RecruitmentClosesOnInvalidException
import ai.govbiz.core.recruitment.service.exception.RecruitmentPostNotFoundException
import ai.govbiz.core.recruitment.service.exception.RecruitmentPostNotOpenException
import ai.govbiz.core.recruitment.service.exception.SupportProgramNotOpenException
import ai.govbiz.core.supportprogram.domain.CatalogSupportProgram
import ai.govbiz.core.supportprogram.domain.SupportProgramStatus
import ai.govbiz.core.supportprogram.repository.SupportProgramRepository
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class RecruitmentPostServiceTest {

    @Mock
    private lateinit var repository: RecruitmentPostRepository

    @Mock
    private lateinit var proposalRepository: RecruitmentProposalRepository

    @Mock
    private lateinit var supportProgramRepository: SupportProgramRepository

    private lateinit var service: RecruitmentPostService

    private val author = AccountTestHelper.account(id = 1L)
    private val otherCompanyViewer = AccountTestHelper.account(id = 2L, email = "other@company.co.kr").let { account ->
        account.copy(company = account.company.copy(id = 2L, businessNumber = "2208162517", companyName = "다른 기업"))
    }

    @BeforeEach
    fun setUp() {
        service = RecruitmentPostService(repository, proposalRepository, supportProgramRepository, AccountTestHelper.FIXED_CLOCK)
    }

    @Test
    fun createsAPostLinkedToAnOpenProgram() {
        stubProgram(RecruitmentTestHelper.program())
        doReturn(RecruitmentTestHelper.stored()).`when`(repository)
            .create(1L, 1L, "BIZINFO", "PBLN_000000091203", RecruitmentTestHelper.draft())

        val result = service.create(author, "BIZINFO", "PBLN_000000091203", RecruitmentTestHelper.draft())

        assertEquals(RecruitmentPostStatus.OPEN, result.status)
        assertTrue(result.isOwner)
        assertEquals("서울 AI 스타트업 실증 지원사업", result.program?.title)
    }

    @Test
    fun rejectsCreationWhenTheProgramIsMissingClosedOrTheDateIsInvalid() {
        assertThrows(SupportProgramNotOpenException::class.java) {
            service.create(author, "BIZINFO", "PBLN_000000091203", RecruitmentTestHelper.draft())
        }

        stubProgram(RecruitmentTestHelper.program(status = SupportProgramStatus.CLOSED))
        assertThrows(SupportProgramNotOpenException::class.java) {
            service.create(author, "BIZINFO", "PBLN_000000091203", RecruitmentTestHelper.draft())
        }

        stubProgram(RecruitmentTestHelper.program())
        assertThrows(RecruitmentClosesOnInvalidException::class.java) {
            service.create(author, "BIZINFO", "PBLN_000000091203", RecruitmentTestHelper.draft(closesOn = LocalDate.of(2026, 10, 1)))
        }
        assertThrows(RecruitmentClosesOnInvalidException::class.java) {
            service.create(author, "BIZINFO", "PBLN_000000091203", RecruitmentTestHelper.draft(closesOn = LocalDate.of(2026, 9, 5)))
        }

        verifyNoInteractions(repository)
    }

    @Test
    fun rejectsContactDetailsBeforeTouchingTheProgramOrRepository() {
        assertThrows(ContactInTextException::class.java) {
            service.create(author, "BIZINFO", "PBLN_000000091203", RecruitmentTestHelper.draft(body = "연락 010-1234-5678"))
        }
        assertThrows(ContactInTextException::class.java) {
            service.update(author, 1L, RecruitmentTestHelper.draft(title = "문의 me@corp.kr"))
        }

        verifyNoInteractions(repository, supportProgramRepository)
    }

    @Test
    fun onlyTheOwningCompanyCanUpdateOrCloseAnOpenPost() {
        doReturn(RecruitmentTestHelper.stored()).`when`(repository).findById(1L)
        stubProgram(RecruitmentTestHelper.program())

        assertThrows(NotPostOwnerException::class.java) {
            service.update(otherCompanyViewer, 1L, RecruitmentTestHelper.draft())
        }
        assertThrows(NotPostOwnerException::class.java) { service.closeEarly(otherCompanyViewer, 1L) }
        verify(repository, never()).closeEarly(1L)

        doReturn(RecruitmentTestHelper.stored()).`when`(repository).update(1L, RecruitmentTestHelper.draft(title = "수정된 제목"))
        val updated = service.update(author, 1L, RecruitmentTestHelper.draft(title = "수정된 제목"))
        assertTrue(updated.isOwner)
    }

    @Test
    fun refusesToChangeAClosedPost() {
        val closed = RecruitmentTestHelper.stored(RecruitmentTestHelper.post(closedEarlyAt = RecruitmentTestHelper.NOW))
        doReturn(closed).`when`(repository).findById(1L)
        stubProgram(RecruitmentTestHelper.program())

        assertThrows(RecruitmentPostNotOpenException::class.java) {
            service.update(author, 1L, RecruitmentTestHelper.draft())
        }
        assertThrows(RecruitmentPostNotOpenException::class.java) { service.closeEarly(author, 1L) }
    }

    @Test
    fun hidesClosedAndHiddenPostsFromEveryoneButTheOwner() {
        val hidden = RecruitmentTestHelper.stored(
            RecruitmentTestHelper.post(hiddenAt = RecruitmentTestHelper.NOW, hiddenReason = "연락처 노출"),
        )
        doReturn(hidden).`when`(repository).findById(1L)
        stubProgram(RecruitmentTestHelper.program())

        assertThrows(RecruitmentPostNotFoundException::class.java) { service.get(1L, null) }
        assertThrows(RecruitmentPostNotFoundException::class.java) { service.get(1L, otherCompanyViewer) }
        assertEquals(RecruitmentPostStatus.HIDDEN, service.get(1L, author).status)
        assertThrows(RecruitmentPostNotFoundException::class.java) { service.get(404L, author) }
    }

    @Test
    fun listsOpenPostsWithProgramSummariesAndOwnerFlags() {
        doReturn(RecruitmentPostPage(listOf(RecruitmentTestHelper.stored()), 0, 20, 1))
            .`when`(repository).findOpenPage(null, null, 0, 20)
        stubProgram(RecruitmentTestHelper.program())

        val anonymous = service.listOpen(null, null, 0, 20, null)
        val asOwner = service.listOpen(null, null, 0, 20, author)

        assertEquals(1, anonymous.totalCount)
        assertFalse(anonymous.posts[0].isOwner)
        assertTrue(asOwner.posts[0].isOwner)
        assertEquals(RecruitmentPostStatus.OPEN, anonymous.posts[0].status)
    }

    @Test
    fun attachesProposalCountsAndTheViewersOwnProposalStatus() {
        doReturn(RecruitmentPostPage(listOf(RecruitmentTestHelper.stored()), 0, 20, 1))
            .`when`(repository).findOpenPage(null, null, 0, 20)
        stubProgram(RecruitmentTestHelper.program())
        doReturn(mapOf(1L to 3)).`when`(proposalRepository).countByPostIds(listOf(1L))
        doReturn(mapOf(1L to RecruitmentTestHelper.storedProposal()))
            .`when`(proposalRepository).findByPostIdsAndCompanyId(listOf(1L), 2L)

        val asProposer = service.listOpen(null, null, 0, 20, otherCompanyViewer).posts.single()
        val anonymous = service.listOpen(null, null, 0, 20, null).posts.single()

        assertEquals(3, asProposer.proposalCount)
        assertEquals(ProposalStatus.PENDING, asProposer.myProposalStatus)
        assertEquals(3, anonymous.proposalCount)
        assertNull(anonymous.myProposalStatus)
        verify(proposalRepository, never()).findByPostIdsAndCompanyId(listOf(1L), 1L)
    }

    @Test
    fun marksMyPostsClosedWhenTheProgramDisappeared() {
        doReturn(listOf(RecruitmentTestHelper.stored())).`when`(repository).findByCompanyId(1L)

        val mine = service.listMine(author)

        assertEquals(RecruitmentPostStatus.CLOSED, mine[0].status)
        assertNull(mine[0].program)
    }

    private fun stubProgram(program: ai.govbiz.core.supportprogram.domain.SupportProgram) {
        doReturn(CatalogSupportProgram(program, "2026-08-21 10:00:00")).`when`(supportProgramRepository)
            .findPresentBySourceAndProgramId("BIZINFO", "PBLN_000000091203")
    }
}
