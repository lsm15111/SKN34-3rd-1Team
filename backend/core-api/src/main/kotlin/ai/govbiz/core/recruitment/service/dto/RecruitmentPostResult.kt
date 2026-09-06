package ai.govbiz.core.recruitment.service.dto

import ai.govbiz.core.account.domain.Company
import ai.govbiz.core.recruitment.domain.RecruitmentPost
import ai.govbiz.core.recruitment.domain.RecruitmentPostStatus
import ai.govbiz.core.supportprogram.domain.SupportProgram

/** 상태·작성 기업·연결 공고·조회자 관계를 채운 모집글입니다. 연결 공고가 더 이상 공개되지 않으면 null입니다. */
data class RecruitmentPostResult(
    val post: RecruitmentPost,
    val status: RecruitmentPostStatus,
    val company: Company,
    val program: SupportProgram?,
    val isOwner: Boolean,
)

data class RecruitmentPostPageResult(
    val posts: List<RecruitmentPostResult>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
)
