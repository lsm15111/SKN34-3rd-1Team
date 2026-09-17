"""GovBiz 가이드가 회원 자료를 읽는 도구입니다. 전부 Core 내부 GET·읽기 전용이고, 결과는 지시가 아니라 자료로만 씁니다."""

import json
import re
from dataclasses import dataclass, field
from time import perf_counter
from typing import Any, Literal
from urllib.parse import urlencode

import httpx
from agents import Agent, RunContextWrapper, function_tool

from app.assistant.errors import ToolCallError
from app.assistant.models import PROGRAM_DETAIL_ROUTE, RECRUITMENT_DETAIL_ROUTE, AssistantPrincipal


SECRET_HEADER = "X-Internal-Token"
TOKEN_HEADER = "X-Assistant-Tool-Token"
TOOLS_PATH_PREFIX = "/internal/v1/assistant/tools"

MAX_LIST_ITEMS = 30
MAX_TEXT_LENGTH = 600
MAX_NESTING = 4
# 식별자·날짜·열거값은 가리지 않습니다. 숫자 식별자를 사업자등록번호로 오인해 지우면 카드가 깨집니다.
IDENTIFIER_KEYS = frozenset({
    "sourceCode", "sourceProgramId", "documentId", "status", "ownRole", "seekingRole",
    "recruitmentDeadline", "programApplicationEndDate", "applicationEndDate",
})

_EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
_RESIDENT_NUMBER = re.compile(r"(?<![0-9])[0-9]{6}-?[1-4][0-9]{6}(?![0-9])")
_BUSINESS_NUMBER = re.compile(r"(?<![0-9])[0-9]{3}-?[0-9]{2}-?[0-9]{5}(?![0-9])")
_PHONE_NUMBER = re.compile(r"(?<![0-9])0[0-9]{1,2}[-. ]?[0-9]{3,4}[-. ]?[0-9]{4}(?![0-9])")
_URL = re.compile(r"https?://\S+")


