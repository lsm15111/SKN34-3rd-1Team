package ai.govbiz.core.supportprogram.service.period

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "app.msit.period-extraction", name = ["enabled"], havingValue = "true", matchIfMissing = false)
class MsitApplicationPeriodExtractionWorker(private val service: MsitApplicationPeriodExtractionService) {
    @Scheduled(
        initialDelayString = "\${app.msit.period-extraction.delay:30s}",
        fixedDelayString = "\${app.msit.period-extraction.delay:30s}",
        scheduler = "msitPeriodExtractionTaskScheduler",
    )
    fun run() {
        try {
            service.runBatch()
        } catch (exception: RuntimeException) {
            // DB 장애 등 예상하지 못한 오류가 스케줄을 멈추지 않게 하고, 다음 주기에 다시 시도합니다.
            logger.error("MSIT 신청 기간 추출에 실패했습니다. 오류 유형: {}", exception.javaClass.simpleName)
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(MsitApplicationPeriodExtractionWorker::class.java)
    }
}
