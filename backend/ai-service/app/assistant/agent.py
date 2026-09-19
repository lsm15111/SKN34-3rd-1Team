import asyncio
import json
import logging
from collections.abc import AsyncIterator
from dataclasses import dataclass
from time import perf_counter

from agents import (
    Agent, AgentsException, Model, ModelSettings, ModelTimeoutError, RunConfig, Runner,
)
from openai import APITimeoutError, OpenAIError
from openai.types.responses import ResponseCreatedEvent, ResponseTextDeltaEvent
from openai.types.shared import Reasoning
from pydantic import ValidationError

from app.assistant.errors import AssistantAnswerError, AssistantAnswerTimeoutError, ToolCallError
from app.assistant.models import AssistantAnswerOutput, AssistantAnswerRequest
from app.assistant.prompt import ASSISTANT_INSTRUCTIONS
from app.assistant.streaming import AnswerTextStream
from app.assistant.tools import GUIDE_TOOLS, CoreToolClient, GuideRunContext, ToolResult


logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class GuideStatusEvent:
    """지금 무엇을 하고 있는지입니다. `reading`은 회원 자료를 읽는 중이고 `tool`에 도구 이름이 들어갑니다."""

    phase: str
    tool: str | None = None


@dataclass(frozen=True)
class GuideTextEvent:
    """답변 문장 조각입니다. 아직 검증 전이라 화면은 마지막 결과로 다시 덮어씁니다."""

    delta: str


@dataclass(frozen=True)
class GuideFinalEvent:
    output: AssistantAnswerOutput
    results: list[ToolResult]


