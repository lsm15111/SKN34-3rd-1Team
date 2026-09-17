#!/usr/bin/env python3
"""GovBiz 가이드 자유 질문 회귀 평가. 기본 실행은 모델 호출 없는 입력 검증이고 `--live`만 OpenAI를 호출한다.

모든 문항을 서비스와 같은 한 경로(AI Service `app/assistant`: 에이전트 하나 + 회원 자료 읽기 도구)로 돌린다.
로그인 세션 문항은 가짜 Core 도구 서버(`agent_fixtures.py`)를 쓰고 모델만 실제로 부른다.
"""

import argparse
import asyncio
from datetime import datetime, timezone
from hashlib import sha256
import json
import os
from pathlib import Path
import sys
from time import perf_counter


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT / "backend/ai-service"))

from app.assistant.models import AssistantAnswerRequest, SCHEMA_VERSION  # noqa: E402

HELP_CATALOG = ROOT / "backend/core-api/src/main/resources/assistant/help-catalog.json"
QUESTIONS = HERE / "questions.json"
ABSTAIN_INTENTS = {"OUT_OF_SCOPE", "UNCLEAR"}
INTENTS = ["PRODUCT_HELP", "ACCOUNT_STATE", "SEARCH", "PROGRAM_QUESTION", "OUT_OF_SCOPE", "UNCLEAR"]
# 회원 자료 도구로 답하는 의도. `mode: "agent"`(로그인 세션·도구 기대) 문항만 이 의도를 기대한다.
AGENT_ONLY_INTENTS = ["PARTNER_MATCH", "SAVED_PROGRAMS_QUESTION"]
TOOL_NAMES = {"get_my_company_profile", "search_partner_recruitments", "list_saved_programs"}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def load_help_entries(path: Path = HELP_CATALOG) -> list[dict]:
    """Core 도움말 카탈로그를 AI Service 요청 형식으로 읽는다. Core가 보내는 것과 같은 모양이며 화면용 `routes`는 뺀다."""
    catalog = json.loads(path.read_text(encoding="utf-8"))
    return [{key: value for key, value in entry.items() if key != "routes"} for entry in catalog["entries"]]


def load_questions(path: Path = QUESTIONS) -> dict:
    fixture = json.loads(path.read_text(encoding="utf-8"))
    require(fixture.get("schemaVersion") == "assistant-intent-eval-v3", "unsupported questions schema")
    require(fixture.get("dataType") == "synthetic", "this fixture must be explicitly synthetic")
    require(fixture.get("referenceSource") == "ai-authored", "reference source must be disclosed")
    cases = fixture.get("cases")
    require(isinstance(cases, list) and len(cases) > 0, "cases must be a non-empty list")
    ids = [case["id"] for case in cases]
    require(len(set(ids)) == len(ids), "case ids must be unique")
    for case in cases:
        mode = case.get("mode", "classify")
        require(mode in ("classify", "agent"), f"{case['id']}: mode must be classify or agent")
        require(case.get("expectedIntent") in INTENTS + (AGENT_ONLY_INTENTS if mode == "agent" else []), f"{case['id']}: unknown expectedIntent")
        require(case.get("split") in ("dev", "heldout"), f"{case['id']}: split must be dev or heldout")
        if case["expectedIntent"] == "PRODUCT_HELP":
            require(isinstance(case.get("expectedCitation"), str), f"{case['id']}: PRODUCT_HELP needs expectedCitation")
        if case["expectedIntent"] == "ACCOUNT_STATE":
            require(case.get("expectedAccountTopic") in ("SAVED_PROGRAMS", "RECEIVED_PROPOSALS", "COMPANY_PROFILE"), f"{case['id']}: bad expectedAccountTopic")
        if mode == "agent":
            tools = case.get("expectedTools")
            require(isinstance(tools, list) and set(tools) <= TOOL_NAMES, f"{case['id']}: agent cases need expectedTools within {sorted(TOOL_NAMES)}")
            require(case.get("session", {}).get("authenticated") is True, f"{case['id']}: agent cases are asked with a logged-in session")
            cards = case.get("expectedCards") or []
            require(isinstance(cards, list) and all(isinstance(item, str) for item in cards), f"{case['id']}: expectedCards must be a list of ids")
    return fixture


