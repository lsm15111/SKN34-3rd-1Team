"""GovBiz 가이드 계약(`govbiz-assistant-v2`). Core가 보내는 요청, 모델의 구조화 출력, Core가 다시 검증하는 응답."""

import re
from typing import Annotated, Any, Literal, Self

from pydantic import AfterValidator, BaseModel, ConfigDict, Field, model_validator

from app.support_program_conversation.models import validate_text


SCHEMA_VERSION = "govbiz-assistant-v2"

MAX_HISTORY_MESSAGES = 6
MAX_HELP_ENTRIES = 40
MAX_CITATIONS = 3
MAX_CARDS = 5
MAX_TOOL_CALL_REPORTS = 12
MAX_TOOL_TOKEN_LENGTH = 400

MessageText = Annotated[str, Field(min_length=1, max_length=500), AfterValidator(
    lambda value: validate_text(value, 500, allow_layout=True)
)]
HistoryText = Annotated[str, Field(min_length=1, max_length=1000), AfterValidator(
    lambda value: validate_text(value, 1000, allow_layout=True)
)]
ShortText = Annotated[str, Field(min_length=1, max_length=160), AfterValidator(
    lambda value: validate_text(value, 160)
)]
ParagraphText = Annotated[str, Field(min_length=1, max_length=600), AfterValidator(
    lambda value: validate_text(value, 600, allow_layout=True)
)]
AnswerText = Annotated[str, Field(min_length=1, max_length=600), AfterValidator(
    lambda value: validate_text(value, 600, allow_layout=True)
)]
ReasonText = Annotated[str, Field(min_length=1, max_length=200), AfterValidator(lambda value: validate_text(value, 200))]
HelpEntryId = Annotated[str, Field(min_length=1, max_length=64, pattern=r"^[a-z0-9]+(-[a-z0-9]+)*$")]
CardId = Annotated[str, Field(min_length=1, max_length=80, pattern=r"^[A-Za-z0-9_:.-]+$")]


def validate_route(value: str) -> str:
    if re.fullmatch(r"/[A-Za-z0-9._~!$&'()*+,;=:@%/-]*", value) is None:
        raise ValueError("route must be an absolute path without query or fragment")
    return value


def validate_card_route(value: str) -> str:
    if re.fullmatch(r"/app/[A-Za-z0-9/_-]+(\?[A-Za-z0-9_=&%.:+-]*)?", value) is None:
        raise ValueError("route must be an internal /app path with an optional query")
    return value


RouteText = Annotated[str, Field(min_length=1, max_length=200), AfterValidator(validate_route)]
CardRouteText = Annotated[str, Field(min_length=1, max_length=300), AfterValidator(validate_card_route)]

AssistantIntent = Literal[
    "PRODUCT_HELP", "ACCOUNT_STATE", "SEARCH", "PROGRAM_QUESTION", "OUT_OF_SCOPE", "UNCLEAR",
    "PARTNER_MATCH", "SAVED_PROGRAMS_QUESTION",
]
AccountTopic = Literal[
    "SAVED_PROGRAMS", "RECEIVED_PROPOSALS", "COMPANY_PROFILE",
    "APPLICATION_PREPARATIONS", "COMBINATION_REVIEWS", "DAILY_REPORT",
]
CardKind = Literal["RECRUITMENT", "PROGRAM", "PREPARATION", "REVIEW"]
NavigationKey = Literal[
    "NONE", "PARTNERS", "SAVED_PROGRAMS", "PROPOSALS", "PROFILE", "CHAT",
    "APPLICATION_PREPARATIONS", "COMBINATION_REVIEWS", "REPORTS",
]

# 회원 자료·공개 공고를 도구로 읽어 답하는 의도입니다. 카드와 이동 버튼은 이 의도의 답에만 붙습니다.
TOOL_INTENTS: frozenset[str] = frozenset({
    "ACCOUNT_STATE", "PARTNER_MATCH", "SAVED_PROGRAMS_QUESTION", "SEARCH",
})

# 이동 버튼이 가리킬 수 있는 화면입니다. Core도 같은 목록으로 다시 검사합니다.
NAVIGATIONS: dict[str, tuple[str, str]] = {
    "PARTNERS": ("파트너 모집 열기", "/app/partners"),
    "SAVED_PROGRAMS": ("관심 공고함 열기", "/app/saved-programs"),
    "PROPOSALS": ("제안함 열기", "/app/proposals"),
    "PROFILE": ("프로필 열기", "/app/profile"),
    "CHAT": ("검색 화면 열기", "/app/chat"),
    "APPLICATION_PREPARATIONS": ("신청 준비 열기", "/app/application-preparations"),
    "COMBINATION_REVIEWS": ("중복 검토 열기", "/app/combination-reviews"),
    "REPORTS": ("리포트 열기", "/app/reports"),
}
RECRUITMENT_DETAIL_ROUTE = "/app/partners/detail"
PROGRAM_DETAIL_ROUTE = "/app/support-programs/detail"
PREPARATION_DETAIL_ROUTE = "/app/application-preparations"
REVIEW_DETAIL_ROUTE = "/app/combination-reviews"

