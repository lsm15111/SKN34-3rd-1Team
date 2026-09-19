import logging

from pydantic import ValidationError

from collections.abc import AsyncIterator

from app.assistant.agent import AssistantAgent, GuideFinalEvent, GuideStatusEvent, GuideTextEvent
from app.assistant.errors import AssistantAnswerError
from app.assistant.models import (
    MAX_TOOL_CALL_REPORTS, NAVIGATIONS, SCHEMA_VERSION, TOOL_INTENTS, AssistantActionChoice, AssistantAnswerOutput,
    AssistantAnswerRequest, AssistantAnswerResponse, AssistantCard, AssistantNavigation, AssistantToolCallReport,
)
from app.assistant.tools import ToolResult, action_catalog, card_catalog


logger = logging.getLogger(__name__)


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
        return self.build_response(request, output, results)

    async def answer_stream(self, request: AssistantAnswerRequest) -> AsyncIterator[object]:
        """진행 상황과 답변 조각을 먼저 내보내고, 마지막에 같은 검증을 거친 응답을 한 번 내보냅니다.

        카드·이동 버튼·실행 제안은 검증을 통과한 마지막 결과에만 실립니다. 조각으로 나간 문장은 아직 검증 전이므로
        Core와 화면이 마지막 결과의 문장으로 덮어씁니다.
        """
        async for event in self._agent.answer_stream(request):
            if isinstance(event, GuideFinalEvent):
                yield self.build_response(request, event.output, event.results)
            elif isinstance(event, (GuideStatusEvent, GuideTextEvent)):
                yield event

    def build_response(
        self, request: AssistantAnswerRequest, output: AssistantAnswerOutput, results: list[ToolResult],
    ) -> AssistantAnswerResponse:
        try:
            if not isinstance(output, AssistantAnswerOutput):
                raise AssistantAnswerError()
            output = AssistantAnswerOutput.model_validate(output.model_dump(by_alias=True))
            if not set(output.citations) <= request.help_entry_ids():
                raise AssistantAnswerError("citation outside the help entries")
            answer, cards, navigation, actions = output.answer, [], None, []
            if output.intent in TOOL_INTENTS:
                grounded = (
                    request.principal is not None and bool(results)
                    # 기업 미등록 회원의 모집글 매칭은 Core가 기업 등록 안내로 답합니다.
                    and not (output.intent == "PARTNER_MATCH" and not request.principal.has_company)
                )
                if grounded:
                    cards = verified_cards(output, results)
                    navigation = _navigation(output.navigation)
                    actions = verified_actions(output, results)
                else:
                    answer = None
            response = AssistantAnswerResponse(
                schemaVersion=SCHEMA_VERSION, intent=output.intent, answer=answer, citations=output.citations,
                clarificationQuestion=output.clarification_question, searchQuery=output.search_query,
                accountTopic=output.account_topic, cards=cards, navigation=navigation if answer is not None else None,
                actions=actions if answer is not None else [],
                toolCalls=[AssistantToolCallReport(name=result.name, ms=result.ms) for result in results][:MAX_TOOL_CALL_REPORTS],
            )
            return AssistantAnswerResponse.model_validate(response.model_dump(by_alias=True))
        except AssistantAnswerError as error:
            # 어떤 규칙에 걸렸는지만 남깁니다. 질문·답·도구 결과 본문은 남기지 않습니다.
            logger.info("assistant_answer_rejected intent=%s reason=%s", getattr(output, "intent", None), error)
            raise
        except (ValidationError, ValueError) as error:
            logger.info("assistant_answer_rejected intent=%s reason=%s", getattr(output, "intent", None), type(error).__name__)
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


def verified_actions(output: AssistantAnswerOutput, results: list[ToolResult]) -> list[AssistantActionChoice]:
    """실행 제안의 대상이 전부 이번 실행의 도구 결과 안에 있어야 합니다. 하나라도 없으면 답 전체를 오류로 끝냅니다."""
    catalog = action_catalog(results)
    for action in output.actions:
        target = catalog.get(action.kind, {}).get(action.target_id)
        if target is None:
            raise AssistantAnswerError("action outside the tool results")
        # 지금과 같은 단계로 바꾸자는 제안은 아무 일도 하지 않는 버튼이 됩니다.
        if action.kind == "SET_PREPARATION_STAGE" and action.stage == target.get("stage"):
            raise AssistantAnswerError("stage action does not change the stage")
    return list(output.actions)


def _navigation(key: str) -> AssistantNavigation | None:
    entry = NAVIGATIONS.get(key)
    return AssistantNavigation(label=entry[0], to=entry[1]) if entry else None