def is_agent_case(case: dict) -> bool:
    return case.get("mode", "classify") == "agent"


def build_requests(fixture: dict, help_entries: list[dict]) -> list[tuple[dict, AssistantAnswerRequest]]:
    """모든 문항을 서비스 계약으로 만든다. 로그인 세션이면 가짜 도구 서버가 받는 고정 principal을 싣는다(Core와 같은 규칙)."""
    from agent_fixtures import ACCOUNT_ID, TOKEN

    help_ids = {entry["id"] for entry in help_entries}
    defaults = fixture.get("defaults", {})
    prepared = []
    for case in fixture["cases"]:
        citation = case.get("expectedCitation")
        require(citation is None or citation in help_ids, f"{case['id']}: expectedCitation {citation} is not a Core catalog entry")
        session = case.get("session", defaults.get("session"))
        principal = {"accountId": ACCOUNT_ID, "toolToken": TOKEN, "hasCompany": bool(session.get("hasCompany"))} if session.get("authenticated") else None
        request = AssistantAnswerRequest.model_validate({
            "schemaVersion": SCHEMA_VERSION,
            "message": case["message"],
            "history": case.get("history", []),
            "session": session,
            "context": case.get("context", defaults.get("context")),
            "helpEntries": help_entries,
            "principal": principal,
        })
        prepared.append((case, request))
    return prepared


def score(case: dict, output: dict | None) -> dict:
    """한 문항의 판정이다. `output`이 None이면 호출 실패다."""
    expected = case["expectedIntent"]
    result = {"id": case["id"], "split": case["split"], "expectedIntent": expected, "intent": None,
              "intentCorrect": False, "citationCorrect": None, "accountTopicCorrect": None, "abstained": None, "error": None}
    if output is None:
        result["error"] = "no output"
        return result
    intent = output.get("intent")
    result["intent"] = intent
    result["intentCorrect"] = intent == expected
    result["abstained"] = intent in ABSTAIN_INTENTS
    if expected == "PRODUCT_HELP":
        result["citationCorrect"] = case["expectedCitation"] in (output.get("citations") or [])
    if expected == "ACCOUNT_STATE":
        result["accountTopicCorrect"] = output.get("accountTopic") == case["expectedAccountTopic"]
    return result


def score_agent(case: dict, output: dict | None, valid_ids: set[str]) -> dict:
    """도구 문항의 판정. 호출한 도구 집합, 카드가 고정 자료 안에만 있는지, 기대 카드 포함, 답을 만들었는지 본다."""
    result = score(case, output)
    result["mode"] = "agent" if is_agent_case(case) else "classify"
    if output is None:
        return result
    tool_calls = [call["name"] for call in output.get("toolCalls") or []]
    cards = output.get("cards") or []
    result["tools"] = sorted(tool_calls)
    result["toolsCorrect"] = set(tool_calls) == set(case.get("expectedTools", [])) if is_agent_case(case) else None
    result["cardCount"] = len(cards)
    result["cardsValid"] = all(card.get("id") in valid_ids for card in cards)
    expected_cards = set(case.get("expectedCards") or [])
    result["expectedCardsIncluded"] = expected_cards <= {card.get("id") for card in cards} if expected_cards else None
    result["answered"] = bool(output.get("answer"))
    return result


def summarize_agent(results: list[dict]) -> dict:
    scored = [item for item in results if item.get("error") is None]
    agent_cases = [item for item in scored if item.get("toolsCorrect") is not None]
    with_expected_cards = [item for item in agent_cases if item.get("expectedCardsIncluded") is not None]
    return {
        "agentCases": len(agent_cases),
        "toolSelectionAccuracy": _rate([item["toolsCorrect"] for item in agent_cases]),
        "cardValidityRate": _rate([item["cardsValid"] for item in scored if "cardsValid" in item]),
        "expectedCardsIncludedRate": _rate([item["expectedCardsIncluded"] for item in with_expected_cards]),
        "answeredRate": _rate([item["answered"] for item in agent_cases]),
    }


