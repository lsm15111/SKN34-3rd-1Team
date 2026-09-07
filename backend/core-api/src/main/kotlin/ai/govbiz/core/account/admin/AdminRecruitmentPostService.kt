package ai.govbiz.core.account.admin

import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.account.service.exception.AdminRequiredException
import ai.govbiz.core.recruitment.domain.RecruitmentPostStatus
import ai.govbiz.core.recruitment.service.RecruitmentPostService
import ai.govbiz.core.recruitment.service.dto.RecruitmentPostPageResult
import ai.govbiz.core.recruitment.service.dto.RecruitmentPostResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 운영자의 모집글 목록·숨김·해제·강제 마감입니다.
 *
 * 관리자 역할만 확인하고 조치를 서버 로그에 남기며, 실제 상태 변경 규칙은 recruitment 기능의 Service가 맡습니다.
 */
@Service
class AdminRecruitmentPostService(
    private val recruitmentPostService: RecruitmentPostService,
) {

    fun listPosts(actor: Account, status: RecruitmentPostStatus?, page: Int, size: Int): RecruitmentPostPageResult {
        requireAdmin(actor)
        return recruitmentPostService.listForAdmin(status, page, size)
    }

    fun hide(actor: Account, postId: Long, reason: String): RecruitmentPostResult {
        requireAdmin(actor)
        val result = recruitmentPostService.hide(postId, reason)
        log.info("admin action: hide recruitment post actorId={} postId={} reason={}", actor.id, postId, reason)
        return result
    }

    fun unhide(actor: Account, postId: Long): RecruitmentPostResult {
        requireAdmin(actor)
        val result = recruitmentPostService.unhide(postId)
        log.info("admin action: unhide recruitment post actorId={} postId={}", actor.id, postId)
        return result
    }

    /** 사유는 DB에 남기지 않고 로그에만 남깁니다(모집글에는 숨김 사유 컬럼만 있음). */
    fun close(actor: Account, postId: Long, reason: String): RecruitmentPostResult {
        requireAdmin(actor)
        val result = recruitmentPostService.closeByAdmin(postId)
        log.info("admin action: close recruitment post actorId={} postId={} reason={}", actor.id, postId, reason)
        return result
    }

    private fun requireAdmin(actor: Account) {
        if (!actor.isAdmin) throw AdminRequiredException()
    }

    private companion object {
        val log = LoggerFactory.getLogger(AdminRecruitmentPostService::class.java)
    }
}
