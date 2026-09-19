package ai.govbiz.core.assistant.service

import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.account.domain.AccountRole
import ai.govbiz.core.account.domain.CompanySummary
import ai.govbiz.core.account.repository.AccountRepository
import ai.govbiz.core.applicationpreparation.domain.ApplicationFormFieldDefinition
import ai.govbiz.core.applicationpreparation.domain.ApplicationFormManifest
import ai.govbiz.core.applicationpreparation.domain.ApplicationFormSectionDefinition
import ai.govbiz.core.applicationpreparation.domain.ApplicationPreparationSummary
import ai.govbiz.core.applicationpreparation.domain.ApplicationProgressStage
import ai.govbiz.core.applicationpreparation.domain.ApplicationServiceField
import ai.govbiz.core.applicationpreparation.service.ApplicationPreparationService
import ai.govbiz.core.applicationpreparation.service.dto.ApplicationPreparationListItemResult
import ai.govbiz.core.applicationpreparation.service.dto.ApplicationPreparationPageResult
import ai.govbiz.core.assistant.service.exception.AssistantToolAccountMissingException
import ai.govbiz.core.combinationreview.domain.CombinationReviewDraft
import ai.govbiz.core.combinationreview.domain.CombinationReviewInput
import ai.govbiz.core.combinationreview.domain.CombinationReviewSummary
import ai.govbiz.core.combinationreview.domain.ReviewProgramIdentity
import ai.govbiz.core.combinationreview.domain.ReviewRunStatus
import ai.govbiz.core.combinationreview.domain.ReviewRunSummary
import ai.govbiz.core.combinationreview.domain.SelectedReviewProgram
import ai.govbiz.core.combinationreview.domain.StoredCombinationReview
import ai.govbiz.core.combinationreview.service.CombinationReviewRunService
import ai.govbiz.core.combinationreview.service.CombinationReviewService
import ai.govbiz.core.combinationreview.service.dto.CombinationReviewPageResult
import ai.govbiz.core.dailyreport.service.DailyReportService
import ai.govbiz.core.dailyreport.service.DailyReportSubscriptionService
import ai.govbiz.core.dailyreport.service.dto.DailyReportSettingsResult
import ai.govbiz.core.partner.domain.PartnerProposalBox
import ai.govbiz.core.supportprogram.domain.CatalogSupportProgram
import ai.govbiz.core.supportprogram.domain.SupportProgram
import ai.govbiz.core.supportprogram.domain.SupportProgramStatus
import ai.govbiz.core.supportprogram.repository.SupportProgramRepository
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

/** 가이드가 읽는 작업 상태입니다. 진행 단계·실행 상태만 담고 작성 내용·판정·상대 기업 정보는 담지 않습니다. */
class AssistantWorkToolServiceTest {
    private val accounts = Mockito.mock(AccountRepository::class.java)
    private val preparations = Mockito.mock(ApplicationPreparationService::class.java)
    private val reviews = Mockito.mock(CombinationReviewService::class.java)
    private val reviewRuns = Mockito.mock(CombinationReviewRunService::class.java)
    private val dailyReports = Mockito.mock(DailyReportService::class.java)
    private val subscriptions = Mockito.mock(DailyReportSubscriptionService::class.java)
    private val proposals = Mockito.mock(ai.govbiz.core.partner.service.PartnerProposalService::class.java)
    private val programs = Mockito.mock(SupportProgramRepository::class.java)
    private val service = AssistantWorkToolService(
        accounts, preparations, reviews, reviewRuns, dailyReports, subscriptions, proposals, programs,
    )

    private val member = Account(7L, "member@example.com", AccountRole.USER, NOW, null, NOW)
    private val companyMember = member.copy(company = CompanySummary(3L, "데이터브릿지 주식회사", "1248100998"))

    @Test
    fun applicationPreparationsCarryTheStageAndTheRevisionTheStageCardNeeds() {
        `when`(accounts.findById(7L)).thenReturn(member)
        `when`(preparations.listOwned(member, null, AssistantWorkToolService.PREPARATION_MAX)).thenReturn(
            ApplicationPreparationPageResult(listOf(ApplicationPreparationListItemResult(summary(), form())), null),
        )

        val found = service.applicationPreparations(7L)

        assertEquals(1, found.size)
        assertEquals(31L, found[0].id)
        assertEquals("PREPARING", found[0].progressStage)
        assertEquals(2L, found[0].progressRevision)
        assertEquals("지원사업", found[0].programTitle)
        assertEquals(LocalDate.of(2026, 9, 16), found[0].updatedAt)
    }

