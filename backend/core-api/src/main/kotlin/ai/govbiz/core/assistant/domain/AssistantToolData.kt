package ai.govbiz.core.assistant.domain

import java.time.LocalDate

/**
 * 도우미 도구가 읽는 내 기업 요약입니다. 연락처·사업자등록번호·이메일은 넣지 않습니다.
 * 기업이 없으면 `registered=false`이고 나머지는 비어 있습니다.
 */
data class AssistantCompanyProfile(
    val registered: Boolean,
    val companyName: String?,
    val region: String?,
    val industry: String?,
    val foundedYear: Int?,
    val roles: List<String>,
    val interestAreas: List<String>,
    val introduction: String?,
    val capabilities: List<String>,
) {
    companion object {
        val NOT_REGISTERED = AssistantCompanyProfile(false, null, null, null, null, emptyList(), emptyList(), null, emptyList())
    }
}

/** 모집 중인 남의 모집글 요약입니다. 본문은 잘라 넣고 담당자 정보는 넣지 않습니다. */
data class AssistantRecruitmentSummary(
    val id: Long,
    val title: String,
    val companyName: String,
    val companyRegion: String,
    val companyIndustry: String,
    val ownRole: String,
    val seekingRole: String,
    val seekingCount: Int,
    val region: String,
    val minimumCompanyAgeYears: Int?,
    val capabilities: List<String>,
    val recruitmentDeadline: LocalDate,
    val programTitle: String,
    val programApplicationEndDate: LocalDate?,
    val body: String,
)

/** 관심 공고 요약입니다. `documentId`는 원문이 수집돼 근거 검색을 할 수 있을 때만 있습니다. */
data class AssistantSavedProgramSummary(
    val sourceCode: String,
    val sourceProgramId: String,
    val title: String,
    val organization: String,
    val applicationEndDate: LocalDate?,
    val status: String,
    val documentId: String?,
)

/**
 * 신청 준비 건 요약입니다. `progressRevision`은 진행 단계 변경 카드가 낙관적 잠금에 쓰는 값이라 함께 담습니다.
 * `programTitle`은 현재 공고 목록에서 찾은 제목이며, 공고가 내려갔으면 null입니다.
 */
data class AssistantApplicationPreparationSummary(
    val id: Long,
    val sourceCode: String,
    val sourceProgramId: String,
    val programTitle: String?,
    val progressStage: String,
    val progressRevision: Long,
    val updatedAt: LocalDate,
)

/** 중복 검토 요약입니다. 최근 실행의 상태와 날짜만 담고 판정 내용·근거는 담지 않습니다. */
data class AssistantCombinationReviewSummary(
    val id: Long,
    val title: String,
    val inputRevision: Long,
    val programTitles: List<String>,
    /** 아직 한 번도 실행하지 않았으면 null입니다. */
    val latestRunStatus: String?,
    val latestRunId: Long?,
    val latestRunDate: LocalDate?,
    val updatedAt: LocalDate,
)

/** 리포트 구독 상태입니다. 수신 이메일 주소는 담지 않습니다. */
data class AssistantDailyReportStatus(
    val enabled: Boolean,
    val emailConfirmed: Boolean,
    val supportPurpose: String,
    val serviceEnabled: Boolean,
    val sendHour: Int,
    val latestReportDate: LocalDate?,
)

/** 제안함 요약입니다. 상대 기업 이름·연락처·본문은 담지 않습니다. */
data class AssistantProposalSummary(
    val hasCompany: Boolean,
    val receivedPending: Int,
    val sentPending: Int,
    /** 받은 제안 중 가장 빨리 만료되는 날입니다. */
    val earliestExpiryDate: LocalDate?,
)

/** 공개 공고 검색 결과 한 건입니다. AI 점수화 없이 키워드·지역·모집 상태로만 고릅니다. */
data class AssistantProgramSearchResult(
    val sourceCode: String,
    val sourceProgramId: String,
    val title: String,
    val organization: String,
    val applicationEndDate: LocalDate?,
    val status: String,
    val regions: List<String>,
    /** 이미 관심 공고함에 담은 공고인지입니다. 담기·빼기 카드가 이 값으로 갈립니다. */
    val saved: Boolean,
)
