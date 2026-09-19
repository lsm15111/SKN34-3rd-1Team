"""가이드 에이전트의 실제 Agents SDK 실행 루프를 대본 모델로 검증합니다. 도구는 Core 흉내(httpx MockTransport)를 부릅니다."""

import json

import pytest
from agents.testing import ScriptedModel, assistant_message, function_call

from app.assistant.agent import AssistantAgent
from app.assistant.errors import AssistantAnswerError
from app.assistant.models import AssistantAnswerRequest
from app.assistant.service import AssistantService
from tests.assistant.conftest import FakeCoreTools, model_output


def agent_for(model: ScriptedModel, tools: FakeCoreTools, *, max_tool_calls: int = 3, secret: str | None = None) -> AssistantAgent:
    return AssistantAgent(
        model=model, tool_client=tools.client(secret), model_timeout_seconds=1, run_timeout_seconds=5, max_tool_calls=max_tool_calls,
    )


def final(**overrides) -> list:
    return [assistant_message(json.dumps(model_output(**overrides), ensure_ascii=False))]


def tool_names(call) -> list[str]:
    return [tool.name for tool in call.tools]


@pytest.mark.anyio
async def test_member_partner_match_reads_profile_then_recruitments_and_rebuilds_cards_from_tool_results(member_request_data, core_tools):
    member_request_data["message"] = "나한테 맞는 모집글 있어?"
    model = ScriptedModel([
        [function_call("get_my_company_profile", {}, call_id="call_1")],
        [function_call("search_partner_recruitments", {"region": None, "seeking_role": "PARTICIPANT", "keyword": None}, call_id="call_2")],
        final(intent="PARTNER_MATCH", answer="역할이 맞는 모집글 한 건을 골랐어요.", navigation="PARTNERS",
              cards=[{"kind": "RECRUITMENT", "id": "21", "reason": "참여기관을 찾고 라벨링 역량이 맞아요."}]),
    ])
    response = await AssistantService(agent_for(model, core_tools)).answer(AssistantAnswerRequest.model_validate(member_request_data))

    assert response.intent == "PARTNER_MATCH"
    assert [card.model_dump(by_alias=True) for card in response.cards] == [{
        "kind": "RECRUITMENT", "id": "21", "title": "AI 실증 참여기관 구합니다", "subtitle": "서울AI 주식회사 · 서울 · 2026-09-20",
        "reason": "참여기관을 찾고 라벨링 역량이 맞아요.", "to": "/app/partners/detail?recruitmentId=21",
    }]
    assert response.navigation is not None and response.navigation.to == "/app/partners"
    assert [report.name for report in response.tool_calls] == ["get_my_company_profile", "search_partner_recruitments"]
    assert [request.url.path for request in core_tools.requests] == [
        "/internal/v1/assistant/tools/company-profile", "/internal/v1/assistant/tools/recruitments",
    ]
    assert dict(core_tools.requests[1].url.params) == {"accountId": "7", "seekingRole": "PARTICIPANT"}
    # 회원이면 도구 세 개가 보이고, 모델 입력에는 계정 번호·토큰이 없습니다.
    assert tool_names(model.first_call) == [
        "get_my_company_profile", "search_partner_recruitments", "list_saved_programs", "find_programs",
        "list_application_preparations", "list_combination_reviews", "get_daily_report_status", "get_proposals_summary",
    ]
    first_input = json.loads(model.first_call.input[0]["content"])
    assert "principal" not in first_input
    # 매 질문 같은 카탈로그를 질문 앞에 두어 프롬프트 캐시 접두사를 넓힙니다.
    assert list(first_input)[:3] == ["schemaVersion", "helpEntries", "message"]
    assert "7.1900000000.sig" not in json.dumps([call.input for call in model.calls], ensure_ascii=False, default=str)
    # 도구 결과의 전화번호는 모델에 가기 전에 가려집니다.
    profile_output = next(item for item in model.calls[1].input if isinstance(item, dict) and item.get("type") == "function_call_output")
    assert "010-1234-5678" not in profile_output["output"] and "[전화번호]" in profile_output["output"]
    model.assert_complete()


