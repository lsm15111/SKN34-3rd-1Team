package ai.govbiz.core.account.client.bizno.dto

/**
 * Bizno가 국세청 등록 사업자로 확인한 기업 한 건입니다.
 *
 * `businessNumber`는 하이픈을 제거한 숫자 10자리이며, `businessStatus`는 Bizno의 `bstt`
 * 원문(계속사업자·휴업자·폐업자)입니다.
 */
data class BiznoBusiness(
    val businessNumber: String,
    val companyName: String,
    val businessStatus: String,
)
