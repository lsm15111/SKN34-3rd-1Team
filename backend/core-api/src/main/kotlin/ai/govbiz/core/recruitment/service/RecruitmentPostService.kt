package ai.govbiz.core.recruitment.service

import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.recruitment.domain.ContactPatternPolicy
import ai.govbiz.core.recruitment.domain.RecruitmentPostDraft
import ai.govbiz.core.recruitment.domain.RecruitmentPostStatus
import ai.govbiz.core.recruitment.domain.RecruitmentPostStatusResolver
import ai.govbiz.core.recruitment.repository.RecruitmentPostRepository
import ai.govbiz.core.recruitment.repository.StoredRecruitmentPost
import ai.govbiz.core.recruitment.service.dto.RecruitmentPostPageResult
import ai.govbiz.core.recruitment.service.dto.RecruitmentPostResult
import ai.govbiz.core.recruitment.service.exception.ContactInTextException
import ai.govbiz.core.recruitment.service.exception.NotPostOwnerException
import ai.govbiz.core.recruitment.service.exception.RecruitmentClosesOnInvalidException
import ai.govbiz.core.recruitment.service.exception.RecruitmentPostNotFoundException
import ai.govbiz.core.recruitment.service.exception.RecruitmentPostNotOpenException
import ai.govbiz.core.recruitment.service.exception.SupportProgramNotOpenException
import ai.govbiz.core.supportprogram.domain.SupportProgram
import ai.govbiz.core.supportprogram.domain.SupportProgramStatus
import ai.govbiz.core.supportprogram.repository.SupportProgramRepository
import java.time.Clock
import java.time.LocalDate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

/**
 * 공식 공고에 묶인 파트너 모집글의 등록·수정·조기 마감·조회입니다.
 *
 * 연결 공고는 지원사업 Repository에서 읽기만 하며, 모집 상태는 저장하지 않고 조회 시 계산합니다.
 */
@Service
class RecruitmentPostService(
    private val repository: RecruitmentPostRepository,
    private val supportProgramRepository: SupportProgramRepository,
    @param:Qualifier("seoulClock") private val clock: Clock,
) {

    fun create(
        author: Account,
        sourceCode: String,
        sourceProgramId: String,
        draft: RecruitmentPostDraft,
    ): RecruitmentPostResult {
        rejectContact(draft)
        val program = findPresentProgram(sourceCode, sourceProgramId)
            ?.takeIf { it.status != SupportProgramStatus.CLOSED }
            ?: throw SupportProgramNotOpenException()
        requireValidClosesOn(draft.closesOn, program)

        val stored = repository.create(
            companyId = author.company.id,
            authorAccountId = author.id,
            sourceCode = program.sourceCode,
            sourceProgramId = program.id,
            draft = draft,
        )
        return stored.toResult(program, viewer = author)
    }

    fun update(author: Account, postId: Long, draft: RecruitmentPostDraft): RecruitmentPostResult {
        rejectContact(draft)
        val (stored, program) = requireOpenOwnedPost(author, postId)
        requireValidClosesOn(draft.closesOn, program)

        return repository.update(postId, draft).toResult(program, viewer = author)
    }

    fun closeEarly(author: Account, postId: Long): RecruitmentPostResult {
        val (_, program) = requireOpenOwnedPost(author, postId)
        check(repository.closeEarly(postId)) { "recruitment post was not closed" }

        return requireNotNull(repository.findById(postId)).toResult(program, viewer = author)
    }

    /** 숨김·종료된 글은 작성 기업에게만 보이고 다른 사용자에게는 없는 글입니다. */
    fun get(postId: Long, viewer: Account?): RecruitmentPostResult {
        val stored = repository.findById(postId) ?: throw RecruitmentPostNotFoundException()
        val result = stored.toResult(findPresentProgram(stored.post.sourceCode, stored.post.sourceProgramId), viewer)
        if (result.status != RecruitmentPostStatus.OPEN && !result.isOwner) {
            throw RecruitmentPostNotFoundException()
        }
        return result
    }

    fun listOpen(
        sourceCode: String?,
        sourceProgramId: String?,
        page: Int,
        size: Int,
        viewer: Account?,
    ): RecruitmentPostPageResult {
        val stored = repository.findOpenPage(sourceCode, sourceProgramId, page, size)
        return RecruitmentPostPageResult(
            posts = java.util.List.copyOf(
                stored.posts.map { it.toResult(findPresentProgram(it.post.sourceCode, it.post.sourceProgramId), viewer) },
            ),
            page = stored.page,
            size = stored.size,
            totalCount = stored.totalCount,
        )
    }

    /** 작성 기업의 글은 상태와 무관하게 모두 보입니다. */
    fun listMine(author: Account): List<RecruitmentPostResult> =
        java.util.List.copyOf(
            repository.findByCompanyId(author.company.id)
                .map { it.toResult(findPresentProgram(it.post.sourceCode, it.post.sourceProgramId), author) },
        )

    private fun requireOpenOwnedPost(author: Account, postId: Long): Pair<StoredRecruitmentPost, SupportProgram?> {
        val stored = repository.findById(postId) ?: throw RecruitmentPostNotFoundException()
        if (!stored.post.isOwnedBy(author.company.id)) throw NotPostOwnerException()
        val program = findPresentProgram(stored.post.sourceCode, stored.post.sourceProgramId)
        val status = RecruitmentPostStatusResolver.resolve(stored.post, program, LocalDate.now(clock))
        if (status != RecruitmentPostStatus.OPEN) throw RecruitmentPostNotOpenException()
        return stored to program
    }

    private fun requireValidClosesOn(closesOn: LocalDate, program: SupportProgram?) {
        val today = LocalDate.now(clock)
        if (closesOn.isBefore(today)) throw RecruitmentClosesOnInvalidException()
        val programEnd = program?.applicationEndDate ?: return
        if (closesOn.isAfter(programEnd)) throw RecruitmentClosesOnInvalidException()
    }

    private fun rejectContact(draft: RecruitmentPostDraft) {
        if (ContactPatternPolicy.containsContact(draft.title) || ContactPatternPolicy.containsContact(draft.body)) {
            throw ContactInTextException()
        }
    }

    private fun findPresentProgram(sourceCode: String, sourceProgramId: String): SupportProgram? =
        supportProgramRepository.findPresentBySourceAndProgramId(sourceCode, sourceProgramId)?.program

    private fun StoredRecruitmentPost.toResult(program: SupportProgram?, viewer: Account?): RecruitmentPostResult =
        RecruitmentPostResult(
            post = post,
            status = RecruitmentPostStatusResolver.resolve(post, program, LocalDate.now(clock)),
            company = company,
            program = program,
            isOwner = viewer != null && post.isOwnedBy(viewer.company.id),
        )
}
