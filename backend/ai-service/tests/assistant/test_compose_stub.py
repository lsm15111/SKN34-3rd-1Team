"""실제 Agents SDK(OpenAI Responses 모델)와 Compose 스텁 서버 사이의 도구 호출·최종 출력 형식을 검증합니다. 모델은 부르지 않습니다."""

import importlib.util
from io import BytesIO
from pathlib import Path

import httpx2
import pytest
from agents import OpenAIResponsesModel
from openai import AsyncOpenAI

from app.assistant.agent import AssistantAgent
from app.assistant.models import AssistantAnswerRequest
from app.assistant.service import AssistantService
from tests.assistant.conftest import FakeCoreTools


def load_stub():
    stub_path = Path(__file__).resolve().parents[4] / "infrastructure/stubs/openai/server.py"
    spec = importlib.util.spec_from_file_location("assistant_compose_stub", stub_path)
    stub = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(stub)
    return stub


@pytest.mark.anyio
@pytest.mark.parametrize(("member", "message", "intent", "card_kinds", "tools"), [
    (True, "나한테 맞는 파트너 모집글 있어?", "PARTNER_MATCH", ["RECRUITMENT", "RECRUITMENT"], ["get_my_company_profile", "search_partner_recruitments"]),
    (True, "관심 공고 마감 언제야?", "ACCOUNT_STATE", ["PROGRAM", "PROGRAM"], ["list_saved_programs"]),
    (True, "담아둔 공고 중 온라인 접수 되는 거 있어?", "SAVED_PROGRAMS_QUESTION", ["PROGRAM", "PROGRAM"], ["list_saved_programs"]),
    (True, "받은 제안 있어?", "ACCOUNT_STATE", [], ["get_proposals_summary"]),
    (True, "신청 준비 어디까지 했지?", "ACCOUNT_STATE", ["PREPARATION", "PREPARATION"], ["list_application_preparations"]),
    (True, "중복 검토 끝났어?", "ACCOUNT_STATE", ["REVIEW", "REVIEW"], ["list_combination_reviews"]),
    (True, "리포트 오고 있어?", "ACCOUNT_STATE", [], ["get_daily_report_status"]),
    (True, "부산 수출 지원 찾아줘", "SEARCH", ["PROGRAM", "PROGRAM"], ["find_programs"]),
    (True, "예비창업패키지 찾아서 담아줘", "SEARCH", ["PROGRAM", "PROGRAM"], ["find_programs"]),
    (False, "나한테 맞는 파트너 모집글 있어?", "PARTNER_MATCH", [], []),
    (False, "점수는 무슨 뜻이야?", "PRODUCT_HELP", [], []),
    (False, "부산 수출 지원 찾아줘", "SEARCH", [], []),
])
async def test_actual_compose_stub_through_agents_sdk_and_service(
    request_data, member_request_data, monkeypatch, member, message, intent, card_kinds, tools,
):
    stub = load_stub()
    data = dict(member_request_data if member else request_data, message=message)
    calls = []

    def handle(http_request):
        calls.append(http_request)
        handler = object.__new__(stub.Handler)
        handler.path = http_request.url.path
        handler.headers = {"Content-Length": str(len(http_request.content))}
        handler.rfile = BytesIO(http_request.content)
        responses = []
        monkeypatch.setattr(handler, "respond", lambda code, body: responses.append(httpx2.Response(code, json=body)))
        handler.do_POST()
        assert len(responses) == 1
        return responses[0]

    client = AsyncOpenAI(api_key="test-key", base_url="https://openai.test/v1/", max_retries=0,
                         http_client=httpx2.AsyncClient(transport=httpx2.MockTransport(handle)))
    core = FakeCoreTools()
    agent = AssistantAgent(
        model=OpenAIResponsesModel(model="gpt-5.6-luna", openai_client=client), tool_client=core.client(),
        model_timeout_seconds=4, run_timeout_seconds=5, max_tool_calls=3,
    )
    try:
        response = await AssistantService(agent).answer(AssistantAnswerRequest.model_validate(data))
    finally:
        await client.close()

    assert response.intent == intent
    assert [card.kind for card in response.cards] == card_kinds
    assert [report.name for report in response.tool_calls] == tools
    assert len(calls) == len(tools) + 1
    if not member and intent == "PARTNER_MATCH":
        assert response.answer is None and core.requests == []
    # 동작을 부탁한 말에만 확인 버튼이 붙고, 조회만 물으면 붙지 않습니다.
    assert [action.kind for action in response.actions] == (["SAVE_PROGRAM"] if "담아줘" in message else [])
