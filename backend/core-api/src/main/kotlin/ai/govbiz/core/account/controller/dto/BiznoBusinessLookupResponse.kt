package ai.govbiz.core.account.controller.dto

import ai.govbiz.core.account.client.bizno.dto.BiznoBusiness

data class BiznoBusinessLookupResponse(
    val businesses: List<BiznoBusinessResponse>,
) {
    companion object {
        fun from(businesses: List<BiznoBusiness>): BiznoBusinessLookupResponse =
            BiznoBusinessLookupResponse(
                businesses = java.util.List.copyOf(businesses.map(BiznoBusinessResponse::from)),
            )
    }
}

data class BiznoBusinessResponse(
    val businessNumber: String,
    val companyName: String,
    val businessStatus: String,
) {
    companion object {
        fun from(business: BiznoBusiness): BiznoBusinessResponse =
            BiznoBusinessResponse(
                businessNumber = business.businessNumber,
                companyName = business.companyName,
                businessStatus = business.businessStatus,
            )
    }
}
