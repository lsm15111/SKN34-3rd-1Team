package ai.govbiz.core.supportprogram.service.sync

import ai.govbiz.core.supportprogram.domain.CatalogSupportProgram
import ai.govbiz.core.supportprogram.facade.SupportProgramCatalogFacade
import ai.govbiz.core.supportprogram.helper.SupportProgramApplicationPeriodExtractorHelper
import ai.govbiz.core.supportprogram.repository.SupportProgramPeriodExtractionRepository
import ai.govbiz.core.supportprogram.repository.SupportProgramRepository
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
) {
    fun sync(): Int? {
        val generation = repository.startSyncGeneration("MSIT")
        try {
            val programs = withExtractedPeriods(catalogFacade.load())
            indexSyncService.indexSnapshot(programs)
            if (!publicationService.publish("MSIT", programs, generation)) return null
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

    /** 목록 API는 접수 기간을 주지 않으므로, 첨부 공고문에서 이미 찾은 기간을 다시 null로 덮어쓰지 않게 합니다. */
    private fun withExtractedPeriods(programs: List<CatalogSupportProgram>): List<CatalogSupportProgram> {
        val periods = periodRepository.findExtracted("MSIT")
        if (periods.isEmpty()) return programs
        return programs.map { item ->
            val period = periods[item.program.id] ?: return@map item
            item.copy(program = item.program.copy(
                applicationPeriod = period.displayText(), applicationStartDate = period.startDate, applicationEndDate = period.endDate,
                summary = SupportProgramApplicationPeriodExtractorHelper.OFFICIAL_ATTACHMENT_SUMMARY,
            ))
        }
    }
}
