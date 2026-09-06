package ai.govbiz.core.recruitment.domain

import ai.govbiz.core.supportprogram.domain.SupportProgram
import ai.govbiz.core.supportprogram.domain.SupportProgramStatus
import java.time.LocalDate

/**
 * 모집글 표시 상태를 읽을 때 계산합니다.
 *
 * 숨김 > 종료 > 모집 중 순서이며, 연결 공고가 더 이상 공개되지 않거나 접수가 끝났으면 모집도 종료로 봅니다.
 */
object RecruitmentPostStatusResolver {

    fun resolve(
        post: RecruitmentPost,
        linkedProgram: SupportProgram?,
        today: LocalDate,
    ): RecruitmentPostStatus =
        when {
            post.hiddenAt != null -> RecruitmentPostStatus.HIDDEN
            post.closedEarlyAt != null -> RecruitmentPostStatus.CLOSED
            today.isAfter(post.draft.closesOn) -> RecruitmentPostStatus.CLOSED
            linkedProgram == null -> RecruitmentPostStatus.CLOSED
            linkedProgram.status == SupportProgramStatus.CLOSED -> RecruitmentPostStatus.CLOSED
            else -> RecruitmentPostStatus.OPEN
        }
}
