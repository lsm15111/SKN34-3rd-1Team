package ai.govbiz.core.account.admin

import ai.govbiz.core.account.admin.dto.AdminRecruitmentPostPageResponse
import ai.govbiz.core.account.admin.dto.AdminRecruitmentPostResponse
import ai.govbiz.core.account.admin.dto.RecruitmentPostActionRequest
import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.recruitment.domain.RecruitmentPostStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 운영자 전용 모집글 목록·숨김·해제·강제 마감입니다. 관리자 여부는 Service가 확인합니다. */
@RestController
@RequestMapping("/api/v1/admin/recruitment-posts")
class AdminRecruitmentPostController(
    private val service: AdminRecruitmentPostService,
) {

    @GetMapping
    fun list(
        actor: Account,
        @RequestParam(required = false) status: RecruitmentPostStatus?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): AdminRecruitmentPostPageResponse =
        AdminRecruitmentPostPageResponse.from(service.listPosts(actor, status, page, size))

    @PostMapping("/{postId}/hide")
    fun hide(
        actor: Account,
        @PathVariable @Min(1) postId: Long,
        @RequestBody @Valid request: RecruitmentPostActionRequest,
    ): AdminRecruitmentPostResponse =
        AdminRecruitmentPostResponse.from(service.hide(actor, postId, request.reason))

    @PostMapping("/{postId}/unhide")
    fun unhide(
        actor: Account,
        @PathVariable @Min(1) postId: Long,
    ): AdminRecruitmentPostResponse =
        AdminRecruitmentPostResponse.from(service.unhide(actor, postId))

    @PostMapping("/{postId}/close")
    fun close(
        actor: Account,
        @PathVariable @Min(1) postId: Long,
        @RequestBody @Valid request: RecruitmentPostActionRequest,
    ): AdminRecruitmentPostResponse =
        AdminRecruitmentPostResponse.from(service.close(actor, postId, request.reason))
}
