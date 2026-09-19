package ai.govbiz.core.assistant.service

import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.account.domain.AccountRole
import ai.govbiz.core.applicationpreparation.domain.ApplicationFormFieldDefinition
import ai.govbiz.core.applicationpreparation.domain.ApplicationFormManifest
import ai.govbiz.core.applicationpreparation.domain.ApplicationFormSectionDefinition
import ai.govbiz.core.applicationpreparation.domain.ApplicationProgressStage
import ai.govbiz.core.applicationpreparation.domain.ApplicationServiceField
import ai.govbiz.core.applicationpreparation.domain.NewApplicationPreparation
import ai.govbiz.core.applicationpreparation.domain.StoredApplicationPreparation
import ai.govbiz.core.applicationpreparation.service.ApplicationPreparationService
import ai.govbiz.core.applicationpreparation.service.dto.ApplicationPreparationDetailResult
import ai.govbiz.core.assistant.domain.AssistantActionChoice
import ai.govbiz.core.assistant.domain.AssistantActionKind
import ai.govbiz.core.combinationreview.domain.CombinationReviewDraft
import ai.govbiz.core.combinationreview.domain.CombinationReviewInput
import ai.govbiz.core.combinationreview.domain.ReviewProgramIdentity
import ai.govbiz.core.combinationreview.domain.SelectedReviewProgram
import ai.govbiz.core.combinationreview.domain.StoredCombinationReview
import ai.govbiz.core.combinationreview.service.CombinationReviewService
import ai.govbiz.core.supportprogram.domain.CatalogSupportProgram
import ai.govbiz.core.supportprogram.domain.SupportProgram
import ai.govbiz.core.supportprogram.domain.SupportProgramStatus
import ai.govbiz.core.supportprogram.repository.SupportProgramRepository
import ai.govbiz.core.supportprogram.service.saved.SavedSupportProgramService
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

/**
 * 실행 제안 검증입니다. 가이드는 실행하지 않고, Core가 대상이 지금도 내 것인지 확인한 뒤 확인 버튼 문구를 직접 만듭니다.
 * 대상이 사라졌거나 이미 원하는 상태면 그 버튼만 빠지고 답은 그대로 나갑니다.
 */
class AssistantActionServiceTest {
    private val savedPrograms = Mockito.mock(SavedSupportProgramService::class.java)
    private val supportPrograms = Mockito.mock(SupportProgramRepository::class.java)
    private val preparations = Mockito.mock(ApplicationPreparationService::class.java)
    private val reviews = Mockito.mock(CombinationReviewService::class.java)
    private val service = AssistantActionService(savedPrograms, supportPrograms, preparations, reviews)
    private val member = Account(7L, "member@example.com", AccountRole.USER, NOW, null, NOW)

    private fun choice(kind: AssistantActionKind, targetId: String, stage: String? = null) = AssistantActionChoice(kind, targetId, stage)

    @Test
    fun saveAndUnsaveCarryTheProgramIdentityAndAConfirmTextBuiltFromTheStoredTitle() {
        `when`(supportPrograms.findPresentBySourceAndProgramId("BIZINFO", "PBLN_1")).thenReturn(program())
        `when`(savedPrograms.isSaved(7L, "BIZINFO", "PBLN_1")).thenReturn(false)

        val save = service.verify(member, listOf(choice(AssistantActionKind.SAVE_PROGRAM, "BIZINFO:PBLN_1"))).single()

        assertEquals(AssistantActionTexts.SAVE_LABEL, save.label)
        assertEquals(AssistantActionTexts.saveConfirm("서울 AI 실증 지원사업"), save.confirm)
        assertEquals("BIZINFO", save.sourceCode)
        assertEquals("PBLN_1", save.sourceProgramId)
        assertNull(save.to)

        `when`(savedPrograms.isSaved(7L, "BIZINFO", "PBLN_1")).thenReturn(true)
        val unsave = service.verify(member, listOf(choice(AssistantActionKind.UNSAVE_PROGRAM, "BIZINFO:PBLN_1"))).single()
        assertEquals(AssistantActionTexts.UNSAVE_LABEL, unsave.label)
    }

    @Test
    fun programActionsThatNoLongerMatchTheStoredStateAreDropped() {
        `when`(supportPrograms.findPresentBySourceAndProgramId("BIZINFO", "PBLN_1")).thenReturn(program())
        // 이미 담은 공고를 또 담자는 제안, 담지 않은 공고를 빼자는 제안은 버튼으로 만들지 않습니다.
        `when`(savedPrograms.isSaved(7L, "BIZINFO", "PBLN_1")).thenReturn(true)
        assertTrue(service.verify(member, listOf(choice(AssistantActionKind.SAVE_PROGRAM, "BIZINFO:PBLN_1"))).isEmpty())
        `when`(savedPrograms.isSaved(7L, "BIZINFO", "PBLN_1")).thenReturn(false)
        assertTrue(service.verify(member, listOf(choice(AssistantActionKind.UNSAVE_PROGRAM, "BIZINFO:PBLN_1"))).isEmpty())
        // 내려간 공고, 형식이 다른 대상, 비로그인도 버튼이 없습니다.
        `when`(supportPrograms.findPresentBySourceAndProgramId("BIZINFO", "GONE")).thenReturn(null)
        assertTrue(service.verify(member, listOf(choice(AssistantActionKind.SAVE_PROGRAM, "BIZINFO:GONE"))).isEmpty())
        assertTrue(service.verify(member, listOf(choice(AssistantActionKind.SAVE_PROGRAM, "31"))).isEmpty())
        assertTrue(service.verify(null, listOf(choice(AssistantActionKind.SAVE_PROGRAM, "BIZINFO:PBLN_1"))).isEmpty())
    }

