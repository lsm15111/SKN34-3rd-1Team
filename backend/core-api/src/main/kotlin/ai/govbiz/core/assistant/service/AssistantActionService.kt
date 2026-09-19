package ai.govbiz.core.assistant.service

import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.applicationpreparation.domain.ApplicationProgressStage
import ai.govbiz.core.applicationpreparation.service.ApplicationPreparationService
import ai.govbiz.core.assistant.domain.AssistantAction
import ai.govbiz.core.assistant.domain.AssistantActionChoice
import ai.govbiz.core.assistant.domain.AssistantActionKind
import ai.govbiz.core.combinationreview.service.CombinationReviewService
import ai.govbiz.core.supportprogram.repository.SupportProgramRepository
import ai.govbiz.core.supportprogram.service.saved.SavedSupportProgramService
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * AI Service가 제안한 실행을 Core 자료로 다시 확인해 확인 버튼으로 만듭니다.
 *
 * 가이드는 실행하지 않습니다. 여기서는 대상이 지금도 존재하고 이 계정의 것인지 확인한 뒤, 버튼 문구와 대상 값을
 * Core가 가진 제목·경로로 다시 만들어 줍니다. 실제 실행은 사용자가 버튼을 눌렀을 때 화면이 기존 API로 합니다.
 * 대상이 사라졌거나 이미 원하는 상태면(담은 공고를 또 담기 등) 그 제안만 빼고 답은 그대로 보냅니다.
 */
@Service
class AssistantActionService(
    private val savedSupportProgramService: SavedSupportProgramService,
    private val supportProgramRepository: SupportProgramRepository,
    private val preparations: ApplicationPreparationService,
    private val reviews: CombinationReviewService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun verify(account: Account?, choices: List<AssistantActionChoice>): List<AssistantAction> {
        if (account == null || choices.isEmpty()) return emptyList()
        return choices.take(MAX_ACTIONS).mapNotNull { choice ->
            try {
                build(account, choice)
            } catch (exception: RuntimeException) {
                // 대상이 사라졌거나 내 것이 아니면 버튼만 빼고 답은 그대로 보냅니다. 조용히 다른 대상으로 바꾸지는 않습니다.
                log.info("assistant_action_dropped kind={} reason={}", choice.kind, exception.javaClass.simpleName)
                null
            }
        }
    }

    private fun build(account: Account, choice: AssistantActionChoice): AssistantAction? = when (choice.kind) {
        AssistantActionKind.SAVE_PROGRAM -> programAction(account, choice, saved = false)
        AssistantActionKind.UNSAVE_PROGRAM -> programAction(account, choice, saved = true)
        AssistantActionKind.START_APPLICATION_PREPARATION -> startPreparation(choice)
        AssistantActionKind.SET_PREPARATION_STAGE -> stageAction(account, choice)
        AssistantActionKind.RUN_COMBINATION_REVIEW -> runReviewAction(account, choice)
    }

    /** 담기·빼기입니다. 지금 담긴 상태가 제안과 다르면(이미 담김·이미 뺌) 버튼을 만들지 않습니다. */
    private fun programAction(account: Account, choice: AssistantActionChoice, saved: Boolean): AssistantAction? {
        val identity = programIdentity(choice.targetId) ?: return null
        val program = supportProgramRepository.findPresentBySourceAndProgramId(identity.first, identity.second) ?: return null
        if (savedSupportProgramService.isSaved(account.id, identity.first, identity.second) != saved) return null
        val title = program.program.title
        return AssistantAction(
            kind = choice.kind,
            label = if (saved) AssistantActionTexts.UNSAVE_LABEL else AssistantActionTexts.SAVE_LABEL,
            confirm = if (saved) AssistantActionTexts.unsaveConfirm(title) else AssistantActionTexts.saveConfirm(title),
            sourceCode = identity.first,
            sourceProgramId = identity.second,
        )
    }

    /** 신청 문서 준비는 양식을 고르는 단계가 있어 가이드가 대신 만들지 않고, 그 공고가 선택된 작성 화면만 엽니다. */
    private fun startPreparation(choice: AssistantActionChoice): AssistantAction? {
        val identity = programIdentity(choice.targetId) ?: return null
        val program = supportProgramRepository.findPresentBySourceAndProgramId(identity.first, identity.second) ?: return null
        val query = "sourceCode=${encode(identity.first)}&sourceProgramId=${encode(identity.second)}"
        return AssistantAction(
            kind = choice.kind,
            label = AssistantActionTexts.START_PREPARATION_LABEL,
            confirm = AssistantActionTexts.startPreparationConfirm(program.program.title),
            sourceCode = identity.first,
            sourceProgramId = identity.second,
            to = "${AssistantMessageService.InternalRoutes.APPLICATION_PREPARATION_NEW}?$query",
        )
    }

    private fun stageAction(account: Account, choice: AssistantActionChoice): AssistantAction? {
        val stage = ApplicationProgressStage.entries.firstOrNull { it.name == choice.stage } ?: return null
        val preparation = preparations.findOwned(account, choice.targetId.toLongOrNull() ?: return null)
        if (preparation.preparation.progressStage == stage) return null
        val title = preparation.form.programTitle
        return AssistantAction(
            kind = choice.kind,
            label = AssistantActionTexts.stageLabel(stage),
            confirm = AssistantActionTexts.stageConfirm(title, stage),
            preparationId = preparation.preparation.id,
            stage = stage.name,
        )
    }

    private fun runReviewAction(account: Account, choice: AssistantActionChoice): AssistantAction? {
        val review = reviews.findOwned(account, choice.targetId.toLongOrNull() ?: return null)
        return AssistantAction(
            kind = choice.kind,
            label = AssistantActionTexts.RUN_REVIEW_LABEL,
            confirm = AssistantActionTexts.runReviewConfirm(review.draft.title),
            reviewId = review.id,
        )
    }

    /** 카드와 같은 `제공처:원본ID` 형식만 받습니다. 형식이 다르면 제안을 버립니다. */
    private fun programIdentity(targetId: String): Pair<String, String>? {
        val separator = targetId.indexOf(':')
        if (separator <= 0 || separator == targetId.lastIndex) return null
        val sourceCode = targetId.substring(0, separator)
        return if (SOURCE_CODE.matches(sourceCode)) sourceCode to targetId.substring(separator + 1) else null
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    companion object {
        const val MAX_ACTIONS = 2
        private val SOURCE_CODE = Regex("[A-Z][A-Z0-9_]{0,39}")
    }
}
