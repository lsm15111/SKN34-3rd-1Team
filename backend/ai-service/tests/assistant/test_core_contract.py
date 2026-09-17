"""Core `AiAssistantClientTest`가 보내고 받는 본문과 같은 파일을 AI Service 모델로 검증합니다. 한쪽만 바뀌면 실패합니다."""

import json
import os
from pathlib import Path

from app.assistant.models import AssistantAnswerRequest, AssistantAnswerResponse


FIXTURES = (
    Path(os.environ["CORE_CONTRACT_FIXTURES"]) / "assistant" if os.environ.get("CORE_CONTRACT_FIXTURES")
    else Path(__file__).resolve().parents[4] / "backend/core-api/src/test/resources/assistant"
)


def load(name: str) -> dict:
    return json.loads((FIXTURES / name).read_text(encoding="utf-8"))


def test_core_request_fixture_is_a_valid_request_and_round_trips():
    data = load("contract-request.json")
    request = AssistantAnswerRequest.model_validate(data)
    assert request.principal is not None and request.principal.account_id == 7
    assert request.model_dump(by_alias=True) == data


def test_core_response_fixture_is_exactly_what_the_service_can_return():
    data = load("contract-response.json")
    assert AssistantAnswerResponse.model_validate(data).model_dump(by_alias=True) == data
