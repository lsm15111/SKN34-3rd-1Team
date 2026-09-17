import asyncio
import json
import logging
from time import perf_counter

from agents import (
    Agent, AgentsException, Model, ModelSettings, ModelTimeoutError, RunConfig, Runner,
)
from openai import APITimeoutError, OpenAIError
from openai.types.shared import Reasoning
from pydantic import ValidationError

from app.assistant.errors import AssistantAnswerError, AssistantAnswerTimeoutError, ToolCallError
from app.assistant.models import AssistantAnswerOutput, AssistantAnswerRequest
from app.assistant.prompt import ASSISTANT_INSTRUCTIONS
from app.assistant.tools import GUIDE_TOOLS, CoreToolClient, GuideRunContext, ToolResult


logger = logging.getLogger(__name__)


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
            usage_reported = usage is not None and bool(usage.request_usage_entries)
            # 시간·호출 수·토큰만 남깁니다. 질문·대화·답·도구 결과 본문은 남기지 않습니다.
            logger.info(
                "assistant_answer_run outcome=%s model_calls=%d tool_calls=%d elapsed_ms=%d usage_reported=%s "
                "input_tokens=%s output_tokens=%s cached_input_tokens=%s reasoning_tokens=%s",
                outcome, model_calls, len(context.results), round((perf_counter() - started_at) * 1000), usage_reported,
                usage.input_tokens if usage_reported else None,
                usage.output_tokens if usage_reported else None,
                usage.input_tokens_details.cached_tokens if usage_reported else None,
                usage.output_tokens_details.reasoning_tokens if usage_reported else None,
            )


def model_input(request: AssistantAnswerRequest) -> str:
    """모델에 보내는 사용자 메시지입니다. principal(계정 번호·토큰)은 절대 넣지 않습니다.

    질문마다 같은 도움말 카탈로그를 질문보다 앞에 두어, 지침·도구 정의·카탈로그까지가 프롬프트 캐시의 같은 접두사가 되게 합니다.
    """
    data = request.model_dump(by_alias=True, exclude={"principal"})
    ordered = {"schemaVersion": data["schemaVersion"], "helpEntries": data["helpEntries"]}
    ordered.update((key, value) for key, value in data.items() if key not in ordered)
    return json.dumps(ordered, ensure_ascii=False)
