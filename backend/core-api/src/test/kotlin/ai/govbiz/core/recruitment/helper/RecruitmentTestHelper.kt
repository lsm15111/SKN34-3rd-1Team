package ai.govbiz.core.recruitment.helper

import ai.govbiz.core.account.domain.Company
import ai.govbiz.core.account.helper.AccountTestHelper
import ai.govbiz.core.recruitment.domain.ProposalDecision
import ai.govbiz.core.recruitment.domain.RecruitmentPost
import ai.govbiz.core.recruitment.domain.RecruitmentPostDraft
import ai.govbiz.core.recruitment.domain.RecruitmentProposal
import ai.govbiz.core.recruitment.domain.RecruitmentRole
import ai.govbiz.core.recruitment.repository.StoredRecruitmentPost
import ai.govbiz.core.recruitment.repository.StoredRecruitmentProposal
import ai.govbiz.core.supportprogram.domain.SupportProgram
import ai.govbiz.core.supportprogram.domain.SupportProgramStatus
import java.time.LocalDate
import java.time.LocalDateTime

/** recruitment 기능 테스트가 함께 쓰는 모집글·공고 예시입니다. 오늘은 AccountTestHelper.FIXED_CLOCK(2026-09-06)입니다. */
object RecruitmentTestHelper {

    val TODAY: LocalDate = LocalDate.of(2026, 9, 6)
    val NOW: LocalDateTime = LocalDateTime.of(2026, 9, 6, 12, 0)

    fun draft(
        title: String = "AI 실증 과제 데이터 구축·라벨링 참여기관 구합니다",
        body: String = "학습용 민원 문서 정제와 라벨링을 맡아 주실 참여기관을 찾습니다.",
        closesOn: LocalDate = LocalDate.of(2026, 9, 20),
        requiredCapabilities: List<String> = listOf("데이터 구축", "라벨링 운영"),
    ): RecruitmentPostDraft =
        RecruitmentPostDraft(
            title = title,
            body = body,
            ourRole = RecruitmentRole.LEAD,
            wantedRole = RecruitmentRole.PARTICIPANT,
            wantedCompanyCount = 1,
            wantedRegion = "서울·경기·인천",
            requiredCapabilities = requiredCapabilities,
            closesOn = closesOn,
        )

    fun post(
        id: Long = 1L,
        companyId: Long = 1L,
        draft: RecruitmentPostDraft = draft(),
        closedEarlyAt: LocalDateTime? = null,
        hiddenAt: LocalDateTime? = null,
        hiddenReason: String? = null,
    ): RecruitmentPost =
        RecruitmentPost(
            id = id,
            companyId = companyId,
            authorAccountId = 1L,
            sourceCode = "BIZINFO",
            sourceProgramId = "PBLN_000000091203",
            draft = draft,
            closedEarlyAt = closedEarlyAt,
            hiddenAt = hiddenAt,
            hiddenReason = hiddenReason,
            createdAt = NOW,
            updatedAt = NOW,
        )

    fun stored(post: RecruitmentPost = post(), companyId: Long = post.companyId): StoredRecruitmentPost =
        StoredRecruitmentPost(post = post, company = AccountTestHelper.company(id = companyId))

    /** 모집글 작성 기업(id 1)과 다른 제안 기업입니다. */
    fun otherCompany(id: Long = 2L): Company =
        Company(id = id, businessNumber = "2208162517", companyName = "비전솔루션", businessStatus = "계속사업자")

    fun proposal(
        id: Long = 1L,
        postId: Long = 1L,
        companyId: Long = 2L,
        message: String = "공공 데이터 라벨링 운영 경험이 있는 참여기관입니다. 세부 비율은 협의하겠습니다.",
        decision: ProposalDecision = ProposalDecision.PENDING,
        decidedAt: LocalDateTime? = null,
        createdAt: LocalDateTime = NOW,
    ): RecruitmentProposal =
        RecruitmentProposal(
            id = id,
            postId = postId,
            companyId = companyId,
            proposerAccountId = 2L,
            message = message,
            decision = decision,
            decidedAt = decidedAt,
            createdAt = createdAt,
        )

    fun storedProposal(
        proposal: RecruitmentProposal = proposal(),
        proposerEmail: String = "partner@vision.co.kr",
    ): StoredRecruitmentProposal =
        StoredRecruitmentProposal(proposal = proposal, company = otherCompany(proposal.companyId), proposerEmail = proposerEmail)

    fun program(
        status: SupportProgramStatus = SupportProgramStatus.OPEN,
        applicationEndDate: LocalDate? = LocalDate.of(2026, 9, 30),
    ): SupportProgram =
        SupportProgram(
            id = "PBLN_000000091203",
            sourceCode = "BIZINFO",
            title = "서울 AI 스타트업 실증 지원사업",
            organization = "서울경제진흥원",
            summary = "서울 소재 AI 스타트업의 실증 과제를 지원합니다.",
            categories = listOf("AI"),
            regions = listOf("서울"),
            targetDescription = "서울 소재 창업 7년 이내 AI 기업",
            applicationPeriod = "2026. 9. 1. ~ 2026. 9. 30. 18:00",
            applicationStartDate = LocalDate.of(2026, 9, 1),
            applicationEndDate = applicationEndDate,
            status = status,
            sourceName = "기업마당",
            sourceUrl = "https://www.bizinfo.go.kr/web/lay1/bbs/S1T122C128/AS/74/view.do?pblancId=PBLN_000000091203",
            matchedReasons = emptyList(),
        )
}
