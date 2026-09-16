package ai.govbiz.core.supportprogram.facade

import ai.govbiz.core.supportprogram.client.msit.MsitClient
import ai.govbiz.core.supportprogram.client.msit.exception.MsitClientException
import ai.govbiz.core.supportprogram.client.msit.helper.MsitAnnouncementKindHelper
import ai.govbiz.core.supportprogram.client.msit.mapper.MsitProgramMapper
import ai.govbiz.core.supportprogram.domain.CatalogSupportProgram
import ai.govbiz.core.supportprogram.facade.exception.SupportProgramCatalogFacadeException
import ai.govbiz.core.supportprogram.service.sync.config.MsitSupportProgramCatalogSyncProperties
import java.time.Clock
import java.time.LocalDate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component("msitSupportProgramCatalogFacade")
class MsitSupportProgramCatalogFacade(
    private val client: MsitClient,
    private val properties: MsitSupportProgramCatalogSyncProperties,
    @param:Qualifier("seoulClock") private val clock: Clock,
) : SupportProgramCatalogFacade {
    override fun load(): List<CatalogSupportProgram> =
        try {
            val publishedSince = LocalDate.now(clock).minus(properties.lookback)
            // 결과 발표·입찰·채용·취소 게시물도 원본 검증은 거친 뒤 공개 대상에서만 제외합니다.
            val mapped = MsitProgramMapper.mapValidated(client.fetchPublishedSince(publishedSince))
            val kinds = mapped.groupBy { MsitAnnouncementKindHelper.classify(it.program.title) }
            val programs = kinds[MsitAnnouncementKindHelper.Kind.SUPPORT_PROGRAM].orEmpty()
            logger.info(
                "MSIT {} 이후 게시 {}건 중 지원사업 {}건 공개, 제외: {}", publishedSince, mapped.size, programs.size,
                kinds.filterKeys { it != MsitAnnouncementKindHelper.Kind.SUPPORT_PROGRAM }.mapValues { it.value.size },
            )
            java.util.List.copyOf(programs)
        } catch (exception: MsitClientException) {
            throw SupportProgramCatalogFacadeException.fromClient(
                failure = when (exception.failure) {
                    MsitClientException.Failure.NOT_CONFIGURED -> SupportProgramCatalogFacadeException.Failure.NOT_CONFIGURED
                    MsitClientException.Failure.UPSTREAM_ERROR -> SupportProgramCatalogFacadeException.Failure.UPSTREAM_ERROR
                    MsitClientException.Failure.INVALID_RESPONSE -> SupportProgramCatalogFacadeException.Failure.INVALID_RESPONSE
                    MsitClientException.Failure.UNAVAILABLE -> SupportProgramCatalogFacadeException.Failure.UNAVAILABLE
                    MsitClientException.Failure.TIMEOUT -> SupportProgramCatalogFacadeException.Failure.TIMEOUT
                },
                message = exception.message, cause = exception,
            )
        }

    private companion object {
        val logger = LoggerFactory.getLogger(MsitSupportProgramCatalogFacade::class.java)
    }
}
