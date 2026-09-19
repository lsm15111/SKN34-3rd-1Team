from collections.abc import AsyncIterator
from typing import Annotated
import json
import logging
from time import perf_counter

from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.responses import StreamingResponse

from app.assistant.agent import GuideStatusEvent, GuideTextEvent
from app.assistant.errors import AssistantAnswerError, AssistantAnswerTimeoutError
from app.assistant.models import AssistantAnswerRequest, AssistantAnswerResponse
from app.assistant.service import AssistantService


router = APIRouter(prefix="/internal/v1/assistant", tags=["internal"])
logger = logging.getLogger(__name__)


def get_assistant_service(request: Request) -> AssistantService:
    return request.app.state.container.assistant_service


@router.post("/answers", response_model=AssistantAnswerResponse, summary="GovBiz 가이드 자유 질문(의도 분류·회원 자료 도구·답·카드)")
async def answer_assistant_message(
    payload: AssistantAnswerRequest,
    service: Annotated[AssistantService, Depends(get_assistant_service)],
) -> AssistantAnswerResponse:
    started = perf_counter()
    try:
        return await service.answer(payload)
    except AssistantAnswerError as error:
        timed_out = isinstance(error, AssistantAnswerTimeoutError)
        # Only failure kind, exception class and elapsed time; never request/model text or a traceback.
        logger.warning(
            "assistant_answer_failed failure_kind=%s error_type=%s elapsed_ms=%d",
            "timeout" if timed_out else "execution",
            type(error.__cause__ or error).__name__,
            round((perf_counter() - started) * 1_000),
        )
        raise HTTPException(
            status_code=status.HTTP_504_GATEWAY_TIMEOUT if timed_out else status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Assistant answer timed out." if timed_out else "Assistant answer is temporarily unavailable.",
        ) from error


@router.post("/answers/stream", summary="GovBiz 가이드 자유 질문(진행 상황·답변 조각을 먼저 보내는 SSE)")
async def stream_assistant_message(
    payload: AssistantAnswerRequest,
    service: Annotated[AssistantService, Depends(get_assistant_service)],
) -> StreamingResponse:
    """`status`·`text`를 먼저 보내고 마지막에 검증을 마친 `final`을 보냅니다.

    응답 머리글이 이미 나간 뒤에는 상태 코드를 바꿀 수 없으므로, 실패는 `error` 이벤트로 알리고 연결을 닫습니다.
    Core가 그 이벤트를 받아 화면에 실패를 그대로 알립니다.
    """
    return StreamingResponse(
        _events(service, payload),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-store", "X-Accel-Buffering": "no"},
    )


async def _events(service: AssistantService, payload: AssistantAnswerRequest) -> AsyncIterator[bytes]:
    started = perf_counter()
    try:
        async for event in service.answer_stream(payload):
            if isinstance(event, GuideStatusEvent):
                yield _sse("status", {"phase": event.phase, "tool": event.tool})
            elif isinstance(event, GuideTextEvent):
                yield _sse("text", {"delta": event.delta})
            elif isinstance(event, AssistantAnswerResponse):
                yield _sse("final", event.model_dump(by_alias=True))
    except AssistantAnswerError as error:
        timed_out = isinstance(error, AssistantAnswerTimeoutError)
        logger.warning(
            "assistant_answer_failed streamed=True failure_kind=%s error_type=%s elapsed_ms=%d",
            "timeout" if timed_out else "execution",
            type(error.__cause__ or error).__name__,
            round((perf_counter() - started) * 1_000),
        )
        yield _sse("error", {"kind": "timeout" if timed_out else "execution"})


def _sse(event: str, data: dict) -> bytes:
    """SSE 한 덩어리입니다. 줄바꿈이 데이터에 섞여 덩어리가 쪼개지지 않도록 JSON 한 줄로 보냅니다."""
    body = json.dumps(data, ensure_ascii=False)
    return f"event: {event}\ndata: {body}\n\n".encode("utf-8")
