import json

import pytest
from agents.testing import ScriptedModel, assistant_message
from fastapi.testclient import TestClient

from app.config import Settings
from app.help_answer.agent import HelpAnswerAgent
from app.main import create_app


SETTINGS = Settings(
    openai_api_key="test-key-never-sent", openai_model="test-model",
    llm_model_timeout_seconds=1, llm_run_timeout_seconds=2,
)
PATH = "/internal/v1/help/answers"


def build_client(outputs: list[str]) -> tuple[TestClient, ScriptedModel]:
    model = ScriptedModel([[assistant_message(output)] for output in outputs])
    agent = HelpAnswerAgent(model=model, model_timeout_seconds=1, run_timeout_seconds=2)
    return TestClient(create_app(settings=SETTINGS, help_answer_agent=agent)), model


def test_http_to_service_to_agent_to_response(request_data, output_data):
    client, model = build_client([json.dumps(output_data, ensure_ascii=False)])
    with client:
        response = client.post(PATH, json=request_data)

    assert response.status_code == 200
    assert response.json() == {
        "answer": output_data["answer"],
        "answerStatus": "ANSWERED",
        "citationEntryIds": ["relevance-score-meaning"],
    }
    assert len(model.calls) == 1


def test_model_never_receives_entry_ids(request_data, output_data):
    client, model = build_client([json.dumps(output_data, ensure_ascii=False)])
    with client:
        client.post(PATH, json=request_data)

    sent = json.dumps(model.calls[0].input, ensure_ascii=False, default=str)
    assert "relevance-score-meaning" not in sent
    assert "점수는 무엇을 뜻하나요" in sent


@pytest.mark.parametrize("status", ["OUT_OF_SCOPE_PROGRAM", "OUT_OF_SCOPE_GENERAL", "NOT_IN_HELP"])
def test_abstention_does_not_pass_model_prose_through(request_data, status):
    # 모델이 기권하면서 문장을 써도 화면이 가진 문구를 쓰도록 비웁니다.
    output = json.dumps({"answer": "", "answerStatus": status, "citationIndexes": []}, ensure_ascii=False)
    client, _ = build_client([output])
    with client:
        response = client.post(PATH, json=request_data)

    assert response.status_code == 200
    assert response.json() == {"answer": "", "answerStatus": status, "citationEntryIds": []}


@pytest.mark.parametrize("mutation", [
    {"question": " "},
    {"entries": []},
    {"schemaVersion": "v0"},
])
def test_invalid_request_is_rejected_before_the_model(request_data, mutation):
    request_data.update(mutation)
    client, model = build_client([])
    with client:
        response = client.post(PATH, json=request_data)

    assert response.status_code == 422
    assert not model.calls


@pytest.mark.parametrize("output", [
    "private non-json output",
    json.dumps({"answer": "지어낸 답", "answerStatus": "ANSWERED", "citationIndexes": []}),
    json.dumps({"answer": "지어낸 답", "answerStatus": "ANSWERED", "citationIndexes": [7]}),
    json.dumps({"answer": "지어낸 답", "answerStatus": "NOT_IN_HELP", "citationIndexes": [0]}),
])
def test_contract_violation_returns_503_without_leaking_output(request_data, output, caplog):
    client, _ = build_client([output])
    with client:
        response = client.post(PATH, json=request_data)

    assert response.status_code == 503
    assert response.json() == {"detail": "Help answer is temporarily unavailable."}
    assert "지어낸 답" not in caplog.text
    assert "private non-json output" not in caplog.text
