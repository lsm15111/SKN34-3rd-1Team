package ai.govbiz.core.account.controller.dto

/** 설정된 소셜 로그인 제공처 키 목록입니다(`google`·`kakao`). 비어 있으면 화면은 버튼을 그리지 않습니다. */
data class OAuthProvidersResponse(
    val providers: List<String>,
)
