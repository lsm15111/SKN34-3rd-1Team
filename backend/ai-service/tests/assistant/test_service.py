from unittest.mock import AsyncMock

import pytest

from app.assistant.errors import AssistantAnswerError
from app.assistant.models import SCHEMA_VERSION, AssistantAnswerOutput, AssistantAnswerRequest
from app.assistant.service import AssistantService
from app.assistant.tools import ToolResult
from tests.assistant.conftest import RECRUITMENTS, model_output


def service_returning(output, results=()):
    agent = AsyncMock()
    agent.answer.return_value = (output, list(results))
    return AssistantService(agent), agent


@pytest.mark.anyio
async def test_help_answer_with_known_citation_passes_through(request_data, output_data):
    request = AssistantAnswerRequest.model_validate(request_data)
    service, agent = service_returning(AssistantAnswerOutput.model_validate(output_data))
    response = await service.answer(request)
    expected = {key: value for key, value in output_data.items() if key not in ("cards", "navigation")}
    assert response.model_dump(by_alias=True) == {"schemaVersion": SCHEMA_VERSION, **expected, "cards": [], "navigation": None, "toolCalls": []}
    agent.answer.assert_awaited_once_with(request)


@pytest.mark.anyio
@pytest.mark.parametrize("citations", [["unknown-entry"], ["search-score-meaning", "unknown-entry"]])
async def test_citation_outside_the_request_help_entries_is_an_error(request_data, output_data, citations):
    output_data["citations"] = citations
    service, _ = service_returning(AssistantAnswerOutput.model_validate(output_data))
    with pytest.raises(AssistantAnswerError):
        await service.answer(AssistantAnswerRequest.model_validate(request_data))


@pytest.mark.anyio
@pytest.mark.parametrize("data", [
    {"intent": "SEARCH", "searchQuery": "서울 제조업 R&D 지원"},
    {"intent": "PROGRAM_QUESTION"},
    {"intent": "OUT_OF_SCOPE", "answer": "세무 대행은 할 수 없어요. 지원사업 검색을 도와드릴 수 있어요."},
    {"intent": "UNCLEAR", "clarificationQuestion": "사용법인가요, 지원사업 검색인가요?"},
])
async def test_non_tool_intents_never_carry_cards_or_navigation(member_request_data, data):
    service, _ = service_returning(AssistantAnswerOutput.model_validate(model_output(**data)), [ToolResult("list_saved_programs", 3, [])])
    response = await service.answer(AssistantAnswerRequest.model_validate(member_request_data))
    assert response.intent == data["intent"]
    assert response.cards == [] and response.navigation is None
    assert [report.name for report in response.tool_calls] == ["list_saved_programs"]


@pytest.mark.anyio
async def test_tool_answer_keeps_verified_cards_with_titles_from_tool_results(member_request_data):
    output = AssistantAnswerOutput.model_validate(model_output(
        intent="PARTNER_MATCH", answer="맞는 모집글이에요.", navigation="PARTNERS",
        cards=[{"kind": "RECRUITMENT", "id": "22", "reason": "역할이 맞아요."}],
    ))
    service, _ = service_returning(output, [ToolResult("search_partner_recruitments", 12, RECRUITMENTS)])
    response = await service.answer(AssistantAnswerRequest.model_validate(member_request_data))
    assert response.cards[0].title == "스마트공장 참여기관 모집"
    assert response.navigation is not None and response.navigation.label == "파트너 모집 열기"


@pytest.mark.anyio
async def test_navigation_none_means_no_button(member_request_data):
    output = AssistantAnswerOutput.model_validate(model_output(intent="PARTNER_MATCH", answer="지금은 맞는 모집글이 없어요."))
    service, _ = service_returning(output, [ToolResult("search_partner_recruitments", 12, [])])
    response = await service.answer(AssistantAnswerRequest.model_validate(member_request_data))
    assert response.answer == "지금은 맞는 모집글이 없어요." and response.navigation is None


@pytest.mark.anyio
@pytest.mark.parametrize("bad_output", [None, {}, "PRODUCT_HELP"])
async def test_untyped_output_is_an_error(request_data, bad_output):
    service, _ = service_returning(bad_output)
    with pytest.raises(AssistantAnswerError):
        await service.answer(AssistantAnswerRequest.model_validate(request_data))


@pytest.mark.anyio
async def test_revalidates_model_instances_instead_of_trusting_constructed_output(request_data):
    service, _ = service_returning(AssistantAnswerOutput.model_construct(
        intent="PRODUCT_HELP", answer="근거 없는 답", citations=[], clarification_question=None, search_query=None,
        account_topic=None, cards=[], navigation="NONE",
    ))
    with pytest.raises(AssistantAnswerError):
        await service.answer(AssistantAnswerRequest.model_validate(request_data))
