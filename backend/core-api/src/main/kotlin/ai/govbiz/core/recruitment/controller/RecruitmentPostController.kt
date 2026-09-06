package ai.govbiz.core.recruitment.controller

import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.recruitment.controller.dto.CreateRecruitmentPostRequest
import ai.govbiz.core.recruitment.controller.dto.RecruitmentPostListResponse
import ai.govbiz.core.recruitment.controller.dto.RecruitmentPostPageResponse
import ai.govbiz.core.recruitment.controller.dto.RecruitmentPostResponse
import ai.govbiz.core.recruitment.controller.dto.UpdateRecruitmentPostRequest
import ai.govbiz.core.recruitment.service.RecruitmentPostService
import ai.govbiz.core.supportprogram.controller.validation.CodePointMax
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 파트너 모집글 공개 API입니다. 목록·상세는 로그인 없이도 볼 수 있고([Account]가 nullable이면 Resolver가 헤더 없는
 * 요청에 null을 넣음), 등록·수정·마감·내 글은 로그인이 필요합니다.
 */
@RestController
@RequestMapping("/api/v1/recruitment-posts")
class RecruitmentPostController(
    private val service: RecruitmentPostService,
) {

    @GetMapping
    fun list(
        viewer: Account?,
        @RequestParam(required = false)
        @Size(max = 64)
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
        sourceCode: String?,
        @RequestParam(required = false)
        @CodePointMax(max = 255)
        @Pattern(regexp = "(?Us)^(?!\\s)(?!.*\\s$)(?!.*\\p{C}).+$")
        sourceProgramId: String?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(50) size: Int,
    ): RecruitmentPostPageResponse =
        RecruitmentPostPageResponse.from(service.listOpen(sourceCode, sourceProgramId, page, size, viewer))

    @GetMapping("/mine")
    fun listMine(author: Account): RecruitmentPostListResponse =
        RecruitmentPostListResponse.from(service.listMine(author))

    @GetMapping("/{postId}")
    fun get(
        viewer: Account?,
        @PathVariable @Min(1) postId: Long,
    ): RecruitmentPostResponse =
        RecruitmentPostResponse.from(service.get(postId, viewer))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        author: Account,
        @RequestBody @Valid request: CreateRecruitmentPostRequest,
    ): RecruitmentPostResponse =
        RecruitmentPostResponse.from(
            service.create(author, request.sourceCode, request.sourceProgramId, request.post.toDraft()),
        )

    @PutMapping("/{postId}")
    fun update(
        author: Account,
        @PathVariable @Min(1) postId: Long,
        @RequestBody @Valid request: UpdateRecruitmentPostRequest,
    ): RecruitmentPostResponse =
        RecruitmentPostResponse.from(service.update(author, postId, request.post.toDraft()))

    @PostMapping("/{postId}/close")
    fun closeEarly(
        author: Account,
        @PathVariable @Min(1) postId: Long,
    ): RecruitmentPostResponse =
        RecruitmentPostResponse.from(service.closeEarly(author, postId))
}