@pytest.mark.anyio
async def test_guest_sees_no_tools_and_tool_intent_answers_are_dropped_for_core_login_guidance(request_data, core_tools):
    request_data["message"] = "관심 공고 마감 언제야?"
    model = ScriptedModel([final(intent="ACCOUNT_STATE", accountTopic="SAVED_PROGRAMS", answer="관심 공고 3건이 이번 주 마감이에요.")])
    response = await AssistantService(agent_for(model, core_tools)).answer(AssistantAnswerRequest.model_validate(request_data))

    assert tool_names(model.first_call) == []
    assert response.intent == "ACCOUNT_STATE" and response.account_topic == "SAVED_PROGRAMS"
    # 자료를 읽지 않고 만든 숫자는 버립니다.
    assert response.answer is None and response.cards == [] and response.navigation is None
    assert core_tools.requests == []


@pytest.mark.anyio
async def test_member_answer_without_reading_any_tool_is_not_trusted(member_request_data, core_tools):
    member_request_data["message"] = "담아둔 공고 중 마감 임박한 거?"
    model = ScriptedModel([final(intent="SAVED_PROGRAMS_QUESTION", answer="두 건이 마감 임박이에요.", navigation="SAVED_PROGRAMS")])
    response = await AssistantService(agent_for(model, core_tools)).answer(AssistantAnswerRequest.model_validate(member_request_data))
    assert response.answer is None and response.navigation is None


@pytest.mark.anyio
async def test_partner_match_for_members_without_a_company_is_left_to_core(member_request_data, core_tools):
    member_request_data["session"] = {"authenticated": True, "hasCompany": False}
    member_request_data["principal"] = {**member_request_data["principal"], "hasCompany": False}
    model = ScriptedModel([
        [function_call("get_my_company_profile", {}, call_id="call_1")],
        final(intent="PARTNER_MATCH", answer="모집글 두 건이에요.", navigation="PARTNERS"),
    ])
    response = await AssistantService(agent_for(model, core_tools)).answer(AssistantAnswerRequest.model_validate(member_request_data))
    assert response.intent == "PARTNER_MATCH" and response.answer is None and response.navigation is None


@pytest.mark.anyio
async def test_work_status_answers_come_from_the_matching_job_tool(member_request_data, core_tools):
    member_request_data["message"] = "신청 준비 어디까지 했지?"
    model = ScriptedModel([
        [function_call("list_application_preparations", {}, call_id="call_1")],
        final(intent="ACCOUNT_STATE", accountTopic="APPLICATION_PREPARATIONS", answer="준비 중 1건, 신청 완료 1건입니다.",
              navigation="APPLICATION_PREPARATIONS",
              cards=[{"kind": "PREPARATION", "id": "31", "reason": "아직 준비 중인 건입니다."}]),
    ])
    response = await AssistantService(agent_for(model, core_tools)).answer(AssistantAnswerRequest.model_validate(member_request_data))

    assert response.account_topic == "APPLICATION_PREPARATIONS"
    assert [(card.kind, card.id, card.to) for card in response.cards] == [("PREPARATION", "31", "/app/application-preparations/31")]
    assert response.navigation is not None and response.navigation.to == "/app/application-preparations"
    assert [request.url.path for request in core_tools.requests] == ["/internal/v1/assistant/tools/application-preparations"]


@pytest.mark.anyio
async def test_actions_must_point_at_items_the_tools_actually_returned(member_request_data, core_tools):
    member_request_data["message"] = "예비창업패키지 담아줘"
    for actions in (
        # 도구 결과에 없는 공고
        [{"kind": "SAVE_PROGRAM", "targetId": "BIZINFO:UNKNOWN", "stage": None}],
        # 이미 담은 공고를 다시 담자는 제안
        [{"kind": "SAVE_PROGRAM", "targetId": "BIZINFO:PBLN_000000000000001", "stage": None}],
        # 대상 종류가 맞지 않는 실행
        [{"kind": "RUN_COMBINATION_REVIEW", "targetId": "KSTARTUP:174520", "stage": None}],
    ):
        model = ScriptedModel([
            [function_call("find_programs", {"keyword": "예비창업패키지", "region": None}, call_id="call_1")],
            final(intent="SEARCH", searchQuery="예비창업패키지", answer="한 건을 찾았어요.", navigation="CHAT", actions=actions),
        ])
        with pytest.raises(AssistantAnswerError):
            await AssistantService(agent_for(model, core_tools)).answer(AssistantAnswerRequest.model_validate(member_request_data))


