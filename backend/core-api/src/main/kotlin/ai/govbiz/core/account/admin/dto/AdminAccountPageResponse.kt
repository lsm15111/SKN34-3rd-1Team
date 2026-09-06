package ai.govbiz.core.account.admin.dto

import ai.govbiz.core.account.controller.dto.CompanyResponse
import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.account.domain.AccountPage
import ai.govbiz.core.account.domain.AccountRole
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class AdminAccountPageResponse(
    val items: List<AdminAccountResponse>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
) {
    companion object {
        fun from(accountPage: AccountPage): AdminAccountPageResponse =
            AdminAccountPageResponse(
                items = java.util.List.copyOf(accountPage.accounts.map(AdminAccountResponse::from)),
                page = accountPage.page,
                size = accountPage.size,
                totalCount = accountPage.totalCount,
            )
    }
}

data class AdminAccountResponse(
    val id: Long,
    val email: String,
    val role: AccountRole,
    val company: CompanyResponse,
    val createdAt: String,
) {
    companion object {
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

        fun from(account: Account): AdminAccountResponse =
            AdminAccountResponse(
                id = account.id,
                email = account.email,
                role = account.role,
                company = CompanyResponse.from(account.company),
                createdAt = account.createdAt.atZone(SEOUL).toOffsetDateTime()
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            )
    }
}
