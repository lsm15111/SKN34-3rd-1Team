package ai.govbiz.core.supportprogram.service.sync

import ai.govbiz.core._common.exception.AiServiceCallException
import ai.govbiz.core.supportprogram.client.ai.AiSupportProgramIndexClient
import ai.govbiz.core.supportprogram.client.ai.dto.AiSupportProgramIndexPruneRequest
import ai.govbiz.core.supportprogram.client.ai.mapper.SupportProgramIndexDocumentMapper
import ai.govbiz.core.supportprogram.client.elasticsearch.ElasticsearchSupportProgramClient
import ai.govbiz.core.supportprogram.client.elasticsearch.mapper.ElasticsearchSupportProgramDocumentMapper
import ai.govbiz.core.supportprogram.domain.CatalogSupportProgram
import ai.govbiz.core.supportprogram.domain.SupportProgramSyncStatus
import ai.govbiz.core.supportprogram.helper.SupportProgramCatalogFingerprintHelper
import ai.govbiz.core.supportprogram.repository.SupportProgramRepository
import ai.govbiz.core.supportprogram.service.sync.config.SupportProgramIndexPruneProperties
import java.time.Clock
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

/** 제공처별 색인 정리 결과입니다. 건너뛰었으면 [skipReason]이 있고 삭제 건수는 null입니다. */
data class SupportProgramIndexPruneResult(
    val sourceCode: String,
    val lexicalDeletedCount: Int?,
    val skipReason: SupportProgramIndexPruneSkipReason?,
)

enum class SupportProgramIndexPruneSkipReason {
    /** 공개 스냅샷이 없거나 legacy 채택 전입니다. */
    NOT_PUBLISHED,
    /** 현재 공개 버전의 색인이 모두 준비됐는지 확인되지 않았습니다. */
    INDEX_NOT_READY,
    /** 더 최근에 시작한 동기화가 새 버전을 준비 중이거나 실패해 공개되지 않았습니다. */
    SYNC_NOT_SETTLED,
    /** 공개 직후라 이전 버전으로 시작한 검색이 아직 진행 중일 수 있습니다. */
    RECENTLY_PUBLISHED,
    /** 읽는 사이 공개 스냅샷이 바뀌었습니다. */
    SNAPSHOT_CHANGED,
}

/**
 * 제공처가 조용할 때만 현재 공개 버전을 제외한 이전 키워드·벡터 색인 버전을 삭제합니다.
 *
 * 공개 준비 중인 새 버전은 아직 DB에 없으므로, 가장 최근에 시작한 동기화 세대가 공개 세대와 같고
 * 마지막 공개 뒤 [SupportProgramIndexPruneProperties.quietPeriod]가 지났을 때만 삭제합니다.
 * 동기화는 외부 API 수집을 끝낸 뒤에야 색인을 준비하므로, 정리 도중 시작한 동기화의 새 버전과 겹치지 않습니다.
 * 드물게 겹쳐 현재 버전이 빠져도 색인 복구 작업이 다음 주기에 다시 채웁니다.
 */
@Service
class SupportProgramIndexPruneService(
    private val repository: SupportProgramRepository,
    private val client: AiSupportProgramIndexClient,
    private val lexicalClient: ElasticsearchSupportProgramClient,
    private val properties: SupportProgramIndexPruneProperties,
    @param:Qualifier("seoulClock") private val clock: Clock,
) {
    fun prune(): List<SupportProgramIndexPruneResult> {
        val statuses = repository.findSyncStatuses()
        val programsBySource = repository.findPresent().groupBy { it.program.sourceCode }
        val results = mutableListOf<SupportProgramIndexPruneResult>()
        var firstFailure: RuntimeException? = null
        for (status in statuses) {
            try {
                results += pruneSource(status, programsBySource[status.sourceCode].orEmpty())
            } catch (exception: RuntimeException) {
                // 한 제공처의 실패가 다른 제공처 정리를 막지 않게 하고, 끝난 뒤 첫 실패를 전달합니다.
                val previous = firstFailure
                if (previous == null) firstFailure = exception else previous.addSuppressed(exception)
            }
        }
        firstFailure?.let { throw it }
        return results
    }

    private fun pruneSource(status: SupportProgramSyncStatus, programs: List<CatalogSupportProgram>): SupportProgramIndexPruneResult {
        val sourceCode = status.sourceCode
        skipReason(status, programs)?.let { return SupportProgramIndexPruneResult(sourceCode, null, it) }
        val publishedGeneration = requireNotNull(status.publishedGeneration)

        // AI Service는 현재 버전 벡터가 모두 있을 때만 삭제하므로 먼저 호출해 키워드 색인 삭제의 관문으로 씁니다.
        val documents = programs.map(SupportProgramIndexDocumentMapper::fromCatalog)
        val retained = client.prune(AiSupportProgramIndexPruneRequest(sourceCode, documents.map { it.reference() })).retainedCount
        if (retained != documents.size) {
            throw AiServiceCallException.invalidResponse("AI Service did not retain every current index document", null)
        }
        val deleted = lexicalClient.pruneSource(
            sourceCode,
            programs.map { ElasticsearchSupportProgramDocumentMapper.fromCatalog(it).reference() },
        )
        if (repository.findLatestStartedGeneration(sourceCode) != publishedGeneration) {
            logger.warn("{} 색인 정리 중 새 동기화가 시작됐습니다. 빠진 버전은 색인 복구 작업이 다시 채웁니다.", sourceCode)
        }
        return SupportProgramIndexPruneResult(sourceCode, deleted, null)
    }

    private fun skipReason(status: SupportProgramSyncStatus, programs: List<CatalogSupportProgram>): SupportProgramIndexPruneSkipReason? {
        val publishedGeneration = status.publishedGeneration
        val fingerprint = status.publishedCatalogFingerprint
        val publishedAt = status.lastSuccessfulSyncAt
        return when {
            publishedGeneration == null || fingerprint == null || publishedAt == null -> SupportProgramIndexPruneSkipReason.NOT_PUBLISHED
            !status.indexReady -> SupportProgramIndexPruneSkipReason.INDEX_NOT_READY
            repository.findLatestStartedGeneration(status.sourceCode) != publishedGeneration -> SupportProgramIndexPruneSkipReason.SYNC_NOT_SETTLED
            publishedAt.isAfter(LocalDateTime.now(clock).minus(properties.quietPeriod)) -> SupportProgramIndexPruneSkipReason.RECENTLY_PUBLISHED
            programs.size != status.publishedProgramCount ||
                SupportProgramCatalogFingerprintHelper.calculate(programs) != fingerprint -> SupportProgramIndexPruneSkipReason.SNAPSHOT_CHANGED
            else -> null
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(SupportProgramIndexPruneService::class.java)
    }
}
