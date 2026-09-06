package ai.govbiz.core.account.admin

import ai.govbiz.core.account.domain.Account
import ai.govbiz.core.account.domain.AccountPage
import ai.govbiz.core.account.repository.AccountRepository
import ai.govbiz.core.account.service.exception.AccountNotFoundException
import ai.govbiz.core.account.service.exception.AdminRequiredException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** 운영자의 회원·기업 조회와 세션 강제 종료입니다. 모든 조치는 관리자 역할을 확인한 뒤 서버 로그에 남깁니다. */
@Service
class AdminAccountService(
    private val repository: AccountRepository,
) {

    fun listAccounts(actor: Account, emailKeyword: String?, page: Int, size: Int): AccountPage {
        requireAdmin(actor)
        return repository.findPage(emailKeyword, page, size)
    }

    /** 대상 계정의 세션을 모두 삭제해 즉시 로그아웃시킵니다. 계정이 없으면 404입니다. */
    fun revokeSessions(actor: Account, accountId: Long) {
        requireAdmin(actor)
        val target = repository.findById(accountId) ?: throw AccountNotFoundException()
        val revoked = repository.deleteSessionsByAccountId(target.id)
        log.info("admin action: revoke sessions actorId={} targetId={} revoked={}", actor.id, target.id, revoked)
    }

    private fun requireAdmin(actor: Account) {
        if (!actor.isAdmin) throw AdminRequiredException()
    }

    private companion object {
        val log = LoggerFactory.getLogger(AdminAccountService::class.java)
    }
}
