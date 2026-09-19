import asyncio
import json
import logging
import re

import pytest
from agents.testing import ModelStep, ScriptedModel, assistant_message, function_call
from fastapi.testclient import TestClient

from app.assistant.agent import AssistantAgent
from app.assistant.models import SCHEMA_VERSION
from app.assistant.tools import CoreToolClient
from app.config import Settings
from app.main import create_app


SETTINGS = Settings(openai_api_key="test-key-never-sent", openai_model="test-model",
                    llm_model_timeout_seconds=1, llm_run_timeout_seconds=2)
PATH = "/internal/v1/assistant/answers"
STREAM_PATH = "/internal/v1/assistant/answers/stream"


def guide_agent(model, *, model_timeout_seconds=1, run_timeout_seconds=2):
    return AssistantAgent(
        model=model, tool_client=CoreToolClient(base_url="http://core-api:8080", secret=None, timeout_seconds=1),
        model_timeout_seconds=model_timeout_seconds, run_timeout_seconds=run_timeout_seconds, max_tool_calls=3,
    )


def test_http_to_service_to_agent_to_response(request_data, output_data):
    model = ScriptedModel([[assistant_message(json.dumps(output_data, ensure_ascii=False))]])
    agent = guide_agent(model)
    with TestClient(create_app(settings=SETTINGS, assistant_agent=agent)) as client:
        response = client.post(PATH, json=request_data)
    assert response.status_code == 200
    expected = {key: value for key, value in output_data.items() if key not in ("cards", "navigation")}
    assert response.json() == {"schemaVersion": SCHEMA_VERSION, **expected, "cards": [], "navigation": None, "toolCalls": []}
    assert len(model.calls) == 1
    # The model receives the request without the principal: help entries, session and screen context included.
    assert json.loads(model.first_call.input[0]["content"]) == {key: value for key, value in request_data.items() if key != "principal"}


@pytest.mark.parametrize("mutation", [{"message": " "}, {"schemaVersion": "govbiz-assistant-v1"}, {"helpEntries": []}, {"turns": []}])
def test_invalid_internal_request_keeps_existing_fastapi_422_policy(request_data, mutation):
    request_data.update(mutation)
    model = ScriptedModel([])
    agent = guide_agent(model)
    with TestClient(create_app(settings=SETTINGS, assistant_agent=agent)) as client:
        response = client.post(PATH, json=request_data)
    assert response.status_code == 422
    assert not model.calls


@pytest.mark.parametrize("kind", ["invalid_json", "unknown_citation", "help_without_citation"])
def test_invalid_upstream_output_returns_safe_503(request_data, output_data, kind, caplog):
    if kind == "unknown_citation":
        output_data["citations"] = ["private-fabricated-entry"]
    if kind == "help_without_citation":
        output_data["citations"] = []
    output = "private non-json output" if kind == "invalid_json" else json.dumps(output_data, ensure_ascii=False)
    model = ScriptedModel([[assistant_message(output)]])
    agent = guide_agent(model)
    with TestClient(create_app(settings=SETTINGS, assistant_agent=agent)) as client:
        response = client.post(PATH, json=request_data)
    assert response.status_code == 503
    assert response.json() == {"detail": "Assistant answer is temporarily unavailable."}
    assert "private" not in response.text
    assert len(model.calls) == 1
    records = [record for record in caplog.records if record.name.endswith("assistant.router")]
    assert len(records) == 1
    record = records[0]
    assert record.levelno == logging.WARNING
    assert record.exc_info is None
    assert re.fullmatch(r"assistant_answer_failed failure_kind=execution error_type=\w+ elapsed_ms=\d+", record.getMessage())
    assert request_data["message"] not in record.getMessage()


@pytest.mark.parametrize("deadline", ["model", "run"])
def test_deadline_returns_safe_504_without_retry(request_data, deadline, caplog):
    async def hang_forever(_):
        await asyncio.Event().wait()
        return []
    model = ScriptedModel([ModelStep.respond(hang_forever)])
    agent = guide_agent(model,
        model_timeout_seconds=0.01 if deadline == "model" else 1,
        run_timeout_seconds=1 if deadline == "model" else 0.01)
    with TestClient(create_app(settings=SETTINGS, assistant_agent=agent)) as client:
        response = client.post(PATH, json=request_data)
    assert response.status_code == 504
    assert response.json() == {"detail": "Assistant answer timed out."}
    assert len(model.calls) <= 1
    records = [record for record in caplog.records if record.name.endswith("assistant.router")]
    assert len(records) == 1
    cause = "ModelTimeoutError" if deadline == "model" else "TimeoutError"
    assert re.fullmatch(rf"assistant_answer_failed failure_kind=timeout error_type={cause} elapsed_ms=\d+", records[0].getMessage())
    assert records[0].exc_info is None


