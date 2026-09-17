package ai.govbiz.core.supportprogram.service.sync

import ai.govbiz.core._common.exception.AiServiceCallException
import ai.govbiz.core.supportprogram.client.ai.AiSupportProgramIndexClient
import ai.govbiz.core.supportprogram.client.ai.dto.AiSupportProgramIndexPrunePayload
import ai.govbiz.core.supportprogram.client.ai.dto.AiSupportProgramIndexPruneRequest
import ai.govbiz.core.supportprogram.client.ai.mapper.SupportProgramIndexDocumentMapper
import ai.govbiz.core.supportprogram.client.elasticsearch.ElasticsearchSupportProgramClient
import ai.govbiz.core.supportprogram.client.elasticsearch.exception.ElasticsearchClientException
import ai.govbiz.core.supportprogram.client.elasticsearch.mapper.ElasticsearchSupportProgramDocumentMapper
import ai.govbiz.core.supportprogram.domain.CatalogSupportProgram
import ai.govbiz.core.supportprogram.domain.SupportProgramSyncOutcome
import ai.govbiz.core.supportprogram.domain.SupportProgramSyncStatus
import ai.govbiz.core.supportprogram.helper.SupportProgramCatalogFingerprintHelper
import ai.govbiz.core.supportprogram.helper.SupportProgramTestHelper.catalogProgram
import ai.govbiz.core.supportprogram.repository.SupportProgramRepository
import ai.govbiz.core.supportprogram.service.sync.config.SupportProgramIndexPruneProperties
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupportProgramIndexPruneServiceTest {
    @Mock private lateinit var repository: SupportProgramRepository
    @Mock private lateinit var client: AiSupportProgramIndexClient
    @Mock private lateinit var lexicalClient: ElasticsearchSupportProgramClient

    private val now = LocalDateTime.parse("2026-09-17T21:00:00")
    private val clock = Clock.fixed(Instant.parse("2026-09-17T12:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val bizInfo = listOf(catalogProgram("one"), catalogProgram("two"))
    private val msit = listOf(msitProgram("3186880"))

    @Test
    fun deletesOnlyPreviousVersionsOfSettledSourcesVectorsFirst() {
        givenCatalog(status("BIZINFO", bizInfo) to 7L, status("MSIT", msit) to 3L)
        givenPrune("BIZINFO", bizInfo, deleted = 812)
        givenPrune("MSIT", msit, deleted = 40)

        val results = service().prune()

        assertEquals(
            listOf(SupportProgramIndexPruneResult("BIZINFO", 812, null), SupportProgramIndexPruneResult("MSIT", 40, null)),
            results,
        )
        val order = inOrder(client, lexicalClient)
        order.verify(client).prune(pruneRequest("BIZINFO", bizInfo))
        order.verify(lexicalClient).pruneSource("BIZINFO", lexicalReferences(bizInfo))
        order.verify(client).prune(pruneRequest("MSIT", msit))
        order.verify(lexicalClient).pruneSource("MSIT", lexicalReferences(msit))
    }

    @Test
    fun skipsSourcesThatCouldStillReferenceOrPrepareOtherVersions() {
        val settled = status("BIZINFO", bizInfo)
        val cases = listOf(
            Triple(settled.copy(publishedGeneration = null), 7L, SupportProgramIndexPruneSkipReason.NOT_PUBLISHED),
            Triple(settled.copy(publishedCatalogFingerprint = null), 7L, SupportProgramIndexPruneSkipReason.NOT_PUBLISHED),
            Triple(settled.copy(lastSuccessfulSyncAt = null), 7L, SupportProgramIndexPruneSkipReason.NOT_PUBLISHED),
            Triple(settled.copy(indexReady = false), 7L, SupportProgramIndexPruneSkipReason.INDEX_NOT_READY),
            // 새 동기화가 수집 중이거나, 실패해 공개되지 않은 세대가 남아 있습니다.
            Triple(settled, 8L, SupportProgramIndexPruneSkipReason.SYNC_NOT_SETTLED),
            Triple(settled.copy(lastSuccessfulSyncAt = now.minusMinutes(29)), 7L, SupportProgramIndexPruneSkipReason.RECENTLY_PUBLISHED),
            Triple(settled.copy(publishedProgramCount = 3), 7L, SupportProgramIndexPruneSkipReason.SNAPSHOT_CHANGED),
            Triple(settled.copy(publishedCatalogFingerprint = "0".repeat(64)), 7L, SupportProgramIndexPruneSkipReason.SNAPSHOT_CHANGED),
        )
        for ((status, latestGeneration, reason) in cases) {
            givenCatalog(status to latestGeneration)

            assertEquals(listOf(SupportProgramIndexPruneResult("BIZINFO", null, reason)), service().prune(), reason.name)
        }
        verifyNoInteractions(client, lexicalClient)
    }

    @Test
    fun prunesExactlyAtTheEndOfTheQuietPeriod() {
        givenCatalog(status("BIZINFO", bizInfo).copy(lastSuccessfulSyncAt = now.minusMinutes(30)) to 7L)
        givenPrune("BIZINFO", bizInfo, deleted = 0)

        assertEquals(listOf(SupportProgramIndexPruneResult("BIZINFO", 0, null)), service().prune())
    }

    @Test
    fun removesAllVersionsOfASourceWhosePublishedSnapshotIsEmpty() {
        givenCatalog(status("CNTRADE_NOTICE", emptyList()) to 2L)
        givenPrune("CNTRADE_NOTICE", emptyList(), deleted = 5)

        assertEquals(listOf(SupportProgramIndexPruneResult("CNTRADE_NOTICE", 5, null)), service().prune())
    }

    @Test
    fun neverDeletesKeywordVersionsWhenTheVectorServiceDidNotConfirmTheCurrentVersions() {
        givenCatalog(status("BIZINFO", bizInfo) to 7L)
        doReturn(AiSupportProgramIndexPrunePayload(1)).`when`(client).prune(pruneRequest("BIZINFO", bizInfo))

        assertThrows(AiServiceCallException::class.java) { service().prune() }
        verify(lexicalClient, never()).pruneSource("BIZINFO", lexicalReferences(bizInfo))

        doReturn(AiSupportProgramIndexPrunePayload(null)).`when`(client).prune(pruneRequest("BIZINFO", bizInfo))
        assertThrows(AiServiceCallException::class.java) { service().prune() }
        verifyNoInteractions(lexicalClient)
    }

    @Test
    fun continuesWithOtherSourcesAndRethrowsTheFirstFailure() {
        givenCatalog(status("BIZINFO", bizInfo) to 7L, status("MSIT", msit) to 3L)
        val vectorFailure = AiServiceCallException.invalidResponse("down", null)
        doThrow(vectorFailure).`when`(client).prune(pruneRequest("BIZINFO", bizInfo))
        givenPrune("MSIT", msit, deleted = 4)
        val lexicalFailure = ElasticsearchClientException("down")
        doThrow(lexicalFailure).`when`(lexicalClient).pruneSource("MSIT", lexicalReferences(msit))

        val thrown = assertThrows(AiServiceCallException::class.java) { service().prune() }

        assertSame(vectorFailure, thrown)
        assertSame(lexicalFailure, thrown.suppressed.single())
        verify(client).prune(pruneRequest("MSIT", msit))
    }

    @Test
    fun stillReportsTheDeletionWhenASyncStartedDuringThePrune() {
        givenCatalog(status("BIZINFO", bizInfo) to 7L)
        givenPrune("BIZINFO", bizInfo, deleted = 9)
        // 조건 확인 때는 7, 정리 직후에는 새 동기화가 시작돼 8입니다. 빠진 버전은 복구 작업이 채웁니다.
        doReturn(7L, 8L).`when`(repository).findLatestStartedGeneration("BIZINFO")

        assertEquals(listOf(SupportProgramIndexPruneResult("BIZINFO", 9, null)), service().prune())
    }

    @Test
    fun rejectsNonPositiveQuietPeriods() {
        assertThrows(IllegalArgumentException::class.java) { SupportProgramIndexPruneProperties(quietPeriod = Duration.ZERO) }
        assertThrows(IllegalArgumentException::class.java) { SupportProgramIndexPruneProperties(fixedDelay = Duration.ofSeconds(-1)) }
        assertThrows(IllegalArgumentException::class.java) { SupportProgramIndexPruneProperties(initialDelay = Duration.ofSeconds(-1)) }
    }

    private fun service() = SupportProgramIndexPruneService(
        repository, client, lexicalClient, SupportProgramIndexPruneProperties(enabled = true), clock,
    )

    private fun givenCatalog(vararg statuses: Pair<SupportProgramSyncStatus, Long>) {
        doReturn(statuses.map { it.first }).`when`(repository).findSyncStatuses()
        doReturn(bizInfo + msit).`when`(repository).findPresent()
        statuses.forEach { (status, latest) -> doReturn(latest).`when`(repository).findLatestStartedGeneration(status.sourceCode) }
    }

    private fun givenPrune(sourceCode: String, programs: List<CatalogSupportProgram>, deleted: Int) {
        doReturn(AiSupportProgramIndexPrunePayload(programs.size)).`when`(client).prune(pruneRequest(sourceCode, programs))
        doReturn(deleted).`when`(lexicalClient).pruneSource(sourceCode, lexicalReferences(programs))
    }

    private fun status(sourceCode: String, programs: List<CatalogSupportProgram>) = SupportProgramSyncStatus(
        sourceCode = sourceCode,
        publishedGeneration = if (sourceCode == "MSIT") 3 else if (sourceCode == "CNTRADE_NOTICE") 2 else 7,
        publishedCatalogFingerprint = SupportProgramCatalogFingerprintHelper.calculate(programs),
        publishedProgramCount = programs.size,
        indexReady = true,
        lastSuccessfulSyncAt = now.minusHours(2),
        lastFailedSyncAt = null,
        lastSyncOutcome = SupportProgramSyncOutcome.SUCCESS,
    )

    private fun pruneRequest(sourceCode: String, programs: List<CatalogSupportProgram>) = AiSupportProgramIndexPruneRequest(
        sourceCode, programs.map { SupportProgramIndexDocumentMapper.fromCatalog(it).reference() },
    )

    private fun lexicalReferences(programs: List<CatalogSupportProgram>) =
        programs.map { ElasticsearchSupportProgramDocumentMapper.fromCatalog(it).reference() }

    private fun msitProgram(id: String) = catalogProgram(id).let {
        it.copy(program = it.program.copy(sourceCode = "MSIT", sourceName = "과학기술정보통신부",
            sourceUrl = "https://www.msit.go.kr/bbs/view.do?nttSeqNo=$id"))
    }
}