def _rate(values: list) -> float | None:
    return round(sum(1 for value in values if value) / len(values), 4) if values else None


def summarize(results: list[dict]) -> dict:
    """의도 정확도, 사용법 인용 정확도, 기권율(답 불가 문항)과 오기권율(답 가능 문항)을 센다."""
    def ratio(hits: int, total: int) -> float | None:
        return None if total == 0 else round(hits / total, 4)

    scored = [item for item in results if item["error"] is None]
    unanswerable = [item for item in scored if item["expectedIntent"] in ABSTAIN_INTENTS]
    answerable = [item for item in scored if item["expectedIntent"] not in ABSTAIN_INTENTS]
    help_cases = [item for item in scored if item["expectedIntent"] == "PRODUCT_HELP"]
    account_cases = [item for item in scored if item["expectedIntent"] == "ACCOUNT_STATE"]
    per_intent = {}
    for intent in INTENTS + AGENT_ONLY_INTENTS:
        items = [item for item in scored if item["expectedIntent"] == intent]
        per_intent[intent] = {"total": len(items), "correct": sum(item["intentCorrect"] for item in items)}
    per_split = {}
    for split in ("dev", "heldout"):
        items = [item for item in scored if item["split"] == split]
        per_split[split] = {"total": len(items), "intentAccuracy": ratio(sum(item["intentCorrect"] for item in items), len(items))}
    return {
        "cases": len(results),
        "scored": len(scored),
        "errors": len(results) - len(scored),
        "intentAccuracy": ratio(sum(item["intentCorrect"] for item in scored), len(scored)),
        "helpCitationAccuracy": ratio(sum(bool(item["citationCorrect"]) for item in help_cases), len(help_cases)),
        "accountTopicAccuracy": ratio(sum(bool(item["accountTopicCorrect"]) for item in account_cases), len(account_cases)),
        "abstainRateOnUnanswerable": ratio(sum(bool(item["abstained"]) for item in unanswerable), len(unanswerable)),
        "falseAbstainRateOnAnswerable": ratio(sum(bool(item["abstained"]) for item in answerable), len(answerable)),
        "perIntent": per_intent,
        "perSplit": per_split,
    }


def confusion(results: list[dict]) -> dict:
    table: dict[str, dict[str, int]] = {}
    for item in results:
        if item["error"] is not None:
            continue
        table.setdefault(item["expectedIntent"], {})
        table[item["expectedIntent"]][item["intent"]] = table[item["expectedIntent"]].get(item["intent"], 0) + 1
    return table