def test_agent_log_never_contains_message_or_answer_text(request_data, output_data, caplog):
    model = ScriptedModel([[assistant_message(json.dumps(output_data, ensure_ascii=False))]])
    agent = guide_agent(model)
    with caplog.at_level(logging.INFO, logger="app.assistant.agent"):
        with TestClient(create_app(settings=SETTINGS, assistant_agent=agent)) as client:
            assert client.post(PATH, json=request_data).status_code == 200
    records = [record for record in caplog.records if record.name.endswith("assistant.agent")]
    assert len(records) == 1
    message = records[0].getMessage()
    assert message.startswith("assistant_answer_run outcome=completed ")
    assert request_data["message"] not in message
    assert output_data["answer"] not in message


def read_events(response) -> list[tuple[str, dict]]:
    """SSE 본문을 (이름, 데이터) 목록으로 읽습니다."""
    events = []
    for block in response.text.split("\n\n"):
        lines = [line for line in block.splitlines() if line]
        if not lines:
            continue
        name = next(line[len("event: "):] for line in lines if line.startswith("event: "))
        data = next(line[len("data: "):] for line in lines if line.startswith("data: "))
        events.append((name, json.loads(data)))
    return events


def test_streaming_sends_status_then_text_then_the_verified_final(member_request_data, core_tools):
    member_request_data["message"] = "관심 공고 마감 언제야?"
    output = {
        "intent": "ACCOUNT_STATE", "answer": "관심 공고 2건 중 가장 빠른 마감은 9월 30일입니다.", "citations": [],
        "clarificationQuestion": None, "searchQuery": None, "accountTopic": "SAVED_PROGRAMS",
        "cards": [{"kind": "PROGRAM", "id": "BIZINFO:PBLN_000000000000001", "reason": "가장 빨리 마감됩니다."}],
        "navigation": "SAVED_PROGRAMS", "actions": [],
    }
    model = ScriptedModel([
        [function_call("list_saved_programs", {}, call_id="call_1")],
        [assistant_message(json.dumps(output, ensure_ascii=False))],
    ])
    agent = AssistantAgent(
        model=model, tool_client=core_tools.client(), model_timeout_seconds=1, run_timeout_seconds=5, max_tool_calls=3,
    )
    with TestClient(create_app(settings=SETTINGS, assistant_agent=agent)) as client:
        response = client.post(STREAM_PATH, json=member_request_data)

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")
    events = read_events(response)
    assert [name for name, _ in events] == ["status", "status", "text", "final"]
    assert events[0][1] == {"phase": "thinking", "tool": None}
    assert events[1][1] == {"phase": "reading", "tool": "list_saved_programs"}
    # 답변 문장만 조각으로 나가고, JSON의 다른 항목은 새 나가지 않습니다.
    assert events[2][1]["delta"] == output["answer"]
    final = events[3][1]
    assert final["answer"] == output["answer"]
    assert [card["id"] for card in final["cards"]] == ["BIZINFO:PBLN_000000000000001"]
    assert final["navigation"]["to"] == "/app/saved-programs"


def test_streaming_reports_a_contract_violation_as_an_error_event(member_request_data, core_tools):
    # 도구 결과에 없는 카드입니다. 조각은 나갔더라도 마지막에는 실패를 그대로 알립니다.
    output = {
        "intent": "ACCOUNT_STATE", "answer": "관심 공고를 확인했습니다.", "citations": [], "clarificationQuestion": None,
        "searchQuery": None, "accountTopic": "SAVED_PROGRAMS",
        "cards": [{"kind": "PROGRAM", "id": "BIZINFO:MADE_UP", "reason": "지어낸 카드"}], "navigation": "NONE", "actions": [],
    }
    model = ScriptedModel([
        [function_call("list_saved_programs", {}, call_id="call_1")],
        [assistant_message(json.dumps(output, ensure_ascii=False))],
    ])
    agent = AssistantAgent(
        model=model, tool_client=core_tools.client(), model_timeout_seconds=1, run_timeout_seconds=5, max_tool_calls=3,
    )
    with TestClient(create_app(settings=SETTINGS, assistant_agent=agent)) as client:
        response = client.post(STREAM_PATH, json=member_request_data)

    events = read_events(response)
    assert [name for name, _ in events][-1] == "error"
    assert events[-1][1] == {"kind": "execution"}
    assert not any(name == "final" for name, _ in events)
