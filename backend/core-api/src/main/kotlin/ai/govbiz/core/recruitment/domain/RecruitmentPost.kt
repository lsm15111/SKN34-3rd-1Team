package ai.govbiz.core.recruitment.domain

import java.time.LocalDate
import java.time.LocalDateTime

/** 컨소시엄에서 맡는 역할입니다. 작성 기업은 주관·참여만 가능하고, 수요처는 찾는 역할로만 씁니다. */
enum class RecruitmentRole {
    LEAD,
    PARTICIPANT,
    DEMAND,
}

/** 저장하지 않고 읽을 때 계산하는 모집글 표시 상태입니다. */
enum class RecruitmentPostStatus {
    OPEN,
    CLOSED,
    HIDDEN,
}

/** 작성·수정 폼이 보내는 모집 조건입니다. 연결 공고는 등록 뒤 바꾸지 않습니다. */
data class RecruitmentPostDraft(
    val title: String,
    val body: String,
    val ourRole: RecruitmentRole,
    val wantedRole: RecruitmentRole,
    val wantedCompanyCount: Int,
    val wantedRegion: String,
    val requiredCapabilities: List<String>,
    val closesOn: LocalDate,
) {
    init {
        require(title.isNotBlank() && title == title.trim() && title.length <= MAX_TITLE_LENGTH) {
            "title must be a trimmed value of 1..$MAX_TITLE_LENGTH characters"
        }
        require(body.isNotBlank() && body.length <= MAX_BODY_LENGTH) {
            "body must be 1..$MAX_BODY_LENGTH characters"
        }
        require(ourRole != RecruitmentRole.DEMAND) { "ourRole must be LEAD or PARTICIPANT" }
        require(wantedCompanyCount in 1..MAX_WANTED_COMPANY_COUNT) {
            "wantedCompanyCount must be 1..$MAX_WANTED_COMPANY_COUNT"
        }
        require(wantedRegion == wantedRegion.trim() && wantedRegion.length <= MAX_REGION_LENGTH) {
            "wantedRegion must be a trimmed value of at most $MAX_REGION_LENGTH characters"
        }
        require(requiredCapabilities.size <= MAX_CAPABILITIES) {
            "requiredCapabilities must contain at most $MAX_CAPABILITIES items"
        }
        require(
            requiredCapabilities.all {
                it.isNotBlank() && it == it.trim() && it.length <= MAX_CAPABILITY_LENGTH
            },
        ) { "each capability must be a trimmed value of 1..$MAX_CAPABILITY_LENGTH characters" }
        require(requiredCapabilities.toSet().size == requiredCapabilities.size) {
            "requiredCapabilities must not repeat"
        }
    }

    companion object {
        const val MAX_TITLE_LENGTH = 80
        const val MAX_BODY_LENGTH = 2_000
        const val MAX_WANTED_COMPANY_COUNT = 10
        const val MAX_REGION_LENGTH = 60
        const val MAX_CAPABILITIES = 10
        const val MAX_CAPABILITY_LENGTH = 30
    }
}

/** 공식 공고 하나에 묶인 파트너 모집글입니다. 상태는 [RecruitmentPostStatusResolver]가 계산합니다. */
data class RecruitmentPost(
    val id: Long,
    val companyId: Long,
    val authorAccountId: Long,
    val sourceCode: String,
    val sourceProgramId: String,
    val draft: RecruitmentPostDraft,
    val closedEarlyAt: LocalDateTime?,
    val hiddenAt: LocalDateTime?,
    val hiddenReason: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    init {
        require(hiddenAt == null || !hiddenReason.isNullOrBlank()) { "hidden posts must carry a reason" }
    }

    fun isOwnedBy(companyId: Long): Boolean = this.companyId == companyId
}
