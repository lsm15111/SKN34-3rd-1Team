package ai.govbiz.core.supportprogram.service.sync

import ai.govbiz.core.supportprogram.domain.CatalogSupportProgram
import ai.govbiz.core.supportprogram.facade.SupportProgramCatalogFacade
import ai.govbiz.core.supportprogram.helper.MsitNoticeContentHelper
import ai.govbiz.core.supportprogram.repository.SupportProgramPeriodExtractionRepository
import ai.govbiz.core.supportprogram.repository.SupportProgramRepository
import ai.govbiz.core.supportprogram.service.period.MsitCatalogWriteGuard
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

/** 전체 수집·검증·색인이 성공한 경우에만 MSIT 제공처 스냅샷을 공개합니다. */
@Service
class MsitSupportProgramCatalogSyncService(
    @param:Qualifier("msitSupportProgramCatalogFacade") private val catalogFacade: SupportProgramCatalogFacade,
    private val repository: SupportProgramRepository,
    private val indexSyncService: SupportProgramIndexSyncService,
    private val publicationService: SupportProgramCatalogPublicationService,
    private val periodRepository: SupportProgramPeriodExtractionRepository,
    private val writeGuard: MsitCatalogWriteGuard,
) {
    fun sync(): Int? {
        val generation = repository.startSyncGeneration("MSIT")
        try {
            val programs = withExtractedPeriods(catalogFacade.load())
            indexSyncService.indexSnapshot(programs)
            if (!writeGuard.withLock { publicationService.publish("MSIT", programs, generation) }) return null
            return programs.size
        } catch (exception: RuntimeException) {
            try {
                repository.recordSyncFailureIfCurrent("MSIT", generation)
            } catch (recordingException: RuntimeException) {
                if (recordingException !== exception) exception.addSuppressed(recordingException)
            }
            throw exception
        }
    }

    /**
     * 목록 API는 접수 기간·본문을 주지 않으므로, 첨부 공고문에서 이미 찾은 기간과 원문 발췌를 병합합니다.
     * 발췌는 검색 색인 텍스트에 들어가므로 이 동기화에서만 반영하며, 색인 준비가 공개보다 먼저 실행됩니다.
     */
    private fun withExtractedPeriods(programs: List<CatalogSupportProgram>): List<CatalogSupportProgram> {
        val extractions = periodRepository.findApplicable("MSIT")
        if (extractions.isEmpty()) return programs
        return programs.map { item ->
            val extraction = extractions[item.program.id] ?: return@map item
            item.copy(program = MsitNoticeContentHelper.apply(item.program, extraction))
        }
    }
}
