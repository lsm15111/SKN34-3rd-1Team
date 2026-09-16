package ai.govbiz.core.supportprogram.service.period

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.springframework.stereotype.Component

/**
 * MSIT 스냅샷 공개와 신청 기간 추출 결과 저장이 같은 support_program 행을 동시에 갱신하지 않게 합니다.
 *
 * 두 작업이 겹치면 InnoDB 교착으로 한쪽이 롤백되는데, 공개 쪽이 지면 다음 동기화 주기까지 새 스냅샷이 늦어집니다.
 * 네트워크·파싱은 잠금 밖에서 하고 짧은 DB 쓰기만 잠급니다. 한 Core 프로세스 안의 직렬화이며 여러 인스턴스는 보장하지 않습니다.
 */
@Component
class MsitCatalogWriteGuard {
    private val lock = ReentrantLock(true)

    fun <T> withLock(block: () -> T): T = lock.withLock(block)
}