# 의도별 (필수, 선택) 필드입니다. 도구 의도는 비로그인이면 답 없이 의도만 돌려주고 Core가 로그인 안내를 붙입니다.
INTENT_FIELDS: dict[str, tuple[frozenset[str], frozenset[str]]] = {
    "PRODUCT_HELP": (frozenset({"answer", "citations"}), frozenset()),
    "ACCOUNT_STATE": (frozenset({"accountTopic"}), frozenset({"answer"})),
    "SEARCH": (frozenset({"searchQuery"}), frozenset({"answer"})),
    "PROGRAM_QUESTION": (frozenset(), frozenset()),
    "OUT_OF_SCOPE": (frozenset({"answer"}), frozenset()),
    "UNCLEAR": (frozenset({"clarificationQuestion"}), frozenset()),
    "PARTNER_MATCH": (frozenset(), frozenset({"answer"})),
    "SAVED_PROGRAMS_QUESTION": (frozenset(), frozenset({"answer"})),
}
_FIELD_ALIASES = {
    "answer": ("answer",),
    "citations": ("citations",),
    "clarificationQuestion": ("clarificationQuestion", "clarification_question"),
    "searchQuery": ("searchQuery", "search_query"),
    "accountTopic": ("accountTopic", "account_topic"),
}


class AssistantHelpAction(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, populate_by_name=True)

    label: ShortText
    to: RouteText


class AssistantHelpEntry(BaseModel):
    """Core 도움말 카탈로그의 한 항목입니다. 모델은 이 항목들만 근거로 사용법을 답합니다."""

    model_config = ConfigDict(extra="forbid", frozen=True, populate_by_name=True)

    id: HelpEntryId
    title: ShortText
    question: ShortText
    summary: ParagraphText
    body: list[ParagraphText] = Field(max_length=10)
    limitation: ParagraphText | None
    audience: Literal["public", "member", "company", "admin"]
    status: Literal["available", "demo", "planned"]
    action: AssistantHelpAction | None


class AssistantHistoryMessage(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, populate_by_name=True)

    role: Literal["USER", "ASSISTANT"]
    content: HistoryText


class AssistantSession(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, populate_by_name=True)

    authenticated: bool = Field(strict=True)
    has_company: bool = Field(alias="hasCompany", strict=True)

    @model_validator(mode="after")
    def validate_company_requires_login(self) -> Self:
        if self.has_company and not self.authenticated:
            raise ValueError("hasCompany requires an authenticated session")
        return self


class AssistantContext(BaseModel):
    """사용자가 지금 보고 있는 화면입니다. programSelected는 공고 상세처럼 원문 질문이 가능한 화면인지입니다."""

    model_config = ConfigDict(extra="forbid", frozen=True, populate_by_name=True)

    route: RouteText
    program_selected: bool = Field(alias="programSelected", strict=True)


class AssistantPrincipal(BaseModel):
    """도구가 Core를 되부를 때 쓰는 계정 번호와 단기 토큰입니다. 비로그인이면 principal 자체가 null입니다."""

    model_config = ConfigDict(extra="forbid", frozen=True, populate_by_name=True)

    account_id: int = Field(alias="accountId", ge=1, strict=True)
    tool_token: str = Field(alias="toolToken", min_length=1, max_length=MAX_TOOL_TOKEN_LENGTH, pattern=r"^[A-Za-z0-9._-]+$")
    has_company: bool = Field(alias="hasCompany", strict=True)


class AssistantAnswerRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, populate_by_name=True)

    schema_version: Literal[SCHEMA_VERSION] = Field(alias="schemaVersion")
    message: MessageText
    history: list[AssistantHistoryMessage] = Field(max_length=MAX_HISTORY_MESSAGES)
    session: AssistantSession
    context: AssistantContext
    help_entries: list[AssistantHelpEntry] = Field(alias="helpEntries", min_length=1, max_length=MAX_HELP_ENTRIES)
    principal: AssistantPrincipal | None

    @model_validator(mode="after")
    def validate_consistency(self) -> Self:
        if len({entry.id for entry in self.help_entries}) != len(self.help_entries):
            raise ValueError("help entry ids must be unique")
        if self.principal is not None:
            if not self.session.authenticated:
                raise ValueError("principal requires an authenticated session")
            if self.principal.has_company != self.session.has_company:
                raise ValueError("principal.hasCompany must match session.hasCompany")
        return self

    def help_entry_ids(self) -> frozenset[str]:
        return frozenset(entry.id for entry in self.help_entries)


class AssistantCardChoice(BaseModel):
    """모델이 고르는 카드입니다. 제목·경로는 모델이 아니라 도구 결과에서 채웁니다."""

    model_config = ConfigDict(extra="forbid", frozen=True, populate_by_name=True)

    kind: CardKind
    id: CardId
    reason: ReasonText


