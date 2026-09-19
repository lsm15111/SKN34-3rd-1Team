from pydantic import ValidationError

from app.assistant.agent import AssistantAgent
from app.assistant.errors import AssistantAnswerError
from app.assistant.models import (
    MAX_TOOL_CALL_REPORTS, NAVIGATIONS, SCHEMA_VERSION, TOOL_INTENTS, AssistantAnswerOutput, AssistantAnswerRequest,
    AssistantAnswerResponse, AssistantCard, AssistantNavigation, AssistantToolCallReport,
)
from app.assistant.tools import ToolResult, card_catalog


class AssistantService:
    """모델 출력을 요청·도구 결과와 대조해 응답 계약으로 만듭니다.

    - 인용은 요청의 도움말 항목만 가리켜야 합니다.
    - 카드는 이번 실행의 도구 결과에 있는 항목만 되고, 제목·경로는 도구 결과에서 다시 만듭니다.
    - 회원 자료 의도의 답은 도구 결과가 있을 때만 씁니다. 비로그인이거나 자료를 읽지 않은 답은 버리고 Core가 안내합니다.
    """

    def __init__(self, agent: AssistantAgent) -> None:
        self._agent = agent

    async def answer(self, request: AssistantAnswerRequest) -> AssistantAnswerResponse:
        output, results = await self._agent.answer(request)
        try:
            if not isinstance(output, AssistantAnswerOutput):
                raise AssistantAnswerError()
            output = AssistantAnswerOutput.model_validate(output.model_dump(by_alias=True))
            if not set(output.citations) <= request.help_entry_ids():
                raise AssistantAnswerError("citation outside the help entries")
            answer, cards, navigation = output.answer, [], None
            if output.intent in TOOL_INTENTS:
                grounded = (
                    request.principal is not None and bool(results)
                    # 기업 미등록 회원의 모집글 매칭은 Core가 기업 등록 안내로 답합니다.
                    and not (output.intent == "PARTNER_MATCH" and not request.principal.has_company)
                )
                if grounded:
                    cards = verified_cards(output, results)
                    navigation = _navigation(output.navigation)
                else:
                    answer = None
            response = AssistantAnswerResponse(
                schemaVersion=SCHEMA_VERSION, intent=output.intent, answer=answer, citations=output.citations,
                clarificationQuestion=output.clarification_question, searchQuery=output.search_query,
                accountTopic=output.account_topic, cards=cards, navigation=navigation if answer is not None else None,
                toolCalls=[AssistantToolCallReport(name=result.name, ms=result.ms) for result in results][:MAX_TOOL_CALL_REPORTS],
            )
            return AssistantAnswerResponse.model_validate(response.model_dump(by_alias=True))
        except (ValidationError, ValueError) as error:
            raise AssistantAnswerError() from error


def verified_cards(output: AssistantAnswerOutput, results: list[ToolResult]) -> list[AssistantCard]:
    """카드 id가 전부 도구 결과 안에 있어야 합니다. 하나라도 없으면 지어낸 카드이므로 답 전체를 오류로 끝냅니다."""
    catalog = card_catalog(results)
    cards: list[AssistantCard] = []
    for choice in output.cards:
        base = catalog.get((choice.kind, choice.id))
        if base is None:
            raise AssistantAnswerError("card outside the tool results")
        cards.append(AssistantCard(**base, reason=choice.reason))
    return cards


def _navigation(key: str) -> AssistantNavigation | None:
    entry = NAVIGATIONS.get(key)
    return AssistantNavigation(label=entry[0], to=entry[1]) if entry else None
