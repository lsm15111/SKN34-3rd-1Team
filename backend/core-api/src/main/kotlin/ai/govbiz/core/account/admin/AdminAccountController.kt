package ai.govbiz.core.account.admin

import ai.govbiz.core.account.admin.dto.AdminAccountPageResponse
import ai.govbiz.core.account.domain.Account
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/accounts")
class AdminAccountController(
    private val service: AdminAccountService,
) {

    @GetMapping
    fun list(
        actor: Account,
        @RequestParam(required = false) @Size(max = 320) email: String?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): AdminAccountPageResponse =
        AdminAccountPageResponse.from(service.listAccounts(actor, email, page, size))

    @PostMapping("/{accountId}/sessions/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeSessions(
        actor: Account,
        @PathVariable @Min(1) accountId: Long,
    ) {
        service.revokeSessions(actor, accountId)
    }
}
