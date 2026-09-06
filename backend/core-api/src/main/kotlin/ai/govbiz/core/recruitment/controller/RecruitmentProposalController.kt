package ai.govbiz.core.recruitment.controller

import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.recruitment.controller.dto.RecruitmentProposalListResponse
import ai.govbiz.core.recruitment.controller.dto.RecruitmentProposalResponse
import ai.govbiz.core.recruitment.controller.dto.SendProposalRequest
import ai.govbiz.core.recruitment.service.RecruitmentProposalService
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 참여 제안 공개 API입니다. 모두 로그인이 필요하며, 모집글 아래 경로는 보내기·받은 제안, 제안 경로는 결정·보낸 제안입니다.
 */
@RestController
class RecruitmentProposalController(
    private val service: RecruitmentProposalService,
) {

    @PostMapping("/api/v1/recruitment-posts/{postId}/proposals")
    @ResponseStatus(HttpStatus.CREATED)
    fun send(
        proposer: Account,
        @PathVariable @Min(1) postId: Long,
        @RequestBody @Valid request: SendProposalRequest,
    ): RecruitmentProposalResponse =
        RecruitmentProposalResponse.from(service.send(proposer, postId, request.message))

    @GetMapping("/api/v1/recruitment-posts/{postId}/proposals")
    fun listReceived(
        owner: Account,
        @PathVariable @Min(1) postId: Long,
    ): RecruitmentProposalListResponse =
        RecruitmentProposalListResponse.from(service.listReceived(owner, postId))

    @GetMapping("/api/v1/recruitment-proposals/sent")
    fun listSent(proposer: Account): RecruitmentProposalListResponse =
        RecruitmentProposalListResponse.from(service.listSent(proposer))

    @PostMapping("/api/v1/recruitment-proposals/{proposalId}/accept")
    fun accept(
        owner: Account,
        @PathVariable @Min(1) proposalId: Long,
    ): RecruitmentProposalResponse =
        RecruitmentProposalResponse.from(service.accept(owner, proposalId))

    @PostMapping("/api/v1/recruitment-proposals/{proposalId}/decline")
    fun decline(
        owner: Account,
        @PathVariable @Min(1) proposalId: Long,
    ): RecruitmentProposalResponse =
        RecruitmentProposalResponse.from(service.decline(owner, proposalId))

    @PostMapping("/api/v1/recruitment-proposals/{proposalId}/withdraw")
    fun withdraw(
        proposer: Account,
        @PathVariable @Min(1) proposalId: Long,
    ): RecruitmentProposalResponse =
        RecruitmentProposalResponse.from(service.withdraw(proposer, proposalId))
}
