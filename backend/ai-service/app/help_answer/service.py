from pydantic import ValidationError

from app.help_answer.agent import HelpAnswerAgent
from app.help_answer.errors import HelpAnswerError
from app.help_answer.models import HelpAnswerRequest, HelpAnswerResponse, HelpAnswerStatus


class HelpAnswerService:
    """인용이 요청한 항목 집합을 벗어나지 않는지, 기권이 답을 숨기지 않는지 검증한다."""

    def __init__(self, agent: HelpAnswerAgent) -> None:
        self._agent = agent

    async def answer(self, request: HelpAnswerRequest) -> HelpAnswerResponse:
        output = await self._agent.answer(request)
        try:
            answer = HelpAnswerResponse.model_validate(output.model_dump(by_alias=True))
        except ValidationError as error:
            raise HelpAnswerError() from error
        if not set(answer.citation_entry_ids).issubset({entry.id for entry in request.entries}):
            raise HelpAnswerError()
        if answer.answer_status is HelpAnswerStatus.ANSWERED:
            if not answer.answer or not answer.citation_entry_ids:
                raise HelpAnswerError()
            return answer
        # 기권 문구는 화면이 가지고 있습니다. 모델이 쓴 문장을 그대로 내보내지 않습니다.
        return HelpAnswerResponse(answer="", answerStatus=answer.answer_status, citationEntryIds=[])
