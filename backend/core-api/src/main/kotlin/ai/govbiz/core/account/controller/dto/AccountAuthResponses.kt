package ai.govbiz.core.account.controller.dto

import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.account.domain.Company
import ai.govbiz.core.account.service.dto.AccountSessionResult
import java.time.format.DateTimeFormatter

data class AuthSessionResponse(
    val sessionToken: String,
    val expiresAt: String,
    val account: AccountResponse,
) {
    companion object {
        fun from(result: AccountSessionResult): AuthSessionResponse =
            AuthSessionResponse(
                sessionToken = result.sessionToken,
                expiresAt = result.expiresAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                account = AccountResponse.from(result.account),
            )
    }
}

data class CurrentAccountResponse(
    val account: AccountResponse,
) {
    companion object {
        fun from(account: Account): CurrentAccountResponse =
            CurrentAccountResponse(AccountResponse.from(account))
    }
}

data class AccountResponse(
    val email: String,
    val company: CompanyResponse,
) {
    companion object {
        fun from(account: Account): AccountResponse =
            AccountResponse(
                email = account.email,
                company = CompanyResponse.from(account.company),
            )
    }
}

data class CompanyResponse(
    val businessNumber: String,
    val companyName: String,
    val businessStatus: String,
) {
    companion object {
        fun from(company: Company): CompanyResponse =
            CompanyResponse(
                businessNumber = company.businessNumber,
                companyName = company.companyName,
                businessStatus = company.businessStatus,
            )
    }
}
