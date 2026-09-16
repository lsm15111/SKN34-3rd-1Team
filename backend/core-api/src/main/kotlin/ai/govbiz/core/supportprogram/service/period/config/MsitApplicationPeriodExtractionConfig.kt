package ai.govbiz.core.supportprogram.service.period.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(MsitApplicationPeriodExtractionProperties::class)
class MsitApplicationPeriodExtractionConfig {
    /** 첨부 다운로드가 공고 동기화·양식 분석 스케줄러 스레드를 점유하지 않게 합니다. */
    @Bean
    @ConditionalOnProperty(prefix = "app.msit.period-extraction", name = ["enabled"], havingValue = "true", matchIfMissing = false)
    fun msitPeriodExtractionTaskScheduler() = ThreadPoolTaskScheduler().apply {
        poolSize = 1
        setThreadNamePrefix("msit-period-extraction-")
    }
}
