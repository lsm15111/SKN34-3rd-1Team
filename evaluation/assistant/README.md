# GovBiz 가이드 자유 질문 회귀 평가

GovBiz 가이드의 자유 질문은 AI Service `POST /internal/v1/assistant/answers`(`govbiz-assistant-v2`)의 에이전트 하나가 의도를 고르고,
로그인 회원이면 Core 읽기 도구(기업 프로필·파트너 모집글·관심 공고 목록)로 답과 카드를 만든다. 이 자료는 프롬프트·모델을 바꿨을 때
**의도·인용·도구 선택·카드가 흔들리지 않는지** 확인하는 고정 질문 세트다. 흐름은 [아키텍처](../../docs/architecture.md)의 "GovBiz 가이드 자유 질문" 절을 참고한다.

이 자료는 **AI가 작성한 가상 질문 70개**이며 실제 사용자 질문이 아니고 사람 검증 정답도 아니다. 답 문장의 품질·말투, Core의 템플릿 답,
프런트 표시는 평가 범위 밖이다.

## 실행

프로젝트 루트에서 AI Service 가상환경으로 실행한다. 기본 실행은 질문·도움말·요청 계약만 검증하고 모델을 부르지 않는다.

```bash
uv run --project backend/ai-service python evaluation/assistant/evaluate.py
uv run --project backend/ai-service python -m unittest discover -s evaluation/assistant -p 'test_*.py'
```

실제 측정은 `--live`다. `OPENAI_API_KEY`가 필요하고 서비스와 같은 설정(`OPENAI_ASSISTANT_MODEL` 기본 `gpt-5.6-luna`,
`OPENAI_ASSISTANT_REASONING_EFFORT` 기본 `low`, `ASSISTANT_AGENT_MAX_TOOL_CALLS` 기본 3)을 읽는다. 문항마다 모델을 1~3번 부르므로 비용이 든다.
요청 본문과 답변 문장은 보고서에 남기지 않는다.

```bash
uv run --project backend/ai-service python evaluation/assistant/evaluate.py --live --report evaluation/assistant/runs/<이름>/report.json
uv run --project backend/ai-service python evaluation/assistant/evaluate.py --live --mode agent
uv run --project backend/ai-service python evaluation/assistant/evaluate.py --live --split heldout
uv run --project backend/ai-service python evaluation/assistant/evaluate.py --live --case H02-1 --case M08
```

로그인 세션 문항은 서비스처럼 `principal`을 싣고, 도구는 가짜 Core 서버(`agent_fixtures.py`: 가상 회사·모집글 2건·관심 공고 3건)가 답한다.
모집글 하나의 본문에는 도구 결과 안의 지시를 따르지 않는지 보려고 일부러 명령 문장을 넣었다.

## 데이터

`questions.json`(`assistant-intent-eval-v3`)의 문항은 `id`, `message`, `expectedIntent`, `split`과 필요할 때 `expectedCitation`(사용법),
`expectedAccountTopic`(상태), `session`·`context`(생략하면 비로그인·`/` 화면)를 가진다.

- 사용법 30개: Core 카탈로그 10항목 × 표현 3개. 세 번째 표현은 `heldout`.
- 회원 상태 4개, 검색 3개, 공고 질문 3개(공고 상세 화면·`programSelected: true`).
- 답할 수 없는 10개: 범위 밖 7개(`OUT_OF_SCOPE`), 정보 부족 3개(`UNCLEAR`).
- 도구 문항 20개(`mode: "agent"`, 로그인 세션): 모집글 매칭 8·관심 공고 묶음 8·도구 상태 4. `expectedTools`는 호출해야 할 도구 집합,
  `expectedCards`는 카드에 포함돼야 할 id다. 관심 공고 묶음 문항은 원문 없이 목록 도구만 쓰므로 v3에서 기대 카드를 없앴고, 기업 미등록 회원의 모집글 매칭(M08)은 Core가 등록 안내로 답하므로 도구를 부르지 않는다.
- 도움말 항목은 복사하지 않고 실행할 때 `backend/core-api/src/main/resources/assistant/help-catalog.json`을 읽는다. 항목 id가 바뀌면
  `expectedCitation`도 고쳐야 하며 검증 단계에서 걸린다.
- `dev`는 프롬프트를 고칠 때 보는 분할, `heldout`은 고친 뒤 한 번 확인하는 분할이다. 같은 항목의 표현이 양쪽에 있으므로 독립적인
  일반화 성능을 뜻하지 않는다.

## 보고서

`--live` 보고서의 `summary`:

| 지표 | 뜻 |
|---|---|
| `intentAccuracy` | 의도가 기대와 같은 비율(전체·`perIntent`·`perSplit`) |
| `helpCitationAccuracy` | 사용법 30문항에서 기대 도움말 id가 인용에 포함된 비율 |
| `accountTopicAccuracy` | 상태 문항에서 `accountTopic`이 맞은 비율 |
| `abstainRateOnUnanswerable` | 답할 수 없는 10문항에서 `OUT_OF_SCOPE`·`UNCLEAR`로 기권한 비율(높을수록 좋음) |
| `falseAbstainRateOnAnswerable` | 답할 수 있는 문항에서 기권한 비율(낮을수록 좋음) |
| `toolSelectionAccuracy` | 도구 문항에서 호출한 도구 집합이 `expectedTools`와 같은 비율 |
| `cardValidityRate` | 카드 id가 전부 가짜 Core 자료 안에 있는 비율(서비스 검증 때문에 1이 아니면 오류로 끝남) |
| `expectedCardsIncludedRate` | `expectedCards`가 카드에 포함된 비율 |
| `answeredRate` | 도구 문항에서 답 문장을 만든 비율 |

`confusion`은 기대 의도별 실제 의도 분포, `results`는 문항별 판정·도구·지연을 담는다. 호출 실패는 `error`로 남고 점수에서 뺀다.
`questionsSha256`·`helpCatalogSha256`으로 어느 질문·카탈로그 버전으로 측정했는지 남긴다. 보고서는 `runs/<이름>/`에 두고 같은 질문을
수정하지 않는다. 문항을 고치면 질문 스키마 버전을 올린다.

이전 구조(분류 경로·LangGraph 도구 경로)의 측정 기록은 `runs/intent-*`, `runs/agent-*`에 남아 있으며 v2 계약과 직접 비교할 수 없다.

## 측정 기록 (v2 계약)

| 실행 | 설정 | 결과 |
|---|---|---|
| `runs/guide-20260917-v1` | 70문항, `gpt-5.6-luna`/low, 도구 상한 3 | 오류 2, 의도 65/68(0.956), 인용 30/30, 기권 10/10·오기권 0, 도구 선택 18/19, 카드 유효 100%, 평균 3.6초. 실패: 관심 공고 개수·마감 질문 3건을 `SAVED_PROGRAMS_QUESTION`으로 분류, 받은 제안 2건이 답 없이 이동 버튼만 채워 계약 위반 |
| `runs/guide-20260917-v2-subset` | 위 실패·인접 12문항, 프롬프트·출력 정리 보강 후 | 오류 0, 의도 12/12, 계정 영역 8/8, 도구 선택 12/12, 카드 유효 100%, 평균 6.9초(도구 문항 위주, 모집글 매칭 최장 14.8초) |

v2-subset은 전체 70문항 재측정이 아니므로 전체 정확도로 읽지 않는다.
