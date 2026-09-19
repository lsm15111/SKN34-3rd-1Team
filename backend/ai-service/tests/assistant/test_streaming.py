"""구조화 출력 조각에서 답변 문장만 뽑아내는 규칙입니다. 조각이 어디서 잘려도 같은 글자가 나와야 합니다."""

import json

import pytest

from app.assistant.streaming import AnswerTextStream


def feed_in_pieces(text: str, size: int) -> str:
    stream = AnswerTextStream()
    return "".join(stream.feed(text[index:index + size]) for index in range(0, len(text), size))


@pytest.mark.parametrize("size", [1, 2, 3, 7, 40, 10_000])
def test_answer_text_is_the_same_no_matter_where_the_chunks_are_cut(size):
    answer = '줄바꿈\n따옴표 "인용" 역슬래시 \\ 그리고 이모지 😀'
    payload = json.dumps(
        {"intent": "PRODUCT_HELP", "answer": answer, "citations": ["search-score-meaning"], "navigation": "NONE"},
        ensure_ascii=False,
    )
    assert feed_in_pieces(payload, size) == answer
    # 한글을 \uXXXX로 보내는 직렬화에서도 같은 글자가 나옵니다.
    assert feed_in_pieces(json.dumps({"intent": "PRODUCT_HELP", "answer": answer}), size) == answer


def test_only_the_top_level_answer_value_is_read():
    payload = json.dumps({
        "intent": "PARTNER_MATCH",
        "cards": [{"kind": "RECRUITMENT", "id": "21", "reason": "answer 라는 낱말이 들어간 이유"}],
        "answer": "진짜 답입니다.",
        "nested": {"answer": "안쪽 답은 읽지 않습니다."},
    }, ensure_ascii=False)
    assert feed_in_pieces(payload, 5) == "진짜 답입니다."


def test_a_null_answer_yields_nothing_and_stops_reading():
    stream = AnswerTextStream()
    assert stream.feed('{"intent":"UNCLEAR","answer":null,"clarificationQuestion":"무엇이 궁금하세요?"}') == ""
    assert stream.finished


def test_reading_stops_at_the_end_of_the_answer_value():
    stream = AnswerTextStream()
    assert stream.feed('{"intent":"OUT_OF_SCOPE","answer":"여기까지"') == "여기까지"
    assert stream.finished
    # 값이 끝난 뒤에 오는 조각은 무시합니다.
    assert stream.feed(',"citations":[]}') == ""


def test_an_unfinished_escape_is_held_until_it_completes():
    stream = AnswerTextStream()
    assert stream.feed('{"answer":"앞') == "앞"
    assert stream.feed("\\u00") == ""
    assert stream.feed("41") == "A"
    assert stream.feed("\\n뒤") == "\n뒤"


def test_a_broken_escape_stops_instead_of_making_up_characters():
    stream = AnswerTextStream()
    assert stream.feed('{"answer":"앞\\uZZZZ뒤"}') == "앞"
    assert stream.finished
