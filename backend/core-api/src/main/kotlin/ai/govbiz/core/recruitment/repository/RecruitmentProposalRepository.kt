package ai.govbiz.core.recruitment.repository

import ai.govbiz.core.account.domain.Company
import ai.govbiz.core.recruitment.domain.ProposalDecision
import ai.govbiz.core.recruitment.domain.RecruitmentProposal
import ai.govbiz.core.recruitment.repository.mapper.RecruitmentProposalDbRow
import ai.govbiz.core.recruitment.repository.mapper.RecruitmentProposalMapper
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/** 제안과 제안 기업·담당자 이메일을 함께 읽은 행입니다. 표시 상태·연락처 공개 여부는 Service가 정합니다. */
data class StoredRecruitmentProposal(
    val proposal: RecruitmentProposal,
    val company: Company,
    val proposerEmail: String,
)

/** 참여 제안을 MySQL에 저장하고 읽습니다. 기업당 모집글 하나에 한 건은 `(post_id, company_id)` UNIQUE가 강제합니다. */
@Repository
class RecruitmentProposalRepository(
    private val mapper: RecruitmentProposalMapper,
    @param:Qualifier("seoulClock") private val clock: Clock,
) {

    /** 같은 기업이 같은 글에 이미 제안했으면 [org.springframework.dao.DuplicateKeyException]이 발생합니다. */
    @Transactional
    fun create(
        postId: Long,
        companyId: Long,
        proposerAccountId: Long,
        message: String,
    ): StoredRecruitmentProposal {
        RecruitmentProposal.requireProposalMessage(message)
        val row = RecruitmentProposalDbRow(
            postId = postId,
            companyId = companyId,
            proposerAccountId = proposerAccountId,
            message = message,
            decision = ProposalDecision.PENDING.name,
            createdAt = now(),
        )
        check(mapper.insert(row) == 1) { "recruitment proposal row was not created" }
        return requireNotNull(findById(row.id)) { "recruitment proposal row was not readable" }
    }

    /** 아직 PENDING인 제안만 결정합니다. 이미 결정된 제안이면 false입니다. */
    @Transactional
    fun decide(id: Long, decision: ProposalDecision): Boolean {
        require(decision != ProposalDecision.PENDING) { "decision must be a final value" }
        return mapper.decide(id, decision.name, now()) == 1
    }

    fun findById(id: Long): StoredRecruitmentProposal? = mapper.findById(id)?.toStored()

    /** 최근 제안순입니다. */
    fun findByPostId(postId: Long): List<StoredRecruitmentProposal> =
        java.util.List.copyOf(mapper.findByPostId(postId).map { it.toStored() })

    fun findByCompanyId(companyId: Long): List<StoredRecruitmentProposal> =
        java.util.List.copyOf(mapper.findByCompanyId(companyId).map { it.toStored() })

    /** 한 기업이 여러 모집글에 보낸 제안을 모집글 id로 찾습니다. */
    fun findByPostIdsAndCompanyId(postIds: Collection<Long>, companyId: Long): Map<Long, StoredRecruitmentProposal> {
        if (postIds.isEmpty()) return emptyMap()
        return mapper.findByPostIdsAndCompanyId(postIds, companyId).associate { it.postId to it.toStored() }
    }

    /** 모집글별 받은 제안 수입니다(철회 제외). 제안이 없는 글은 빠집니다. */
    fun countByPostIds(postIds: Collection<Long>): Map<Long, Int> {
        if (postIds.isEmpty()) return emptyMap()
        return mapper.countByPostIds(postIds).associate { it.postId to it.proposalCount }
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS)

    private fun RecruitmentProposalDbRow.toStored(): StoredRecruitmentProposal =
        StoredRecruitmentProposal(
            proposal = RecruitmentProposal(
                id = id,
                postId = postId,
                companyId = companyId,
                proposerAccountId = proposerAccountId,
                message = message,
                decision = ProposalDecision.valueOf(decision),
                decidedAt = decidedAt,
                createdAt = requireNotNull(createdAt) { "createdAt must not be null" },
            ),
            company = Company(
                id = companyId,
                businessNumber = companyBusinessNumber,
                companyName = companyName,
                businessStatus = companyBusinessStatus,
            ),
            proposerEmail = proposerEmail,
        )
}