    @Test
    fun combinationReviewsCarryProgramTitlesAndOnlyTheLatestRunState() {
        `when`(accounts.findById(7L)).thenReturn(member)
        `when`(reviews.listOwned(member, null, AssistantWorkToolService.REVIEW_MAX)).thenReturn(
            CombinationReviewPageResult(listOf(CombinationReviewSummary(41L, "혁신바우처와 R&D", 3L, NOW, NOW)), null),
        )
        `when`(reviews.findOwned(member, 41L)).thenReturn(storedReview())
        `when`(reviewRuns.listOwned(member, 41L, null, 1)).thenReturn(
            listOf(ReviewRunSummary(77L, 3L, ReviewRunStatus.SUCCEEDED, null, LocalDateTime.of(2026, 9, 15, 10, 0), null)),
        )
        `when`(programs.findPresentBySourceAndProgramId("BIZINFO", "PBLN_000000000000001")).thenReturn(catalogProgram("혁신바우처"))
        // 내려간 공고는 제목 대신 식별자로 남기고 검토 자체는 그대로 보여 줍니다.
        `when`(programs.findPresentBySourceAndProgramId("KSTARTUP", "174520")).thenReturn(null)

        val found = service.combinationReviews(7L)

        assertEquals(listOf("혁신바우처", "KSTARTUP:174520"), found[0].programTitles)
        assertEquals("SUCCEEDED", found[0].latestRunStatus)
        assertEquals(77L, found[0].latestRunId)
        assertEquals(LocalDate.of(2026, 9, 15), found[0].latestRunDate)
    }

    @Test
    fun dailyReportStatusNeedsBothTheSchedulerAndMailToCountAsServiceEnabled() {
        `when`(accounts.findById(7L)).thenReturn(member)
        `when`(subscriptions.settings(member)).thenReturn(DailyReportSettingsResult("AI 실증", true, true, false, 8, true))
        `when`(dailyReports.latest(member)).thenReturn(null)

        val status = service.dailyReportStatus(7L)

        assertTrue(status.enabled)
        assertFalse(status.serviceEnabled, "메일 발송이 막혀 있으면 서비스가 켜진 것으로 보지 않습니다")
        assertNull(status.latestReportDate)
        assertFalse(status.toString().contains("@"), "수신 이메일 주소는 어디에도 없습니다")
    }

    @Test
    fun proposalSummaryIsEmptyWithoutACompanyAndOtherwiseCountsPendingOnesOnly() {
        `when`(accounts.findById(7L)).thenReturn(member)
        val none = service.proposalSummary(7L)
        assertFalse(none.hasCompany)
        assertEquals(0, none.receivedPending)
        Mockito.verifyNoInteractions(proposals)

        `when`(accounts.findById(8L)).thenReturn(companyMember)
        `when`(proposals.findBox(companyMember, PartnerProposalBox.RECEIVED)).thenReturn(emptyList())
        `when`(proposals.findBox(companyMember, PartnerProposalBox.SENT)).thenReturn(emptyList())
        val summary = service.proposalSummary(8L)
        assertTrue(summary.hasCompany)
        assertNull(summary.earliestExpiryDate)
    }

    @Test
    fun aMissingAccountFailsTheToolCallInsteadOfAnsweringWithEmptyData() {
        `when`(accounts.findById(9L)).thenReturn(null)
        assertThrows(AssistantToolAccountMissingException::class.java) { service.applicationPreparations(9L) }
        assertThrows(AssistantToolAccountMissingException::class.java) { service.combinationReviews(9L) }
        assertThrows(AssistantToolAccountMissingException::class.java) { service.dailyReportStatus(9L) }
        assertThrows(AssistantToolAccountMissingException::class.java) { service.proposalSummary(9L) }
    }

    private fun summary() = ApplicationPreparationSummary(
        31L, 4L, ApplicationProgressStage.PREPARING, 2L, LocalDateTime.of(2026, 9, 16, 9, 0),
        "BIZINFO", "PBLN_000000000000001", "verified-form-v1", ApplicationServiceField.TECHNICAL_SUPPORT,
        NOW, LocalDateTime.of(2026, 9, 16, 9, 0),
    )

    private fun form() = ApplicationFormManifest(
        1, "verified-form-v1", "BIZINFO", "PBLN_000000000000001", "지원사업", "사업계획서",
        "https://www.bizinfo.go.kr/form", "form.hwpx", 1, "a".repeat(64), "SOURCE_HASH_AND_LOCATORS_VERIFIED", false,
        listOf(ApplicationServiceField.TECHNICAL_SUPPORT),
        listOf(ApplicationFormSectionDefinition(
            "company-overview", "기업 개요", "문단 1", "기업을 설명합니다.",
            listOf(ApplicationFormFieldDefinition("company-name", "업체명", "업체명을 입력합니다.", true)),
        )),
    )

    private fun storedReview() = StoredCombinationReview(
        41L, 7L, 3L,
        CombinationReviewDraft(
            "혁신바우처와 R&D",
            CombinationReviewInput(listOf(
                SelectedReviewProgram(ReviewProgramIdentity("BIZINFO", "PBLN_000000000000001")),
                SelectedReviewProgram(ReviewProgramIdentity("KSTARTUP", "174520")),
            )),
        ),
        NOW, NOW,
    )

    private fun catalogProgram(title: String) = CatalogSupportProgram(
        SupportProgram(
            "PBLN_000000000000001", "BIZINFO", title, "중소벤처기업부", "요약", emptyList(), emptyList(), "대상", "기간",
            null, LocalDate.of(2026, 9, 30), SupportProgramStatus.OPEN, "기업마당", "https://example.com", emptyList(),
        ),
        "2026-09-10T10:00:00",
    )

    private companion object {
        private val NOW = LocalDateTime.of(2026, 9, 10, 10, 0)
    }
}