@pytest.mark.anyio
async def test_stage_action_must_change_the_stage_and_survives_as_a_choice(member_request_data, core_tools):
    member_request_data["message"] = "서울 AI 실증 신청 완료로 바꿔줘"
    same_stage = ScriptedModel([
        [function_call("list_application_preparations", {}, call_id="call_1")],
        final(intent="ACCOUNT_STATE", accountTopic="APPLICATION_PREPARATIONS", answer="준비 중입니다.", navigation="APPLICATION_PREPARATIONS",
              actions=[{"kind": "SET_PREPARATION_STAGE", "targetId": "31", "stage": "PREPARING"}]),
    ])
    with pytest.raises(AssistantAnswerError):
        await AssistantService(agent_for(same_stage, core_tools)).answer(AssistantAnswerRequest.model_validate(member_request_data))

    changed = ScriptedModel([
        [function_call("list_application_preparations", {}, call_id="call_1")],
        final(intent="ACCOUNT_STATE", accountTopic="APPLICATION_PREPARATIONS", answer="신청 완료로 바꿀까요?", navigation="APPLICATION_PREPARATIONS",
              actions=[{"kind": "SET_PREPARATION_STAGE", "targetId": "31", "stage": "APPLIED"}]),
    ])
    response = await AssistantService(agent_for(changed, core_tools)).answer(AssistantAnswerRequest.model_validate(member_request_data))
    assert [(action.kind, action.target_id, action.stage) for action in response.actions] == [("SET_PREPARATION_STAGE", "31", "APPLIED")]


@pytest.mark.anyio
async def test_search_answers_with_found_programs_and_keeps_the_search_query(member_request_data, core_tools):
    member_request_data["message"] = "서울 창업 지원금 찾아줘"
    model = ScriptedModel([
        [function_call("find_programs", {"keyword": "창업 지원금", "region": "서울"}, call_id="call_1")],
        final(intent="SEARCH", searchQuery="서울 창업 지원금", answer="모집 중인 창업 지원사업 두 건을 찾았습니다.", navigation="CHAT",
              cards=[{"kind": "PROGRAM", "id": "KSTARTUP:174520", "reason": "10월 10일까지 모집 중입니다."}]),
    ])
    response = await AssistantService(agent_for(model, core_tools)).answer(AssistantAnswerRequest.model_validate(member_request_data))

    assert response.intent == "SEARCH" and response.search_query == "서울 창업 지원금"
    assert [(card.kind, card.id) for card in response.cards] == [("PROGRAM", "KSTARTUP:174520")]
    assert dict(core_tools.requests[0].url.params) == {"accountId": "7", "keyword": "창업 지원금", "region": "서울"}


@pytest.mark.anyio
async def test_guest_search_keeps_the_query_and_drops_the_agent_answer(request_data, core_tools):
    request_data["message"] = "창업 지원금 찾아줘"
    model = ScriptedModel([final(intent="SEARCH", searchQuery="창업 지원금", answer="두 건을 찾았습니다.", navigation="CHAT")])
    response = await AssistantService(agent_for(model, core_tools)).answer(AssistantAnswerRequest.model_validate(request_data))

    # 비로그인은 도구가 없으므로 근거 없는 답과 이동 버튼을 버리고 Core가 검색 화면을 안내합니다.
    assert response.search_query == "창업 지원금" and response.answer is None and response.navigation is None
    assert core_tools.requests == []