async def run_live(prepared: list[tuple[dict, AssistantAnswerRequest]]) -> tuple[list[dict], dict]:
    """AI Service 모듈을 그대로 써서 순서대로 한 번씩 호출한다. 요청 본문·답변 문장은 보고서에 남기지 않는다."""
    from agents import OpenAIResponsesModel
    from openai import AsyncOpenAI

    from app.assistant.agent import AssistantAgent
    from app.assistant.errors import AssistantAnswerError
    from app.assistant.service import AssistantService
    from app.assistant.tools import CoreToolClient
    from app.config import Settings
    from agent_fixtures import RECRUITMENT_IDS, SAVED_PROGRAM_IDS, SECRET, FakeCoreTools

    require(bool(os.environ.get("OPENAI_API_KEY")), "OPENAI_API_KEY is required for --live")
    settings = Settings.from_environment()
    client = AsyncOpenAI(api_key=settings.openai_api_key, base_url=os.environ.get("OPENAI_BASE_URL") or None)
    fake = FakeCoreTools()
    tool_client = CoreToolClient(base_url="http://core-api.eval", secret=SECRET, timeout_seconds=3, transport=fake.transport())
    service = AssistantService(AssistantAgent(
        model=OpenAIResponsesModel(model=settings.openai_assistant_model, openai_client=client),
        tool_client=tool_client,
        model_timeout_seconds=settings.llm_model_timeout_seconds,
        run_timeout_seconds=settings.assistant_agent_timeout_seconds,
        max_tool_calls=settings.assistant_agent_max_tool_calls,
        reasoning_effort=settings.openai_assistant_reasoning_effort,
    ))
    valid_ids = RECRUITMENT_IDS | SAVED_PROGRAM_IDS
    results, latencies = [], []
    try:
        for case, request in prepared:
            started = perf_counter()
            output = None
            error_kind = None
            try:
                output = (await service.answer(request)).model_dump(by_alias=True)
            except AssistantAnswerError as error:
                # 예외 이름과 계약 위반 사유만 남긴다. 질문·모델 문장은 담기지 않는다.
                cause = error.__cause__
                error_kind = type(error).__name__ + (f": {error}" if str(error) else "") + (f" <- {type(cause).__name__}" if cause is not None else "")
            latencies.append(perf_counter() - started)
            result = score_agent(case, output, valid_ids)
            if error_kind is not None:
                result["error"] = error_kind
            result["latencyMs"] = round(latencies[-1] * 1000)
            results.append(result)
    finally:
        await tool_client.aclose()
        await client.close()
    return results, {
        "model": settings.openai_assistant_model, "reasoningEffort": settings.openai_assistant_reasoning_effort,
        "maxToolCalls": settings.assistant_agent_max_tool_calls,
        "meanLatencyMs": round(sum(latencies) / len(latencies) * 1000) if latencies else None,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--live", action="store_true", help="OpenAI를 실제로 호출한다. OPENAI_API_KEY가 필요하다.")
    parser.add_argument("--mode", choices=["classify", "agent"], help="한 종류 문항만 평가한다(agent는 로그인 세션·도구 문항).")
    parser.add_argument("--split", choices=["dev", "heldout"], help="한 분할만 평가한다.")
    parser.add_argument("--case", action="append", default=[], help="특정 문항 id만 평가한다(반복 가능).")
    parser.add_argument("--report", type=Path, help="결과 JSON을 이 경로에도 저장한다.")
    args = parser.parse_args()

    fixture = load_questions()
    help_entries = load_help_entries()
    prepared = build_requests(fixture, help_entries)
    if args.mode:
        prepared = [item for item in prepared if item[0].get("mode", "classify") == args.mode]
    if args.split:
        prepared = [item for item in prepared if item[0]["split"] == args.split]
    if args.case:
        prepared = [item for item in prepared if item[0]["id"] in set(args.case)]
    require(len(prepared) > 0, "no cases selected")

    report = {
        "schemaVersion": "assistant-intent-eval-report-v2",
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "dataType": fixture["dataType"],
        "referenceSource": fixture["referenceSource"],
        "questionsSha256": sha256(QUESTIONS.read_bytes()).hexdigest(),
        "helpCatalogSha256": sha256(HELP_CATALOG.read_bytes()).hexdigest(),
        "helpEntryCount": len(help_entries),
        "selectedCases": len(prepared),
        "mode": "live" if args.live else "dry-run",
    }
    if args.live:
        results, run_info = asyncio.run(run_live(prepared))
        report.update(run_info)
        report["summary"] = {**summarize(results), **summarize_agent(results)}
        report["confusion"] = confusion(results)
        report["results"] = results
    else:
        report["summary"] = None
        report["note"] = "dry-run: 질문·도움말·요청 계약만 검증했다. 의도 정확도는 --live로 측정한다."
    text = json.dumps(report, ensure_ascii=False, indent=2)
    # Windows 콘솔 기본 인코딩에서도 한글 문항 id·문구가 깨지지 않게 한다.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    print(text)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(text + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
