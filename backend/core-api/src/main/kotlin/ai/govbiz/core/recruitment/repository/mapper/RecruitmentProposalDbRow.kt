package ai.govbiz.core.recruitment.repository.mapper

import java.time.LocalDateTime

/** MyBatis가 제안 한 행을 제안 기업·담당자 이메일 컬럼과 함께 읽고 쓰기 위한 DB 행 값입니다. */
data class RecruitmentProposalDbRow(
    var id: Long = 0,
    var postId: Long = 0,
    var companyId: Long = 0,
    var proposerAccountId: Long = 0,
    var message: String = "",
    var decision: String = "PENDING",
    var decidedAt: LocalDateTime? = null,
    var createdAt: LocalDateTime? = null,
    var companyBusinessNumber: String = "",
    var companyName: String = "",
    var companyBusinessStatus: String = "",
    var proposerEmail: String = "",
)

/** 모집글별로 센 제안 수입니다. */
data class ProposalCountDbRow(
    var postId: Long = 0,
    var proposalCount: Int = 0,
)
