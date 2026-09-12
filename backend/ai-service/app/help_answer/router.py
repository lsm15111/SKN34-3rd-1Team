from typing import Annotated
import logging
from time import perf_counter

from fastapi import APIRouter, Depends, HTTPException, Request, status

from app.help_answer.errors import HelpAnswerError, HelpAnswerTimeoutError
from app.help_answer.models import HelpAnswerRequest, HelpAnswerResponse
from app.help_answer.service import HelpAnswerService


router = APIRouter(prefix="/internal/v1/help", tags=["internal"])
logger = logging.getLogger(__name__)


def get_help_answer_service(request: Request) -> HelpAnswerService:
    return request.app.state.container.help_answer_service


@router.post("/answers", response_model=HelpAnswerResponse, summary="도움말 항목 근거 답변")
async def answer_help_question(
    payload: HelpAnswerRequest,
    service: Annotated[HelpAnswerService, Depends(get_help_answer_service)],
) -> HelpAnswerResponse:
    started = perf_counter()
    try:
        return await service.answer(payload)
    except HelpAnswerError as error:
        timed_out = isinstance(error, HelpAnswerTimeoutError)
        # 실패 종류와 예외 이름, 소요 시간만 남깁니다. 질문 본문과 모델 출력은 기록하지 않습니다.
        logger.warning(
            "help_answer_failed failure_kind=%s error_type=%s elapsed_ms=%d",
            "timeout" if timed_out else "execution",
            type(error.__cause__ or error).__name__,
            round((perf_counter() - started) * 1_000),
        )
        raise HTTPException(
            status_code=status.HTTP_504_GATEWAY_TIMEOUT if timed_out else status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Help answer timed out." if timed_out else "Help answer is temporarily unavailable.",
        ) from error
