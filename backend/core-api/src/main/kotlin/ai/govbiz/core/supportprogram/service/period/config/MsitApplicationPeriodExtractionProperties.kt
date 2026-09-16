package ai.govbiz.core.supportprogram.service.period.config

import ai.govbiz.core._common.helper.validatePositiveDuration
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.msit.period-extraction")
data class MsitApplicationPeriodExtractionProperties(
    val enabled: Boolean = false,
    /** 한 번에 처리할 공고 수입니다. 공고마다 상세 페이지와 첨부를 순서대로 내려받으므로 작게 유지합니다. */
    val batchSize: Int = 5,
    val delay: Duration = Duration.ofSeconds(30),
    val retryAfter: Duration = Duration.ofHours(1),
    val recheckAfter: Duration = Duration.ofDays(30),
    val extractedRecheckAfter: Duration = Duration.ofDays(180),
) {
    init {
        require(batchSize in 1..50) { "app.msit.period-extraction.batch-size must be between 1 and 50" }
        validatePositiveDuration(delay, "app.msit.period-extraction.delay")
        validatePositiveDuration(retryAfter, "app.msit.period-extraction.retry-after")
        validatePositiveDuration(recheckAfter, "app.msit.period-extraction.recheck-after")
        validatePositiveDuration(extractedRecheckAfter, "app.msit.period-extraction.extracted-recheck-after")
    }
}
