class AssistantAnswerError(Exception):
    """모델·도구 장애 또는 가이드 답변의 계약 위반."""


class AssistantAnswerTimeoutError(AssistantAnswerError):
    """가이드 답변의 모델·HTTP 또는 전체 실행 제한 시간이 소진된 오류."""


class ToolCallError(RuntimeError):
    """Core 내부 도구 호출 실패. 답을 지어내지 않도록 실행 전체를 실패로 끝냅니다."""
