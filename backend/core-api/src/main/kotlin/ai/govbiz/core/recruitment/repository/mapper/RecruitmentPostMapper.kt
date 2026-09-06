package ai.govbiz.core.recruitment.repository.mapper

import java.time.LocalDate
import java.time.LocalDateTime
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

/** 파트너 모집글 MySQL SQL을 실행하는 MyBatis Mapper입니다. */
@Mapper
interface RecruitmentPostMapper {

    fun insert(row: RecruitmentPostDbRow): Int

    fun update(row: RecruitmentPostDbRow): Int

    fun closeEarly(
        @Param("id") id: Long,
        @Param("closedEarlyAt") closedEarlyAt: LocalDateTime,
    ): Int

    fun findById(@Param("id") id: Long): RecruitmentPostDbRow?

    /** 숨김·조기 마감이 없고 모집 마감일이 지나지 않았으며 연결 공고가 현재 공개된 글만, 마감 임박순으로 읽습니다. */
    fun findOpenPage(
        @Param("sourceCode") sourceCode: String?,
        @Param("sourceProgramId") sourceProgramId: String?,
        @Param("today") today: LocalDate,
        @Param("offset") offset: Int,
        @Param("limit") limit: Int,
    ): List<RecruitmentPostDbRow>

    fun countOpen(
        @Param("sourceCode") sourceCode: String?,
        @Param("sourceProgramId") sourceProgramId: String?,
        @Param("today") today: LocalDate,
    ): Long

    fun findByCompanyId(@Param("companyId") companyId: Long): List<RecruitmentPostDbRow>
}