@pytest.mark.anyio
async def test_saved_programs_question_uses_program_cards_from_the_saved_list(member_request_data, core_tools):
    model = ScriptedModel([
        [function_call("list_saved_programs", {}, call_id="call_1")],
        final(intent="SAVED_PROGRAMS_QUESTION", answer="목록만으로는 접수 방법을 확인할 수 없어요. 공고 상세의 원문 질문에서 확인해 주세요.",
              navigation="SAVED_PROGRAMS", cards=[{"kind": "PROGRAM", "id": "MSIT:3186880", "reason": "원문 질문에서 접수 방법을 확인하세요."}]),
    ])
    response = await AssistantService(agent_for(model, core_tools)).answer(AssistantAnswerRequest.model_validate(member_request_data))
    assert [card.to for card in response.cards] == ["/app/support-programs/detail?sourceCode=MSIT&sourceProgramId=3186880"]
    assert response.cards[0].subtitle == "과학기술정보통신부"


@pytest.mark.anyio
@pytest.mark.parametrize("card", [
    {"kind": "RECRUITMENT", "id": "999", "reason": "지어낸 모집글"},
    {"kind": "PROGRAM", "id": "BIZINFO:PBLN_000000000000001", "reason": "모집글만 읽었는데 공고 카드"},
])
async def test_cards_outside_the_tool_results_fail_the_whole_answer(member_request_data, core_tools, card):
    model = ScriptedModel([
        [function_call("search_partner_recruitments", {}, call_id="call_1")],
        final(intent="PARTNER_MATCH", answer="맞는 모집글이에요.", navigation="PARTNERS", cards=[card]),
    ])
    with pytest.raises(AssistantAnswerError):
        await AssistantService(agent_for(model, core_tools)).answer(AssistantAnswerRequest.model_validate(member_request_data))


@pytest.mark.anyio
@pytest.mark.parametrize("status", [401, 503])
async def test_tool_failure_ends_the_run_instead_of_letting_the_model_improvise(member_request_data, core_tools, status):
    core_tools.fail_with = status
    model = ScriptedModel([
        [function_call("list_saved_programs", {}, call_id="call_1")],
        final(intent="ACCOUNT_STATE", accountTopic="SAVED_PROGRAMS", answer="확인하지 못했어요."),
    ])
    with pytest.raises(AssistantAnswerError):
        await agent_for(model, core_tools).answer(AssistantAnswerRequest.model_validate(member_request_data))
    assert len(model.calls) == 1


@pytest.mark.anyio
async def test_tool_loop_is_bounded_by_max_tool_calls(member_request_data, core_tools):
    model = ScriptedModel([[function_call("list_saved_programs", {}, call_id=f"call_{index}")] for index in range(3)])
    with pytest.raises(AssistantAnswerError):
        await agent_for(model, core_tools, max_tool_calls=2).answer(AssistantAnswerRequest.model_validate(member_request_data))
    assert len(model.calls) == 3
    assert len(core_tools.requests) == 2


@pytest.mark.anyio
async def test_tools_stay_hidden_when_the_core_tool_secret_is_not_configured(member_request_data, core_tools):
    model = ScriptedModel([final(intent="ACCOUNT_STATE", accountTopic="COMPANY_PROFILE")])
    response = await AssistantService(agent_for(model, core_tools, secret="")).answer(AssistantAnswerRequest.model_validate(member_request_data))
    assert tool_names(model.first_call) == []
    assert response.answer is None


@pytest.mark.anyio
async def test_a_tool_the_model_cannot_see_is_an_error_not_a_silent_call(request_data, core_tools):
    model = ScriptedModel([[function_call("list_saved_programs", {}, call_id="call_1")]])
    with pytest.raises(AssistantAnswerError):
        await agent_for(model, core_tools).answer(AssistantAnswerRequest.model_validate(request_data))
    assert core_tools.requests == []


def test_rejects_unbounded_tool_call_settings(core_tools):
    for value in (0, 7):
        with pytest.raises(ValueError):
            agent_for(ScriptedModel([]), core_tools, max_tool_calls=value)
