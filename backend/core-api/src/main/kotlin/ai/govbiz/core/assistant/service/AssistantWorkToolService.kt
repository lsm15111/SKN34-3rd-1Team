package ai.govbiz.core.assistant.service

import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.account.repository.AccountRepository
import ai.govbiz.core.applicationpreparation.service.ApplicationPreparationService
import ai.govbiz.core.assistant.domain.AssistantApplicationPreparationSummary
import ai.govbiz.core.assistant.domain.AssistantCombinationReviewSummary
import ai.govbiz.core.assistant.domain.AssistantDailyReportStatus
import ai.govbiz.core.assistant.domain.AssistantProposalSummary
import ai.govbiz.core.assistant.service.exception.AssistantToolAccountMissingException
import ai.govbiz.core.combinationreview.service.CombinationReviewRunService
import ai.govbiz.core.combinationreview.service.CombinationReviewService
import ai.govbiz.core.dailyreport.service.DailyReportService
import ai.govbiz.core.dailyreport.service.DailyReportSubscriptionService
import ai.govbiz.core.partner.domain.PartnerProposalBox
import ai.govbiz.core.partner.domain.PartnerProposalStatus
import ai.govbiz.core.partner.service.PartnerProposalService
import ai.govbiz.core.supportprogram.repository.SupportProgramRepository
import org.springframework.stereotype.Service

/**
 * 가이드가 읽는 "내 작업 상태"입니다. 신청 준비·중복 검토·리포트·제안함을 각 기능의 기존 Service로만 읽습니다.
 * 전부 읽기 전용이고, 판정 내용·본문·상대 기업 정보처럼 답에 필요 없는 값은 담지 않습니다.
 * 목록은 최근 몇 건으로 잘라 한 번의 답변이 만드는 조회량을 예측 가능하게 둡니다.
 */
@Service
class AssistantWorkToolService(
    private val accounts: AccountRepository,
    private val preparations: ApplicationPreparationService,
    private val reviews: CombinationReviewService,
    private val reviewRuns: CombinationReviewRunService,
    private val dailyReports: DailyReportService,
    private val dailyReportSubscriptions: DailyReportSubscriptionService,
    private val proposals: PartnerProposalService,
    private val programs: SupportProgramRepository,
) {
    /** 진행 단계 변경 카드가 쓰는 `progressRevision`까지 함께 돌려줍니다. */
    fun applicationPreparations(accountId: Long): List<AssistantApplicationPreparationSummary> {
        val account = account(accountId)
        return preparations.listOwned(account, null, PREPARATION_MAX).items.map { item ->
            AssistantApplicationPreparationSummary(
                id = item.preparation.id,
                sourceCode = item.preparation.sourceCode,
                sourceProgramId = item.preparation.sourceProgramId,
                programTitle = item.form.programTitle,
                progressStage = item.preparation.progressStage.name,
                progressRevision = item.preparation.progressRevision,
                updatedAt = item.preparation.updatedAt.toLocalDate(),
            )
        }
    }

    /** 검토별 최근 실행 한 건의 상태만 확인합니다. 결과 판정은 결과 화면이 보여 줍니다. */
    fun combinationReviews(accountId: Long): List<AssistantCombinationReviewSummary> {
        val account = account(accountId)
        return reviews.listOwned(account, null, REVIEW_MAX).items.map { summary ->
            val review = reviews.findOwned(account, summary.id)
            val latest = reviewRuns.listOwned(account, summary.id, null, 1).firstOrNull()
            AssistantCombinationReviewSummary(
                id = summary.id,
                title = summary.title,
                inputRevision = summary.inputRevision,
                programTitles = review.draft.input.programs.map { programTitle(it.identity.sourceCode, it.identity.sourceProgramId) },
                latestRunStatus = latest?.status?.name,
                latestRunId = latest?.id,
                latestRunDate = latest?.startedAt?.toLocalDate(),
                updatedAt = summary.updatedAt.toLocalDate(),
            )
        }
    }

    fun dailyReportStatus(accountId: Long): AssistantDailyReportStatus {
        val account = account(accountId)
        val settings = dailyReportSubscriptions.settings(account)
        return AssistantDailyReportStatus(
            enabled = settings.enabled,
            emailConfirmed = settings.emailConfirmed,
            supportPurpose = settings.supportPurpose,
            serviceEnabled = settings.schedulerEnabled && settings.emailDeliveryAvailable,
            sendHour = settings.sendHour,
            latestReportDate = dailyReports.latest(account)?.reportDate,
        )
    }

    /** 대기 중인 제안 수와 가장 가까운 만료일입니다. 기업 미등록 회원은 제안함이 없으므로 수는 0입니다. */
    fun proposalSummary(accountId: Long): AssistantProposalSummary {
        val account = account(accountId)
        if (account.company == null) return AssistantProposalSummary(false, 0, 0, null)
        val received = proposals.findBox(account, PartnerProposalBox.RECEIVED).filter { it.status == PartnerProposalStatus.PENDING }
        val sent = proposals.findBox(account, PartnerProposalBox.SENT).filter { it.status == PartnerProposalStatus.PENDING }
        return AssistantProposalSummary(
            hasCompany = true,
            receivedPending = received.size,
            sentPending = sent.size,
            earliestExpiryDate = received.minOfOrNull { it.proposal.expiresAt }?.toLocalDate(),
        )
    }

    /** 공고가 내려갔거나 조회할 수 없으면 제목 대신 식별자를 씁니다. 검토 자체는 그대로 보여 줍니다. */
    private fun programTitle(sourceCode: String, sourceProgramId: String): String {
        val program = try {
            programs.findPresentBySourceAndProgramId(sourceCode, sourceProgramId)
        } catch (_: IllegalArgumentException) {
            null
        }
        return program?.program?.title ?: "$sourceCode:$sourceProgramId"
    }

    /** 토큰은 유효한데 계정이 사라진 경우입니다. 빈 자료로 답하지 않고 도구 호출을 실패로 끝냅니다. */
    private fun account(accountId: Long): Account =
        accounts.findById(accountId) ?: throw AssistantToolAccountMissingException(accountId)

    companion object {
        const val PREPARATION_MAX = 10
        const val REVIEW_MAX = 5
    }
}
