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
                "status": "available",
            },
            {
                "id": "feature-status-preparing",
                "title": "준비 중 배지가 붙은 기능",
                "summary": "준비 중 배지가 붙은 버튼은 아직 동작하지 않습니다.",
                "body": ["관심 공고함 연동과 모집글 저장이 여기에 해당합니다."],
                "limitation": "대체 경로가 없습니다.",
                "status": "preparing",
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