class AssistantAgent:
    """GovBiz 가이드 에이전트 하나입니다. 의도를 고르고, 로그인 회원이면 읽기 도구로 자료를 모아 답과 카드를 고릅니다."""

    def __init__(
        self, *, model: Model, tool_client: CoreToolClient, model_timeout_seconds: float, run_timeout_seconds: float,
        max_tool_calls: int, reasoning_effort: str = "low",
    ) -> None:
        if not 1 <= max_tool_calls <= 6:
            raise ValueError("max_tool_calls must be between 1 and 6")
        self._tool_client = tool_client
        self._max_tool_calls = max_tool_calls
        self._run_timeout_seconds = run_timeout_seconds
        # 모델 호출 한 번은 도구 한 번 또는 최종 답입니다. 도구를 max_tool_calls번 부르고 답하면 끝납니다.
        self._max_turns = max_tool_calls + 1
        self._agent: Agent[GuideRunContext] = Agent(
            name="GovBiz Guide",
            instructions=ASSISTANT_INSTRUCTIONS,
            model=model,
            tools=list(GUIDE_TOOLS),
            output_type=AssistantAnswerOutput,
            model_settings=ModelSettings(
                max_tokens=1_600, reasoning=Reasoning(effort=reasoning_effort), store=False,
                parallel_tool_calls=False, timeout=model_timeout_seconds,
                # Keep the per-request HTTP deadline aligned without mutating the shared client.
                extra_args={"timeout": model_timeout_seconds},
            ),
        )
        self._run_config = RunConfig(
            workflow_name="GovBiz guide answer",
            tracing_disabled=True, trace_include_sensitive_data=False,
        )

    async def answer(self, request: AssistantAnswerRequest) -> tuple[AssistantAnswerOutput, list[ToolResult]]:
        started_at = perf_counter()
        context = GuideRunContext(principal=request.principal, client=self._tool_client, max_tool_calls=self._max_tool_calls)
        usage = None
        model_calls = 0
        outcome = "failed"
        try:
            async with asyncio.timeout(self._run_timeout_seconds):
                result = await Runner.run(
                    self._agent, model_input(request), context=context,
                    max_turns=self._max_turns, run_config=self._run_config,
                )
            usage = getattr(getattr(result, "context_wrapper", None), "usage", None)
            model_calls = len(getattr(result, "raw_responses", []) or [])
            if not isinstance(result.final_output, AssistantAnswerOutput):
                raise AssistantAnswerError()
            output = AssistantAnswerOutput.model_validate(result.final_output.model_dump(by_alias=True))
            outcome = "completed"
            return output, list(context.results)
        except (ModelTimeoutError, APITimeoutError, TimeoutError) as error:
            outcome = "timeout"
            raise AssistantAnswerTimeoutError() from error
        except AssistantAnswerError:
            raise
        except (ToolCallError, AgentsException, OpenAIError, ValidationError) as error:
            raise AssistantAnswerError() from error
        except asyncio.CancelledError:
            outcome = "cancelled"
            raise
        finally:
            self._log_run(outcome, model_calls, len(context.results), started_at, usage)

    async def answer_stream(self, request: AssistantAnswerRequest) -> AsyncIterator[object]:
        """같은 실행을 하면서 진행 상황과 답변 조각을 먼저 내보냅니다. 모델 호출 수와 토큰은 [answer]와 같습니다.

        실행은 따로 만든 작업이 맡고 이 함수는 큐에서 꺼내 넘기기만 합니다. 시간 제한을 그 작업에만 걸어,
        화면이 늦게 읽는다고 해서 실행이 중간에 끊기지 않게 합니다.
        """
        context = GuideRunContext(principal=request.principal, client=self._tool_client, max_tool_calls=self._max_tool_calls)
        queue: asyncio.Queue[object] = asyncio.Queue()
        producer = asyncio.create_task(self._produce(request, context, queue))
        try:
            yield GuideStatusEvent("thinking")
            while True:
                item = await queue.get()
                if isinstance(item, BaseException):
                    raise item
                if item is None:
                    return
                yield item
        finally:
            # 화면이 끊겼으면 실행도 멈춥니다. 답을 끝까지 만들어 봐야 보여 줄 곳이 없습니다.
            producer.cancel()

    async def _produce(self, request: AssistantAnswerRequest, context: GuideRunContext, queue: asyncio.Queue[object]) -> None:
        started_at = perf_counter()
        usage = None
        model_calls = 0
        outcome = "failed"
        try:
            async with asyncio.timeout(self._run_timeout_seconds):
                result = Runner.run_streamed(
                    self._agent, model_input(request), context=context,
                    max_turns=self._max_turns, run_config=self._run_config,
                )
                # 구조화 출력은 호출마다 새로 시작하므로, 마지막 호출의 답만 조각으로 나갑니다.
                text = AnswerTextStream()
                async for event in result.stream_events():
                    if event.type == "raw_response_event":
                        if isinstance(event.data, ResponseCreatedEvent):
                            text = AnswerTextStream()
                        elif isinstance(event.data, ResponseTextDeltaEvent) and not text.finished:
                            delta = text.feed(event.data.delta)
                            if delta:
                                await queue.put(GuideTextEvent(delta))
                    elif event.type == "run_item_stream_event" and event.name == "tool_called":
                        await queue.put(GuideStatusEvent("reading", tool_name(event.item)))
                usage = getattr(getattr(result, "context_wrapper", None), "usage", None)
                model_calls = len(getattr(result, "raw_responses", []) or [])
                if not isinstance(result.final_output, AssistantAnswerOutput):
                    raise AssistantAnswerError()
                output = AssistantAnswerOutput.model_validate(result.final_output.model_dump(by_alias=True))
            outcome = "completed"
            await queue.put(GuideFinalEvent(output, list(context.results)))
            await queue.put(None)
        except (ModelTimeoutError, APITimeoutError, TimeoutError) as error:
            outcome = "timeout"
            await queue.put(AssistantAnswerTimeoutError())
        except AssistantAnswerError as error:
            await queue.put(error)
        except (ToolCallError, AgentsException, OpenAIError, ValidationError) as error:
            await queue.put(AssistantAnswerError())
        except asyncio.CancelledError:
            outcome = "cancelled"
            raise
        finally:
            self._log_run(outcome, model_calls, len(context.results), started_at, usage, streamed=True)

    def _log_run(self, outcome: str, model_calls: int, tool_calls: int, started_at: float, usage, streamed: bool = False) -> None:
        usage_reported = usage is not None and bool(usage.request_usage_entries)
        # 시간·호출 수·토큰만 남깁니다. 질문·대화·답·도구 결과 본문은 남기지 않습니다.
        logger.info(
            "assistant_answer_run outcome=%s streamed=%s model_calls=%d tool_calls=%d elapsed_ms=%d usage_reported=%s "
            "input_tokens=%s output_tokens=%s cached_input_tokens=%s reasoning_tokens=%s",
            outcome, streamed, model_calls, tool_calls, round((perf_counter() - started_at) * 1000), usage_reported,
            usage.input_tokens if usage_reported else None,
            usage.output_tokens if usage_reported else None,
            usage.input_tokens_details.cached_tokens if usage_reported else None,
            usage.output_tokens_details.reasoning_tokens if usage_reported else None,
        )


def tool_name(item: object) -> str | None:
    name = getattr(getattr(item, "raw_item", None), "name", None)
    return name if isinstance(name, str) else None


def model_input(request: AssistantAnswerRequest) -> str:
    """모델에 보내는 사용자 메시지입니다. principal(계정 번호·토큰)은 절대 넣지 않습니다.

    질문마다 같은 도움말 카탈로그를 질문보다 앞에 두어, 지침·도구 정의·카탈로그까지가 프롬프트 캐시의 같은 접두사가 되게 합니다.
    """
    data = request.model_dump(by_alias=True, exclude={"principal"})
    ordered = {"schemaVersion": data["schemaVersion"], "helpEntries": data["helpEntries"]}
    ordered.update((key, value) for key, value in data.items() if key not in ordered)
    return json.dumps(ordered, ensure_ascii=False)