class AssistantAnswerOutput(BaseModel):
    """모델의 구조화 출력입니다. 의도 하나와 그 의도의 필드, 도구 의도라면 카드·이동 버튼을 고릅니다."""

    model_config = ConfigDict(extra="forbid", frozen=True, populate_by_name=True)

    intent: AssistantIntent
    answer: AnswerText | None
    citations: list[HelpEntryId] = Field(max_length=MAX_CITATIONS)
    clarification_question: ShortText | None = Field(alias="clarificationQuestion")
    search_query: MessageText | None = Field(alias="searchQuery")
    account_topic: AccountTopic | None = Field(alias="accountTopic")
    cards: list[AssistantCardChoice] = Field(max_length=MAX_CARDS)
    navigation: NavigationKey

    @model_validator(mode="before")
    @classmethod
    def clear_fields_outside_the_intent(cls, data: Any) -> Any:
        # 모델이 의도와 무관한 필드(검색 의도의 answer 같은)를 함께 채우는 일이 잦습니다. 쓰지 않는 필드는 비우고,
        # 필수 필드가 빠진 경우는 아래 검증이 그대로 오류로 냅니다.
        if not isinstance(data, dict) or data.get("intent") not in INTENT_FIELDS:
            return data
        required, optional = INTENT_FIELDS[data["intent"]]
        cleaned = dict(data)
        for field, keys in _FIELD_ALIASES.items():
            if field in required | optional:
                continue
            for key in keys:
                if key in cleaned:
                    cleaned[key] = [] if field == "citations" else None
        # 카드·이동 버튼은 회원 자료 의도의 답에만 붙습니다. 답이 없으면 붙일 곳이 없으므로 비웁니다.
        if data["intent"] not in TOOL_INTENTS or cleaned.get("answer") is None:
            cleaned["cards"] = []
            cleaned["navigation"] = "NONE"
        return cleaned

    @model_validator(mode="after")
    def validate_fields_for_intent(self) -> Self:
        if len(set(self.citations)) != len(self.citations):
            raise ValueError("citations must be unique")
        required, optional = INTENT_FIELDS[self.intent]
        present = present_fields(self)
        if not (required <= present <= required | optional):
            raise ValueError(f"{self.intent} requires {sorted(required)} and allows {sorted(optional)}")
        if len({(card.kind, card.id) for card in self.cards}) != len(self.cards):
            raise ValueError("cards must be unique")
        if (self.cards or self.navigation != "NONE") and self.answer is None:
            raise ValueError("cards and navigation need an answer")
        return self


class AssistantCard(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, populate_by_name=True)

    kind: CardKind
    id: CardId
    title: ShortText
    subtitle: ShortText | None
    reason: ReasonText
    to: CardRouteText


class AssistantNavigation(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, populate_by_name=True)

    label: ShortText
    to: CardRouteText


class AssistantToolCallReport(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, populate_by_name=True)

    name: str = Field(min_length=1, max_length=64, pattern=r"^[a-z_]+$")
    ms: int = Field(ge=0, strict=True)


class AssistantAnswerResponse(BaseModel):
    """Core로 돌아가는 응답입니다. 카드 제목·경로는 도구 결과에서 다시 만든 값입니다."""

    model_config = ConfigDict(extra="forbid", frozen=True, populate_by_name=True)

    schema_version: Literal[SCHEMA_VERSION] = Field(alias="schemaVersion")
    intent: AssistantIntent
    answer: AnswerText | None
    citations: list[HelpEntryId] = Field(max_length=MAX_CITATIONS)
    clarification_question: ShortText | None = Field(alias="clarificationQuestion")
    search_query: MessageText | None = Field(alias="searchQuery")
    account_topic: AccountTopic | None = Field(alias="accountTopic")
    cards: list[AssistantCard] = Field(max_length=MAX_CARDS)
    navigation: AssistantNavigation | None
    tool_calls: list[AssistantToolCallReport] = Field(alias="toolCalls", max_length=MAX_TOOL_CALL_REPORTS)

    @model_validator(mode="after")
    def validate_fields_for_intent(self) -> Self:
        if len(set(self.citations)) != len(self.citations):
            raise ValueError("citations must be unique")
        required, optional = INTENT_FIELDS[self.intent]
        present = present_fields(self)
        if not (required <= present <= required | optional):
            raise ValueError(f"{self.intent} requires {sorted(required)} and allows {sorted(optional)}")
        if (self.cards or self.navigation is not None) and (self.intent not in TOOL_INTENTS or self.answer is None):
            raise ValueError("cards and navigation belong to tool answers only")
        if len({(card.kind, card.id) for card in self.cards}) != len(self.cards):
            raise ValueError("cards must be unique")
        return self


def present_fields(output: AssistantAnswerOutput | AssistantAnswerResponse) -> frozenset[str]:
    return frozenset(
        name for name, value in (
            ("answer", output.answer), ("citations", output.citations or None),
            ("clarificationQuestion", output.clarification_question),
            ("searchQuery", output.search_query), ("accountTopic", output.account_topic),
        ) if value is not None
    )
