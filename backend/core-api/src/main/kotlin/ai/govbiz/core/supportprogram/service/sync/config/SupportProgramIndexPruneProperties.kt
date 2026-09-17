package ai.govbiz.core.supportprogram.service.sync.config

import ai.govbiz.core._common.helper.validatePositiveDuration
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.support-program-index.prune")
data class SupportProgramIndexPruneProperties(
    val enabled: Boolean = false,
    val initialDelay: Duration = Duration.ofMinutes(30),
    val fixedDelay: Duration = Duration.ofHours(6),
    /** 공개 직후 이전 버전으로 검색 중인 요청이 끝나도록, 마지막 공개 뒤 이 시간이 지나야 정리합니다. */
    val quietPeriod: Duration = Duration.ofMinutes(30),
) {
    init {
        require(!initialDelay.isNegative) { "app.support-program-index.prune.initial-delay must not be negative" }
        validatePositiveDuration(fixedDelay, "app.support-program-index.prune.fixed-delay")
        validatePositiveDuration(quietPeriod, "app.support-program-index.prune.quiet-period")
    }
}
