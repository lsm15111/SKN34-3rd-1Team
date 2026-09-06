package ai.govbiz.core.recruitment.repository.mapper

import java.time.LocalDateTime
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

/** 참여 제안 MySQL SQL을 실행하는 MyBatis Mapper입니다. */
@Mapper
interface RecruitmentProposalMapper {

    fun insert(row: RecruitmentProposalDbRow): Int

    /** 아직 PENDING인 제안만 바꿉니다. 이미 결정된 제안이면 0을 돌려줍니다. */
    fun decide(
        @Param("id") id: Long,
        @Param("decision") decision: String,
        @Param("decidedAt") decidedAt: LocalDateTime,
    ): Int

    fun findById(@Param("id") id: Long): RecruitmentProposalDbRow?

    fun findByPostId(@Param("postId") postId: Long): List<RecruitmentProposalDbRow>

    fun findByCompanyId(@Param("companyId") companyId: Long): List<RecruitmentProposalDbRow>

    fun findByPostIdsAndCompanyId(
        @Param("postIds") postIds: Collection<Long>,
        @Param("companyId") companyId: Long,
    ): List<RecruitmentProposalDbRow>

    /** 철회한 제안은 세지 않습니다. */
    fun countByPostIds(@Param("postIds") postIds: Collection<Long>): List<ProposalCountDbRow>
}
