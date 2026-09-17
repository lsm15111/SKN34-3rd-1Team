package ai.govbiz.core.supportprogram.service.sync

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** 색인 복구와 같은 단일 스레드에서 실행해, 한 인스턴스 안에서는 복구와 정리가 겹치지 않습니다. */
@Component
@ConditionalOnProperty(prefix = "app.support-program-index.prune", name = ["enabled"], havingValue = "true", matchIfMissing = false)
class SupportProgramIndexPruneScheduler(private val pruneService: SupportProgramIndexPruneService) {
    @Scheduled(
        initialDelayString = "\${app.support-program-index.prune.initial-delay:PT30M}",
        fixedDelayString = "\${app.support-program-index.prune.fixed-delay:PT6H}",
        scheduler = "supportProgramIndexTaskScheduler",
    )
    fun prune() {
        try {
            val summary = pruneService.prune().joinToString(", ") { result ->
                "${result.sourceCode} ${result.skipReason?.let { "건너뜀($it)" } ?: "키워드 색인 ${result.lexicalDeletedCount}건 삭제"}"
            }
            logger.info("지원사업 색인 이전 버전 정리: {}", summary.ifEmpty { "대상 제공처 없음" })
        } catch (exception: RuntimeException) {
            logger.error("지원사업 색인 이전 버전 정리 실패: {}. 다음 실행에서 다시 시도합니다.", exception.javaClass.simpleName)
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(SupportProgramIndexPruneScheduler::class.java)
    }
}