class CoreToolClient:
    """공유 비밀과 계정 묶음 토큰을 붙여 Core를 부릅니다. 재시도하지 않습니다."""

    def __init__(
        self, *, base_url: str, secret: str | None, timeout_seconds: float,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._secret = secret
        self._client = httpx.AsyncClient(
            base_url=base_url.rstrip("/"), timeout=timeout_seconds, transport=transport,
            headers={"Accept": "application/json"},
        )

    @property
    def enabled(self) -> bool:
        return bool(self._secret)

    async def get(self, path: str, principal: AssistantPrincipal, params: dict[str, str] | None = None) -> Any:
        if not self._secret:
            raise ToolCallError("tools are disabled")
        query = {"accountId": str(principal.account_id), **{k: v for k, v in (params or {}).items() if v}}
        try:
            response = await self._client.get(
                f"{TOOLS_PATH_PREFIX}{path}", params=query,
                headers={SECRET_HEADER: self._secret, TOKEN_HEADER: principal.tool_token},
            )
        except httpx.HTTPError as error:
            raise ToolCallError(type(error).__name__) from error
        if response.status_code != 200:
            raise ToolCallError(f"status {response.status_code}")
        try:
            return response.json()
        except ValueError as error:
            raise ToolCallError("invalid json") from error

    async def aclose(self) -> None:
        await self._client.aclose()


@dataclass
class ToolResult:
    name: str
    ms: int
    data: Any


@dataclass
class GuideRunContext:
    """한 번의 답변 실행에만 쓰는 상태입니다. 도구 결과는 카드 검증에 다시 씁니다. 모델에는 전달되지 않습니다."""

    principal: AssistantPrincipal | None
    client: CoreToolClient
    max_tool_calls: int
    results: list[ToolResult] = field(default_factory=list)


def _member_only(context: RunContextWrapper[GuideRunContext], _agent: Agent[GuideRunContext]) -> bool:
    return context.context.principal is not None and context.context.client.enabled


async def _call(context: RunContextWrapper[GuideRunContext], name: str, path: str, params: dict[str, str] | None = None) -> str:
    principal = context.context.principal
    if principal is None:
        raise ToolCallError("tools need a signed-in member")
    # 실행 턴 상한은 마지막 턴의 도구까지 실행하므로, 도구 횟수는 여기서 정확히 막습니다.
    if len(context.context.results) >= context.context.max_tool_calls:
        raise ToolCallError("tool call limit reached")
    started = perf_counter()
    data = sanitize(await context.context.client.get(path, principal, params))
    context.context.results.append(ToolResult(name=name, ms=round((perf_counter() - started) * 1000), data=data))
    return json.dumps(data, ensure_ascii=False)


# 도구가 실패하면 모델에 오류 문장을 넘겨 그럴듯한 답을 만들게 하지 않고 실행 전체를 실패로 끝냅니다.
@function_tool(is_enabled=_member_only, failure_error_function=None)
async def get_my_company_profile(context: RunContextWrapper[GuideRunContext]) -> str:
    """내 기업 프로필(등록 여부·상호·지역·업종·설립연도·파트너 역할·관심 분야·역량)을 읽는다. 연락처는 없다."""
    return await _call(context, "get_my_company_profile", "/company-profile")


@function_tool(is_enabled=_member_only, failure_error_function=None)
async def search_partner_recruitments(
    context: RunContextWrapper[GuideRunContext],
    region: str | None = None,
    seeking_role: Literal["LEAD", "PARTICIPANT", "DEMAND"] | None = None,
    keyword: str | None = None,
) -> str:
    """모집 중인 다른 기업의 파트너 모집글을 마감 임박순으로 최대 30건 찾는다. 내 글은 제외된다.

    Args:
        region: 모집 지역 이름(예: 서울, 경기). 사용자가 이번 말에서 지역을 직접 말했을 때만 넣고, 모르면 비운다.
        seeking_role: 내 기업이 맡을 역할. 주관하려면 LEAD, 참여하려면 PARTICIPANT, 수요기업이면 DEMAND. 모르면 비운다.
        keyword: 제목·본문에서 찾을 짧은 키워드. 모르면 비운다.
    """
    params = {
        "region": (region or "")[:50], "seekingRole": seeking_role or "", "keyword": (keyword or "")[:100],
    }
    return await _call(context, "search_partner_recruitments", "/recruitments", params)


@function_tool(is_enabled=_member_only, failure_error_function=None)
async def list_saved_programs(context: RunContextWrapper[GuideRunContext]) -> str:
    """내 관심 공고함의 공고(제목·기관·접수 마감일·상태)를 최대 10건 읽는다. 공고 원문 내용은 없다."""
    return await _call(context, "list_saved_programs", "/saved-programs")


GUIDE_TOOLS = [get_my_company_profile, search_partner_recruitments, list_saved_programs]


def card_catalog(results: list[ToolResult]) -> dict[tuple[str, str], dict[str, Any]]:
    """도구 결과에서 카드가 될 수 있는 항목입니다. 제목·부제·경로는 모델이 아니라 여기서 정합니다."""
    catalog: dict[tuple[str, str], dict[str, Any]] = {}
    for result in results:
        if not isinstance(result.data, list):
            continue
        for item in result.data:
            if not isinstance(item, dict):
                continue
            if result.name == "search_partner_recruitments" and isinstance(item.get("id"), int) and not isinstance(item.get("id"), bool):
                identifier = str(item["id"])
                catalog[("RECRUITMENT", identifier)] = {
                    "kind": "RECRUITMENT", "id": identifier, "title": _short(item.get("title")),
                    "subtitle": _subtitle(item.get("companyName"), item.get("region"), item.get("recruitmentDeadline")),
                    "to": f"{RECRUITMENT_DETAIL_ROUTE}?{urlencode({'recruitmentId': identifier})}",
                }
            elif result.name == "list_saved_programs" and isinstance(item.get("sourceCode"), str) and isinstance(item.get("sourceProgramId"), str):
                identifier = f"{item['sourceCode']}:{item['sourceProgramId']}"
                catalog[("PROGRAM", identifier)] = {
                    "kind": "PROGRAM", "id": identifier, "title": _short(item.get("title")),
                    "subtitle": _subtitle(item.get("organization"), None, item.get("applicationEndDate")),
                    "to": f"{PROGRAM_DETAIL_ROUTE}?{urlencode({'sourceCode': item['sourceCode'], 'sourceProgramId': item['sourceProgramId']})}",
                }
    return catalog


def sanitize(value: Any, depth: int = 0) -> Any:
    """Core가 이미 가린 결과를 한 번 더 검사합니다: 개인정보·URL 제거, 문자열·목록 절단, 깊이 제한."""
    if depth > MAX_NESTING:
        return None
    if isinstance(value, str):
        text = _URL.sub("[링크]", value)
        text = _EMAIL.sub("[이메일]", text)
        text = _RESIDENT_NUMBER.sub("[주민등록번호]", text)
        text = _BUSINESS_NUMBER.sub("[사업자등록번호]", text)
        text = _PHONE_NUMBER.sub("[전화번호]", text)
        text = "".join(character for character in text if character in "\n\t" or character.isprintable())
        return text if len(text) <= MAX_TEXT_LENGTH else text[:MAX_TEXT_LENGTH].rstrip() + "…"
    if isinstance(value, bool) or value is None or isinstance(value, (int, float)):
        return value
    if isinstance(value, list):
        return [sanitize(item, depth + 1) for item in value[:MAX_LIST_ITEMS]]
    if isinstance(value, dict):
        return {
            str(key)[:64]: (item[:200] if key in IDENTIFIER_KEYS and isinstance(item, str) else sanitize(item, depth + 1))
            for key, item in list(value.items())[:40]
        }
    return None


def _short(value: Any) -> str:
    text = str(value).strip() if isinstance(value, str) and value.strip() else "제목 없음"
    return text if len(text) <= 160 else text[:159] + "…"


def _subtitle(*parts: Any) -> str | None:
    text = " · ".join(str(part).strip() for part in parts if isinstance(part, (str, int)) and str(part).strip())
    return (text if len(text) <= 160 else text[:159] + "…") or None
