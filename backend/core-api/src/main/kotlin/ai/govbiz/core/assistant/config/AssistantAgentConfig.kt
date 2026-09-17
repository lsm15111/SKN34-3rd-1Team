package ai.govbiz.core.assistant.config

import ai.govbiz.core.assistant.service.AssistantHelpCatalog
import ai.govbiz.core.assistant.service.AssistantToolTokenService
import ai.govbiz.core.supportprogram.service.admission.SupportProgramRequestAdmissionService
import ai.govbiz.core.supportprogram.service.admission.config.SupportProgramRequestAdmissionProperties
import java.time.Clock
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AssistantAgentProperties::class)
class AssistantAgentConfig {
    @Bean
    fun assistantToolTokenService(
        properties: AssistantAgentProperties,
        @Qualifier("seoulClock") clock: Clock,
    ) = AssistantToolTokenService(properties, clock)

    /** 가이드 답변의 근거인 도움말 카탈로그입니다. 형식이 틀리면 앱이 뜨지 않습니다. */
    @Bean
    fun assistantHelpCatalog(mapper: ObjectMapper) = AssistantHelpCatalog.load(mapper)

    /** 로그인 회원 질문만의 주소당 분당 상한입니다. 전체 분당·동시 실행 한도는 공유 Bean이 이미 걸므로 여기서는 사실상 걸지 않습니다. */
    @Bean
    fun assistantAgentAdmissionService(properties: AssistantAgentProperties) = SupportProgramRequestAdmissionService(
        SupportProgramRequestAdmissionProperties(
            perClientPerMinute = properties.agentPerClientPerMinute, globalPerMinute = 10_000, maxConcurrent = 100,
        ),
    )
}