    @Test
    fun startingApplicationPreparationOpensTheFormScreenForThatProgramInsteadOfCreatingIt() {
        `when`(supportPrograms.findPresentBySourceAndProgramId("BIZINFO", "PBLN_1")).thenReturn(program())

        val action = service.verify(member, listOf(choice(AssistantActionKind.START_APPLICATION_PREPARATION, "BIZINFO:PBLN_1"))).single()

        assertEquals("/app/application-preparations/new?sourceCode=BIZINFO&sourceProgramId=PBLN_1", action.to)
        Mockito.verifyNoInteractions(preparations)
    }

    @Test
    fun stageActionNeedsAnOwnedPreparationAndADifferentStage() {
        `when`(preparations.findOwned(member, 31L)).thenReturn(detail(ApplicationProgressStage.PREPARING))

        val action = service.verify(member, listOf(choice(AssistantActionKind.SET_PREPARATION_STAGE, "31", "APPLIED"))).single()

        assertEquals(31L, action.preparationId)
        assertEquals("APPLIED", action.stage)
        assertTrue(action.label.contains("지원 완료"))
        assertTrue(action.confirm.contains("지원사업"))

        assertTrue(service.verify(member, listOf(choice(AssistantActionKind.SET_PREPARATION_STAGE, "31", "PREPARING"))).isEmpty())
        assertTrue(service.verify(member, listOf(choice(AssistantActionKind.SET_PREPARATION_STAGE, "31", "UNKNOWN_STAGE"))).isEmpty())
        `when`(preparations.findOwned(member, 99L)).thenThrow(IllegalStateException("not owned"))
        assertTrue(service.verify(member, listOf(choice(AssistantActionKind.SET_PREPARATION_STAGE, "99", "APPLIED"))).isEmpty())
    }

    @Test
    fun runReviewActionNeedsAnOwnedReviewAndKeepsAtMostTwoActions() {
        `when`(reviews.findOwned(member, 41L)).thenReturn(storedReview())

        val action = service.verify(member, listOf(choice(AssistantActionKind.RUN_COMBINATION_REVIEW, "41"))).single()
        assertEquals(41L, action.reviewId)
        assertEquals(AssistantActionTexts.RUN_REVIEW_LABEL, action.label)

        `when`(supportPrograms.findPresentBySourceAndProgramId("BIZINFO", "PBLN_1")).thenReturn(program())
        `when`(savedPrograms.isSaved(7L, "BIZINFO", "PBLN_1")).thenReturn(false)
        `when`(preparations.findOwned(member, 31L)).thenReturn(detail(ApplicationProgressStage.PREPARING))
        val many = service.verify(member, listOf(
            choice(AssistantActionKind.SAVE_PROGRAM, "BIZINFO:PBLN_1"),
            choice(AssistantActionKind.RUN_COMBINATION_REVIEW, "41"),
            choice(AssistantActionKind.SET_PREPARATION_STAGE, "31", "APPLIED"),
        ))
        assertEquals(AssistantActionService.MAX_ACTIONS, many.size)
    }

    private fun program() = CatalogSupportProgram(
        SupportProgram(
            "PBLN_1", "BIZINFO", "서울 AI 실증 지원사업", "서울경제진흥원", "요약", emptyList(), emptyList(), "대상", "기간",
            null, LocalDate.of(2026, 9, 30), SupportProgramStatus.OPEN, "기업마당", "https://example.com", emptyList(),
        ),
        "2026-09-10T10:00:00",
    )

    private fun detail(stage: ApplicationProgressStage) = ApplicationPreparationDetailResult(
        StoredApplicationPreparation(
            31L, 7L, 4L, stage, 2L, NOW,
            NewApplicationPreparation("BIZINFO", "PBLN_1", "verified-form-v1", ApplicationServiceField.TECHNICAL_SUPPORT),
            NOW, NOW,
        ),
        form(),
    )

    private fun form() = ApplicationFormManifest(
        1, "verified-form-v1", "BIZINFO", "PBLN_1", "지원사업", "사업계획서",
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
                SelectedReviewProgram(ReviewProgramIdentity("BIZINFO", "PBLN_1")),
                SelectedReviewProgram(ReviewProgramIdentity("KSTARTUP", "174520")),
            )),
        ),
        NOW, NOW,
    )

    private companion object {
        private val NOW = LocalDateTime.of(2026, 9, 10, 10, 0)
    }
}
