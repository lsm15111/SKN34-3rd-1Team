package ai.govbiz.core.assistant.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * GovBiz 가이드 설정입니다. 회원 자료 도구 API는 AI Service만 부르는 내부 읽기 경로라 공유 비밀과
 * 계정을 묶은 단기 토큰 둘 다 있어야 열립니다. 비밀이 비어 있으면 도구 API 전체가 닫히고 가이드는 의도 안내만 합니다.
 */
@ConfigurationProperties(prefix = "app.assistant")
data class AssistantAgentProperties(
    /** Core와 AI Service가 공유하는 비밀입니다. 32자 미만이면 도구 API를 닫습니다. */
    val toolsSecret: String = "",
    /** 요청마다 발급하는 계정 묶음 토큰의 유효 시간입니다. */
    val toolTokenTtl: Duration = Duration.ofMinutes(5),
    /** 로그인 회원 질문만의 추가 한도입니다. 도구를 부르면 모델을 여러 번 호출하므로 기존 한도 위에 얹습니다. */
    val agentPerClientPerMinute: Int = 3,
    /** 관심 공고를 담을 때 원문을 미리 수집·색인하는 큐(RabbitMQ)를 켭니다. */
    val prefetchQueueEnabled: Boolean = false,
) {
    init {
        require(!toolTokenTtl.isNegative && !toolTokenTtl.isZero && toolTokenTtl <= Duration.ofHours(1)) {
            "app.assistant.tool-token-ttl must be between 1 second and 1 hour"
        }
        require(agentPerClientPerMinute in 1..1_000) { "app.assistant.agent-per-client-per-minute must be between 1 and 1000" }
    }

    val toolsEnabled: Boolean
        get() = toolsSecret.length >= MIN_SECRET_LENGTH

    companion object {
        const val MIN_SECRET_LENGTH = 32
    }
}
