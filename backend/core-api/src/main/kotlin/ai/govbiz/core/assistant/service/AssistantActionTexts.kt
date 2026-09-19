package ai.govbiz.core.assistant.service

import ai.govbiz.core.applicationpreparation.domain.ApplicationProgressStage

/**
 * 확인 버튼의 문구입니다. 모델이 쓴 문장이 아니라 Core가 대상 제목만 넣어 만드는 고정 문구라,
 * 버튼이 실제로 무엇을 하는지와 화면에 보이는 말이 어긋나지 않습니다.
 */
object AssistantActionTexts {
    const val SAVE_LABEL = "관심 공고함에 담기"
    const val UNSAVE_LABEL = "관심 공고함에서 빼기"
    const val START_PREPARATION_LABEL = "신청 문서 준비 시작"
    const val RUN_REVIEW_LABEL = "중복 검토 실행"

    fun saveConfirm(title: String): String = "'${clip(title)}'을(를) 관심 공고함에 담을까요?"

    fun unsaveConfirm(title: String): String = "'${clip(title)}'을(를) 관심 공고함에서 뺄까요?"

    fun startPreparationConfirm(title: String): String =
        "'${clip(title)}'의 신청 문서 준비를 시작할까요? 다음 화면에서 양식을 고르면 작성이 시작됩니다."

    fun stageLabel(stage: ApplicationProgressStage): String = "진행 단계를 ${stageName(stage)}(으)로 바꾸기"

    fun stageConfirm(title: String, stage: ApplicationProgressStage): String =
        "'${clip(title)}'의 진행 단계를 ${stageName(stage)}(으)로 바꿀까요?"

    fun runReviewConfirm(title: String): String =
        "'${clip(title)}' 중복 검토를 지금 실행할까요? 결과가 나오기까지 몇 분 걸릴 수 있습니다."

    /** 관심 공고함 진행 관리 화면(`applicationPipelineStages`)과 같은 이름을 씁니다. */
    fun stageName(stage: ApplicationProgressStage): String = when (stage) {
        ApplicationProgressStage.PREPARING -> "준비 중"
        ApplicationProgressStage.APPLIED -> "지원 완료"
        ApplicationProgressStage.DOCUMENT_REVIEW -> "서류 심사"
        ApplicationProgressStage.PRESENTATION_REVIEW -> "발표 심사"
        ApplicationProgressStage.SELECTED -> "선정"
        ApplicationProgressStage.REJECTED -> "탈락"
    }

    /** 긴 공고 제목이 확인 문구를 넘치게 하지 않도록 자릅니다. */
    private fun clip(title: String): String = if (title.length <= TITLE_MAX) title else title.take(TITLE_MAX - 1) + "…"

    private const val TITLE_MAX = 60
}
