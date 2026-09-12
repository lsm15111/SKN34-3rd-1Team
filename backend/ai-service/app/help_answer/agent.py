import asyncio
import json
import logging
from time import perf_counter

from agents import (
    Agent,
    MaxTurnsExceeded,
    Model,
    ModelBehaviorError,
    ModelRefusalError,
    ModelSettings,
    ModelTimeoutError,
    RunConfig,
    Runner,
)
from openai import APITimeoutError, OpenAIError
from openai.types.shared import Reasoning
from pydantic import ValidationError

from app.help_answer.errors import HelpAnswerError, HelpAnswerTimeoutError
from app.help_answer.models import HelpAnswerRequest, HelpAnswerResponse, HelpAnswerSelection
from app.help_answer.prompt import HELP_ANSWER_INSTRUCTIONS


logger = logging.getLogger(__name__)


class HelpAnswerAgent:
    """한 번의 structured LLM 호출로 도움말 항목에 근거한 답변을 만든다. 항목 전량을 넣으므로 검색 단계가 없다."""

    def __init__(self, *, model: Model, model_timeout_seconds: float, run_timeout_seconds: float) -> None:
        self._run_timeout_seconds = run_timeout_seconds
        self._agent: Agent[None] = Agent(
            name="GovBiz Help Answerer",
            instructions=HELP_ANSWER_INSTRUCTIONS,
            model=model,
            output_type=HelpAnswerSelection,
            model_settings=ModelSettings(
                max_tokens=1_000,
                reasoning=Reasoning(effort="none"),
                store=False,
                timeout=model_timeout_seconds,
                extra_args={"timeout": model_timeout_seconds},
            ),
        )
        self._run_config = RunConfig(
            workflow_name="GovBiz help answer",
            tracing_disabled=True,
            trace_include_sensitive_data=False,
        )

    async def answer(self, request: HelpAnswerRequest) -> HelpAnswerResponse:
        started_at = perf_counter()
        model_finished_at = None
        usage = None
        outcome = "failed"
        # 항목 순서를 고정해 보내면 프롬프트 앞부분이 그대로라 캐시가 먹습니다. ID는 모델에게 주지 않습니다.
        payload = {
            "question": request.question,
            "entries": [
                {"index": index, **entry.model_dump(by_alias=True, exclude={"id"})}
                for index, entry in enumerate(request.entries)
            ],
        }
        try:
            async with asyncio.timeout(self._run_timeout_seconds):
                result = await Runner.run(
                    self._agent,
                    json.dumps(payload, ensure_ascii=False),
                    max_turns=1,
                    run_config=self._run_config,
                )
            model_finished_at = perf_counter()
            usage = getattr(getattr(result, "context_wrapper", None), "usage", None)
            if not isinstance(result.final_output, HelpAnswerSelection):
                raise HelpAnswerError()
            selection = HelpAnswerSelection.model_validate(result.final_output.model_dump(by_alias=True))
            if any(index >= len(request.entries) for index in selection.citation_indexes):
                raise HelpAnswerError()
            answer = HelpAnswerResponse(
                answer=selection.answer,
                answerStatus=selection.answer_status,
                citationEntryIds=[request.entries[index].id for index in selection.citation_indexes],
            )
            outcome = "completed"
            return answer
        except (ModelTimeoutError, APITimeoutError, TimeoutError) as error:
            raise HelpAnswerTimeoutError() from error
        except (MaxTurnsExceeded, ModelBehaviorError, ModelRefusalError, OpenAIError, ValidationError) as error:
            raise HelpAnswerError() from error
        except asyncio.CancelledError:
            outcome = "cancelled"
            raise
        finally:
            finished_at = perf_counter()
            usage_reported = usage is not None and bool(usage.request_usage_entries)
            logger.info(
                "help_answer_run outcome=%s model_ms=%d validation_ms=%d elapsed_ms=%d "
                "usage_reported=%s input_tokens=%s output_tokens=%s cached_input_tokens=%s reasoning_tokens=%s",
                outcome, round(((model_finished_at or finished_at) - started_at) * 1000),
                round((finished_at - model_finished_at) * 1000) if model_finished_at is not None else 0,
                round((finished_at - started_at) * 1000), usage_reported,
                usage.input_tokens if usage_reported else None,
                usage.output_tokens if usage_reported else None,
                usage.input_tokens_details.cached_tokens if usage_reported else None,
                usage.output_tokens_details.reasoning_tokens if usage_reported else None,
            )
