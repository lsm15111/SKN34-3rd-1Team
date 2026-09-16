package ai.govbiz.core.supportprogram.service.sync.config

import ai.govbiz.core._common.helper.validatePositiveDuration
import java.time.Duration
import java.time.Period
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.msit.sync")
data class MsitSupportProgramCatalogSyncProperties(
    val enabled: Boolean = false,
    val initialDelay: Duration = Duration.ofSeconds(15),
    val fixedDelay: Duration = Duration.ofHours(24),
    /** 게시일이 이 기간 안인 공고만 수집합니다. 목록 API는 13년치를 게시일 최신순으로 제공합니다. */
    val lookback: Period = Period.ofMonths(12),
) {
    init {
        require(!initialDelay.isNegative) { "app.msit.sync.initial-delay must not be negative" }
        validatePositiveDuration(fixedDelay, "app.msit.sync.fixed-delay")
        require(!lookback.isNegative && !lookback.isZero) { "app.msit.sync.lookback must be positive" }
        require(lookback.toTotalMonths() + lookback.days / 31 <= MAX_LOOKBACK_MONTHS) {
            "app.msit.sync.lookback must not exceed $MAX_LOOKBACK_MONTHS months"
        }
    }

    private companion object {
        const val MAX_LOOKBACK_MONTHS = 240L
    }
}
