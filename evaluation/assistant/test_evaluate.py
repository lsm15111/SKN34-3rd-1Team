"""모델 호출 없이 Core 도움말 카탈로그·질문 세트·채점 규칙을 검증한다."""

import unittest

from evaluate import build_requests, is_agent_case, load_help_entries, load_questions, score, score_agent, summarize, summarize_agent


class HelpCatalogTest(unittest.TestCase):
    def test_reads_every_core_catalog_entry_in_the_ai_service_contract_shape(self):
        entries = load_help_entries()
        ids = [entry["id"] for entry in entries]
        self.assertIn("search-score-meaning", ids)
        self.assertIn("partner-write-requires-company", ids)
        for entry in entries:
            self.assertEqual(
                set(entry), {"id", "title", "question", "summary", "body", "limitation", "audience", "status", "action"},
            )
            if entry["action"] is not None:
                self.assertRegex(entry["action"]["to"], r"^/app/[A-Za-z0-9/-]*$", "action route must be a bare /app path")
        status_entry = next(entry for entry in entries if entry["id"] == "status-unknown-source")
        self.assertEqual(status_entry["action"], {"label": "필터 검색 열기", "to": "/app/chat"})


class QuestionSetTest(unittest.TestCase):
    def test_every_question_validates_against_the_single_guide_contract(self):
        from agent_fixtures import ACCOUNT_ID

        fixture = load_questions()
        prepared = build_requests(fixture, load_help_entries())
        self.assertEqual(len(prepared), len(fixture["cases"]))
        agent_cases = [(case, request) for case, request in prepared if is_agent_case(case)]
        self.assertEqual(len(agent_cases), 20)
        self.assertEqual(len([case for case, _ in prepared if case["expectedIntent"] == "PRODUCT_HELP"]), 30, "도움말 10항목 × 표현 3개")
        self.assertEqual(len([case for case, _ in prepared if case["expectedIntent"] in ("OUT_OF_SCOPE", "UNCLEAR")]), 10)
        for case, request in prepared:
            self.assertEqual(request.message, case["message"])
            self.assertEqual(request.principal is not None, request.session.authenticated)
            if request.principal is not None:
                self.assertEqual(request.principal.account_id, ACCOUNT_ID)
                self.assertEqual(request.principal.has_company, request.session.has_company)
            if case["expectedIntent"] == "PROGRAM_QUESTION":
                self.assertTrue(request.context.program_selected, f"{case['id']}: 공고 질문은 상세 화면에서 묻는다")
            if case["expectedIntent"] == "SAVED_PROGRAMS_QUESTION":
                # 원문 청크 없이 목록 도구만 쓰므로 원문 내용에 기대는 기대 카드를 두지 않는다.
                self.assertEqual(case["expectedTools"], ["list_saved_programs"])
                self.assertNotIn("expectedCards", case)


class ScoringTest(unittest.TestCase):
    def test_scores_tools_cards_and_answers(self):
        from agent_fixtures import RECRUITMENT_IDS, SAVED_PROGRAM_IDS

        valid = RECRUITMENT_IDS | SAVED_PROGRAM_IDS
        match_case = {"id": "M", "split": "dev", "mode": "agent", "expectedIntent": "PARTNER_MATCH",
                      "expectedTools": ["get_my_company_profile", "search_partner_recruitments"], "expectedCards": ["21"]}
        output = {"intent": "PARTNER_MATCH", "answer": "답", "cards": [{"id": "21"}, {"id": "22"}],
                  "toolCalls": [{"name": "get_my_company_profile", "ms": 3}, {"name": "search_partner_recruitments", "ms": 5}]}
        good = score_agent(match_case, output, valid)
        self.assertTrue(good["intentCorrect"] and good["toolsCorrect"] and good["cardsValid"] and good["expectedCardsIncluded"] and good["answered"])
        bad = score_agent(match_case, {**output, "cards": [{"id": "999"}], "toolCalls": [{"name": "search_partner_recruitments", "ms": 1}]}, valid)
        self.assertFalse(bad["toolsCorrect"] or bad["cardsValid"] or bad["expectedCardsIncluded"])
        classify = score_agent({"id": "S", "split": "dev", "expectedIntent": "SEARCH"}, {"intent": "SEARCH", "answer": None, "cards": [], "toolCalls": []}, valid)
        self.assertIsNone(classify["toolsCorrect"])
        failed = score_agent(match_case, None, valid)
        failed["error"] = "AssistantAnswerError"

        summary = summarize_agent([good, bad, classify, failed])
        self.assertEqual(summary["agentCases"], 2)
        self.assertEqual(summary["toolSelectionAccuracy"], 0.5)
        self.assertEqual(summary["cardValidityRate"], round(2 / 3, 4))
        self.assertEqual(summary["answeredRate"], 1.0)

    def test_scores_intent_citation_topic_and_abstain(self):
        help_case = {"id": "H", "split": "dev", "expectedIntent": "PRODUCT_HELP", "expectedCitation": "search-score-meaning"}
        account_case = {"id": "A", "split": "heldout", "expectedIntent": "ACCOUNT_STATE", "expectedAccountTopic": "SAVED_PROGRAMS"}
        oos_case = {"id": "N", "split": "dev", "expectedIntent": "OUT_OF_SCOPE"}
        results = [
            score(help_case, {"intent": "PRODUCT_HELP", "citations": ["eligibility-unknown"]}),
            score(account_case, {"intent": "ACCOUNT_STATE", "accountTopic": "SAVED_PROGRAMS"}),
            score(oos_case, {"intent": "UNCLEAR"}),
            score(oos_case, None),
        ]
        self.assertTrue(results[0]["intentCorrect"])
        self.assertFalse(results[0]["citationCorrect"])
        self.assertTrue(results[1]["accountTopicCorrect"])
        self.assertFalse(results[2]["intentCorrect"])
        self.assertTrue(results[2]["abstained"], "UNCLEAR도 기권으로 센다")
        self.assertEqual(results[3]["error"], "no output")

        summary = summarize(results)
        self.assertEqual(summary["scored"], 3)
        self.assertEqual(summary["errors"], 1)
        self.assertAlmostEqual(summary["intentAccuracy"], 2 / 3, places=3)
        self.assertEqual(summary["helpCitationAccuracy"], 0.0)
        self.assertEqual(summary["accountTopicAccuracy"], 1.0)
        self.assertEqual(summary["abstainRateOnUnanswerable"], 1.0)
        self.assertEqual(summary["falseAbstainRateOnAnswerable"], 0.0)
        self.assertEqual(summary["perSplit"]["heldout"]["intentAccuracy"], 1.0)
        self.assertIn("PARTNER_MATCH", summary["perIntent"])


if __name__ == "__main__":
    unittest.main()
