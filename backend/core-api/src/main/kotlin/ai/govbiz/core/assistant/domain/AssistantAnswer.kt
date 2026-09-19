package ai.govbiz.core.assistant.domain

/**
 * AI Service가 고른 의도입니다. Core는 의도별로 무엇을 조회하고 어디로 안내할지 정합니다.
 * `ACCOUNT_STATE`·`PARTNER_MATCH`·`SAVED_PROGRAMS_QUESTION`은 로그인 회원이면 AI Service가 회원 자료 도구로 답합니다.
 */
enum class AssistantIntent {
    PRODUCT_HELP,
    ACCOUNT_STATE,
    SEARCH,
    PROGRAM_QUESTION,
    OUT_OF_SCOPE,
    UNCLEAR,
    PARTNER_MATCH,
    SAVED_PROGRAMS_QUESTION,
    ;

    /** 회원 자료·공개 공고를 도구로 읽어 답하는 의도입니다. 비로그인이면 로그인·검색 화면 안내로 끝납니다. */
    val usesTools: Boolean
        get() = this == ACCOUNT_STATE || this == PARTNER_MATCH || this == SAVED_PROGRAMS_QUESTION || this == SEARCH
}

/** 계정 상태 질문의 영역입니다. Core가 회원 자료를 읽어 답을 만드는 기준입니다. */
enum class AssistantAccountTopic {
    SAVED_PROGRAMS,
    RECEIVED_PROPOSALS,
    COMPANY_PROFILE,
    APPLICATION_PREPARATIONS,
    COMBINATION_REVIEWS,
    DAILY_REPORT,
}

/** 답변 뒤에 붙는 이동 버튼입니다. `to`는 Core가 허용한 내부 경로만 담습니다. */
data class AssistantNavigation(
    val label: String,
    val to: String,
)

enum class AssistantCardKind {
    RECRUITMENT,
    PROGRAM,
    PREPARATION,
    REVIEW,
}

/**
 * 도구 에이전트가 고른 항목 하나입니다. 모집글은 `id`가 모집글 번호, 공고는 `sourceCode:sourceProgramId`입니다.
 * 제목·부제·경로는 AI Service가 도구 결과에서 채우고 Core가 형식과 경로를 다시 검사합니다.
 */
data class AssistantCard(
    val kind: AssistantCardKind,
    val id: String,
    val title: String,
    val subtitle: String?,
    val reason: String,
    val to: String,
)

/** 가이드가 제안할 수 있는 실행입니다. 사용자가 카드에서 확인해야 기존 화면과 같은 API로 실행됩니다. */
enum class AssistantActionKind {
    SAVE_PROGRAM,
    UNSAVE_PROGRAM,
    START_APPLICATION_PREPARATION,
    SET_PREPARATION_STAGE,
    RUN_COMBINATION_REVIEW,
}

/** AI Service가 제안한 실행입니다. 종류와 대상만 있고 문구·경로·권한은 Core가 채웁니다. */
data class AssistantActionChoice(
    val kind: AssistantActionKind,
    val targetId: String,
    val stage: String?,
)

/**
 * 확인 버튼 하나입니다. 문구·대상 값·경로는 모델 문자열이 아니라 Core가 자기 자료에서 다시 만든 값이며,
 * 종류에 따라 쓰는 필드가 다릅니다(공고 담기·빼기는 공고 식별자, 단계 변경은 준비 건, 검토 실행은 검토 id).
 */
data class AssistantAction(
    val kind: AssistantActionKind,
    val label: String,
    val confirm: String,
    val sourceCode: String? = null,
    val sourceProgramId: String? = null,
    val preparationId: Long? = null,
    val stage: String? = null,
    val reviewId: Long? = null,
    /** 실행하지 않고 화면만 여는 제안(신청 문서 준비 시작)의 경로입니다. */
    val to: String? = null,
)

/** 프런트 말풍선 하나에 해당하는 답입니다. 의도에 따라 채워지는 필드가 다릅니다. */
data class AssistantAnswer(
    val intent: AssistantIntent,
    /** 본문입니다. UNCLEAR만 null입니다. */
    val answer: String?,
    /** 답의 근거가 된 도움말 항목 id입니다. 요청에 실린 항목만 남깁니다. */
    val citations: List<String>,
    val clarificationQuestion: String?,
    val searchQuery: String?,
    val accountTopic: AssistantAccountTopic?,
    val navigation: AssistantNavigation?,
    /** 도구 에이전트가 고른 항목입니다. 도구 의도의 답에만 붙고 최대 5장입니다. */
    val cards: List<AssistantCard> = emptyList(),
    /** 사용자가 확인해야 실행되는 제안입니다. 도구 의도의 답에만 붙고 최대 2개입니다. */
    val actions: List<AssistantAction> = emptyList(),
)

/** 가이드 답변의 근거가 되는 도움말 한 항목입니다. Core 카탈로그(`assistant/help-catalog.json`)가 원본입니다. */
data class AssistantHelpEntry(
    val id: String,
    val title: String,
    val question: String,
    val summary: String,
    val body: List<String>,
    val limitation: String?,
    val audience: String,
    val status: String,
    val action: AssistantNavigation?,
)

data class AssistantHistoryMessage(
    val role: AssistantHistoryRole,
    val content: String,
)

enum class AssistantHistoryRole {
    USER,
    ASSISTANT,
}

/** 사용자가 지금 보고 있는 화면입니다. `programSelected`는 공고 상세처럼 원문 질문을 열 수 있는 화면인지입니다. */
data class AssistantScreenContext(
    val route: String,
    val programSelected: Boolean,
)

/** 가이드 질문 한 건입니다. 이전 대화는 브라우저가 아니라 [conversationId]로 서버 저장소에서 읽습니다. */
data class AssistantQuestion(
    val message: String,
    val conversationId: String,
    val context: AssistantScreenContext,
)

/**
 * 가이드 답을 만드는 동안 화면으로 나가는 사건 하나입니다.
 *
 * [Status]와 [Text]는 아직 검증 전이라 화면에 "만드는 중"으로만 보여 주고, 카드·이동 버튼·실행 제안이 실린
 * [Final]이 와야 답이 확정됩니다. [Text]로 나간 문장과 [Final]의 문장이 다를 수 있어(Core가 템플릿 답으로 바꾸는 경우)
 * 화면은 마지막에 [Final]의 문장으로 덮어씁니다.
 */
sealed interface AssistantStreamEvent {
    data class Status(val phase: AssistantStreamPhase) : AssistantStreamEvent

    data class Text(val delta: String) : AssistantStreamEvent

    data class Final(val answer: AssistantAnswer) : AssistantStreamEvent
}

/** 화면에 보여 줄 진행 단계입니다. 도구 이름 같은 내부 값은 내보내지 않습니다. */
enum class AssistantStreamPhase {
    THINKING,
    READING,
}
