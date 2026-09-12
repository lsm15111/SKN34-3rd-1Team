import pytest


@pytest.fixture
def request_data() -> dict:
    return {
        "question": "점수는 무슨 뜻인가요?",
        "entries": [
            {
                "id": "relevance-score-meaning",
                "title": "점수는 무엇을 뜻하나요",
                "summary": "점수는 검색어와 공고의 관련도입니다.",
                "body": ["점수는 검색 문장과 공고 내용이 얼마나 가까운지를 나타냅니다."],
                "limitation": "다른 검색의 점수와 비교할 수 없습니다.",
            },
            {
                "id": "saved-program-basics",
                "title": "관심 공고함에 공고 담기",
                "summary": "공고 상세에서 담은 공고가 관심 공고함에 모입니다.",
                "body": ["목록에서 제목을 누르면 다시 상세로 갑니다."],
                "limitation": "관심 공고함이 마감을 알려 주지는 않습니다.",
            },
        ],
    }


@pytest.fixture
def output_data() -> dict:
    return {
        "answer": "점수는 검색어와 공고의 관련도입니다. 신청 자격을 뜻하지 않습니다.",
        "answerStatus": "ANSWERED",
        "citationIndexes": [0],
    }
