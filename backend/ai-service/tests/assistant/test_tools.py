import httpx
import pytest

from app.assistant.errors import ToolCallError
from app.assistant.models import AssistantPrincipal
from app.assistant.tools import CoreToolClient, ToolResult, card_catalog, sanitize
from tests.assistant.conftest import RECRUITMENTS, SAVED_PROGRAMS, FakeCoreTools


PRINCIPAL = AssistantPrincipal(accountId=7, toolToken="7.1900000000.sig", hasCompany=True)


@pytest.mark.anyio
async def test_get_sends_shared_secret_account_token_and_non_empty_params(core_tools):
    client = core_tools.client()
    try:
        data = await client.get("/recruitments", PRINCIPAL, {"region": "서울", "seekingRole": "", "keyword": ""})
    finally:
        await client.aclose()
    assert [item["id"] for item in data] == [21]
    request = core_tools.requests[0]
    assert request.url.path == "/internal/v1/assistant/tools/recruitments"
    assert dict(request.url.params) == {"accountId": "7", "region": "서울"}
    assert request.headers["X-Internal-Token"] == core_tools.secret
    assert request.headers["X-Assistant-Tool-Token"] == PRINCIPAL.tool_token


@pytest.mark.anyio
@pytest.mark.parametrize("status", [401, 404, 500, 503])
async def test_non_200_is_a_tool_call_error(core_tools, status):
    core_tools.fail_with = status
    client = core_tools.client()
    try:
        with pytest.raises(ToolCallError):
            await client.get("/company-profile", PRINCIPAL)
    finally:
        await client.aclose()


@pytest.mark.anyio
async def test_transport_errors_invalid_json_and_missing_secret_are_tool_call_errors():
    def boom(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("refused", request=request)

    def not_json(_request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, content=b"<html>")

    for handler in (boom, not_json):
        client = CoreToolClient(base_url="http://core-api:8080", secret="s" * 40, timeout_seconds=1, transport=httpx.MockTransport(handler))
        try:
            with pytest.raises(ToolCallError):
                await client.get("/company-profile", PRINCIPAL)
        finally:
            await client.aclose()
    disabled = FakeCoreTools().client(secret="")
    try:
        assert not disabled.enabled
        with pytest.raises(ToolCallError):
            await disabled.get("/company-profile", PRINCIPAL)
    finally:
        await disabled.aclose()


@pytest.mark.parametrize(("raw", "expected"), [
    ("문의 010-1234-5678", "문의 [전화번호]"),
    ("메일 ceo@example.com 로", "메일 [이메일] 로"),
    ("사업자 123-45-67890", "사업자 [사업자등록번호]"),
    ("주민 900101-1234567", "주민 [주민등록번호]"),
    ("링크 https://evil.example/a?b=c 참고", "링크 [링크] 참고"),
    ("제어\x07문자\n줄바꿈", "제어문자\n줄바꿈"),
])
def test_sanitize_masks_personal_data_links_and_control_characters(raw, expected):
    assert sanitize(raw) == expected


def test_sanitize_bounds_size_depth_and_keeps_identifiers():
    assert len(sanitize("가" * 700)) == 601
    assert len(sanitize(list(range(50)))) == 30
    assert sanitize({"a": {"b": {"c": {"d": {"e": {"f": 1}}}}}}) == {"a": {"b": {"c": {"d": {"e": None}}}}}
    # 숫자로 된 공고 id와 날짜는 전화번호·사업자번호로 오인해 지우지 않습니다.
    assert sanitize({"sourceProgramId": "1234567890", "applicationEndDate": "2026-09-30"}) == {
        "sourceProgramId": "1234567890", "applicationEndDate": "2026-09-30",
    }
    assert sanitize(object()) is None


def test_card_catalog_only_uses_items_from_the_matching_tool():
    catalog = card_catalog([
        ToolResult("search_partner_recruitments", 1, RECRUITMENTS + [{"id": True, "title": "불리언 id"}, {"id": "23"}, "문자열"]),
        ToolResult("list_saved_programs", 1, SAVED_PROGRAMS),
        ToolResult("get_my_company_profile", 1, {"id": 99, "title": "프로필은 카드가 아님"}),
    ])
    assert set(catalog) == {
        ("RECRUITMENT", "21"), ("RECRUITMENT", "22"),
        ("PROGRAM", "BIZINFO:PBLN_000000000000001"), ("PROGRAM", "MSIT:3186880"),
    }
    assert catalog[("PROGRAM", "BIZINFO:PBLN_000000000000001")]["subtitle"] == "서울경제진흥원 · 2026-09-30"
    assert catalog[("RECRUITMENT", "21")]["to"] == "/app/partners/detail?recruitmentId=21"
