package ai.govbiz.core.recruitment.repository

import ai.govbiz.core.account.domain.Company
import ai.govbiz.core.recruitment.domain.RecruitmentPost
import ai.govbiz.core.recruitment.domain.RecruitmentPostDraft
import ai.govbiz.core.recruitment.domain.RecruitmentRole
import ai.govbiz.core.recruitment.repository.mapper.RecruitmentPostDbRow
import ai.govbiz.core.recruitment.repository.mapper.RecruitmentPostMapper
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

/** 모집글과 작성 기업 정보를 함께 읽는 저장된 행입니다. 상태·연결 공고는 Service가 채웁니다. */
data class StoredRecruitmentPost(
    val post: RecruitmentPost,
    val company: Company,
)

data class RecruitmentPostPage(
    val posts: List<StoredRecruitmentPost>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
)

/** 파트너 모집글을 MySQL에 저장하고 읽습니다. 표시 상태는 저장하지 않습니다. */
@Repository
class RecruitmentPostRepository(
    private val mapper: RecruitmentPostMapper,
    private val objectMapper: ObjectMapper,
    @param:Qualifier("seoulClock") private val clock: Clock,
) {

    @Transactional
    fun create(
        companyId: Long,
        authorAccountId: Long,
        sourceCode: String,
        sourceProgramId: String,
        draft: RecruitmentPostDraft,
    ): StoredRecruitmentPost {
        val now = now()
        val row = RecruitmentPostDbRow(
            companyId = companyId,
            authorAccountId = authorAccountId,
            sourceCode = sourceCode,
            sourceProgramId = sourceProgramId,
            createdAt = now,
            updatedAt = now,
        ).applyDraft(draft)
        check(mapper.insert(row) == 1) { "recruitment post row was not created" }
        return requireNotNull(findById(row.id)) { "recruitment post row was not readable" }
    }

    /** 연결 공고는 바꾸지 않고 모집 조건만 갱신합니다. */
    @Transactional
    fun update(id: Long, draft: RecruitmentPostDraft): StoredRecruitmentPost {
        val row = RecruitmentPostDbRow(id = id, updatedAt = now()).applyDraft(draft)
        check(mapper.update(row) == 1) { "recruitment post row was not updated" }
        return requireNotNull(findById(id)) { "recruitment post row was not readable" }
    }

    /** 이미 조기 마감된 글은 다시 갱신하지 않습니다. */
    @Transactional
    fun closeEarly(id: Long): Boolean = mapper.closeEarly(id, now()) == 1

    fun findById(id: Long): StoredRecruitmentPost? = mapper.findById(id)?.toStored()

    fun findOpenPage(
        sourceCode: String?,
        sourceProgramId: String?,
        page: Int,
        size: Int,
    ): RecruitmentPostPage {
        require(page >= 0) { "page must not be negative" }
        require(size in 1..MAX_PAGE_SIZE) { "size must be between 1 and $MAX_PAGE_SIZE" }
        require((sourceCode == null) == (sourceProgramId == null)) {
            "sourceCode and sourceProgramId must be given together"
        }
        val today = LocalDate.now(clock)
        return RecruitmentPostPage(
            posts = java.util.List.copyOf(
                mapper.findOpenPage(sourceCode, sourceProgramId, today, page * size, size).map { it.toStored() },
            ),
            page = page,
            size = size,
            totalCount = mapper.countOpen(sourceCode, sourceProgramId, today),
        )
    }

    fun findByCompanyId(companyId: Long): List<StoredRecruitmentPost> =
        java.util.List.copyOf(mapper.findByCompanyId(companyId).map { it.toStored() })

    private fun now(): LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS)

    private fun RecruitmentPostDbRow.applyDraft(draft: RecruitmentPostDraft): RecruitmentPostDbRow = apply {
        title = draft.title
        body = draft.body
        ourRole = draft.ourRole.name
        wantedRole = draft.wantedRole.name
        wantedCompanyCount = draft.wantedCompanyCount
        wantedRegion = draft.wantedRegion
        requiredCapabilitiesJson = objectMapper.writeValueAsString(draft.requiredCapabilities)
        closesOn = draft.closesOn
    }

    private fun RecruitmentPostDbRow.toStored(): StoredRecruitmentPost =
        StoredRecruitmentPost(
            post = RecruitmentPost(
                id = id,
                companyId = companyId,
                authorAccountId = authorAccountId,
                sourceCode = sourceCode,
                sourceProgramId = sourceProgramId,
                draft = RecruitmentPostDraft(
                    title = title,
                    body = body,
                    ourRole = RecruitmentRole.valueOf(ourRole),
                    wantedRole = RecruitmentRole.valueOf(wantedRole),
                    wantedCompanyCount = wantedCompanyCount,
                    wantedRegion = wantedRegion,
                    requiredCapabilities = java.util.List.copyOf(
                        objectMapper.readValue(requiredCapabilitiesJson, STRING_LIST_TYPE),
                    ),
                    closesOn = requireNotNull(closesOn) { "closesOn must not be null" },
                ),
                closedEarlyAt = closedEarlyAt,
                hiddenAt = hiddenAt,
                hiddenReason = hiddenReason,
                createdAt = requireNotNull(createdAt) { "createdAt must not be null" },
                updatedAt = requireNotNull(updatedAt) { "updatedAt must not be null" },
            ),
            company = Company(
                id = companyId,
                businessNumber = companyBusinessNumber,
                companyName = companyName,
                businessStatus = companyBusinessStatus,
            ),
        )

    private companion object {
        const val MAX_PAGE_SIZE = 50
        val STRING_LIST_TYPE = object : TypeReference<List<String>>() {}
    }
}
