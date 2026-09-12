import re
from enum import StrEnum
from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

MAX_HELP_ENTRIES = 50
MAX_HELP_ANSWER_LENGTH = 600
_ENTRY_ID_PATTERN = re.compile(r"^[a-z][a-z0-9]*(-[a-z0-9]+)*$")


class HelpEntryInput(BaseModel):
    """화면이 보유한 도움말 항목 한 건. 항목 수가 적어 전량을 컨텍스트에 넣고 검색하지 않는다."""

    model_config = ConfigDict(extra="forbid", frozen=True, populate_by_name=True)

    id: str = Field(min_length=2, max_length=64)
    title: str = Field(min_length=1, max_length=120)
    summary: str = Field(min_length=1, max_length=400)
    body: list[Annotated[str, Field(min_length=1, max_length=1_000)]] = Field(min_length=1, max_length=8)
    limitation: str = Field(min_length=1, max_length=400)

    @field_validator("id")
    @classmethod
    def require_kebab_case_id(cls, value: str) -> str:
        if not _ENTRY_ID_PATTERN.fullmatch(value):
            raise ValueError("help entry id must be kebab-case")
        return value


class HelpAnswerRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, populate_by_name=True)

    question: str = Field(min_length=1, max_length=500)
    entries: list[HelpEntryInput] = Field(min_length=1, max_length=MAX_HELP_ENTRIES)

    @field_validator("question", mode="before")
    @classmethod
    def strip_question(cls, value: object) -> object:
        return value.strip() if isinstance(value, str) else value

    @model_validator(mode="after")
    def require_unique_entry_ids(self) -> "HelpAnswerRequest":
        ids = [entry.id for entry in self.entries]
        if len(ids) != len(set(ids)):
            raise ValueError("help entry ids must be unique")
        return self


class HelpAnswerStatus(StrEnum):
    """답하거나, 답하지 않는 이유를 밝힌다. 기권 문구는 화면이 가지고 있어 여기서 새로 쓰지 않는다."""

    ANSWERED = "ANSWERED"
    OUT_OF_SCOPE_PROGRAM = "OUT_OF_SCOPE_PROGRAM"
    OUT_OF_SCOPE_GENERAL = "OUT_OF_SCOPE_GENERAL"
    NOT_IN_HELP = "NOT_IN_HELP"


class HelpAnswerSelection(BaseModel):
    """LLM은 항목 ID를 만들지 않고 이번 요청 배열의 위치만 고른다."""

    model_config = ConfigDict(extra="forbid", frozen=True, populate_by_name=True)

    answer: str = Field(default="", max_length=MAX_HELP_ANSWER_LENGTH)
    answer_status: HelpAnswerStatus = Field(alias="answerStatus")
    citation_indexes: list[Annotated[int, Field(strict=True, ge=0, le=MAX_HELP_ENTRIES - 1)]] = Field(
        alias="citationIndexes", default_factory=list, max_length=3,
    )

    @field_validator("answer", mode="before")
    @classmethod
    def strip_answer(cls, value: object) -> object:
        return value.strip() if isinstance(value, str) else value

    @model_validator(mode="after")
    def require_status_consistent_citations(self) -> "HelpAnswerSelection":
        if len(self.citation_indexes) != len(set(self.citation_indexes)):
            raise ValueError("citation indexes must be unique")
        if self.answer_status is HelpAnswerStatus.ANSWERED:
            if not self.citation_indexes:
                raise ValueError("ANSWERED requires at least one citation")
            if not self.answer:
                raise ValueError("ANSWERED requires an answer")
        elif self.citation_indexes:
            raise ValueError("only ANSWERED may cite help entries")
        return self


class HelpAnswerResponse(BaseModel):
    """Core에 돌려주는 검증 완료 답변. 기권일 때 answer는 비어 있고 화면이 정해진 문구를 씁니다."""

    model_config = ConfigDict(extra="forbid", frozen=True, populate_by_name=True)

    answer: str = Field(default="", max_length=MAX_HELP_ANSWER_LENGTH)
    answer_status: HelpAnswerStatus = Field(alias="answerStatus")
    citation_entry_ids: list[str] = Field(alias="citationEntryIds", default_factory=list, max_length=3)
