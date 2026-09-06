package ai.govbiz.core.account.service

import ai.govbiz.core.account.client.bizno.BiznoClient
import ai.govbiz.core.account.client.bizno.dto.BiznoBusiness
import org.springframework.stereotype.Service

/** 사용자가 입력한 사업자등록번호를 정규화해 Bizno에서 등록 기업을 확인합니다. */
@Service
class BiznoBusinessService(private val biznoClient: BiznoClient) {

    /** 하이픈 등 구분 문자를 제거한 뒤 조회합니다. 숫자 10자리 검증은 공개 Controller가 맡습니다. */
    fun findByBusinessNumber(businessNumber: String): List<BiznoBusiness> =
        biznoClient.findByBusinessNumber(normalize(businessNumber))

    companion object {
        /** 공개 API·저장소가 같은 표기를 쓰도록 사업자등록번호에서 숫자만 남깁니다. */
        fun normalize(businessNumber: String): String = businessNumber.filter(Char::isDigit)
    }
}
