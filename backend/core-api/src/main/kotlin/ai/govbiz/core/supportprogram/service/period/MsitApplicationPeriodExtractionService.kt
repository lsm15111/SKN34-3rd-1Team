package ai.govbiz.core.supportprogram.service.period

import ai.govbiz.core.supportprogram.client.document.SupportProgramDocumentException
import ai.govbiz.core.supportprogram.client.document.SupportProgramDocumentParser
import ai.govbiz.core.supportprogram.client.msit.MsitAttachmentClient
import ai.govbiz.core.supportprogram.helper.SupportProgramApplicationPeriodExtractorHelper
import ai.govbiz.core.supportprogram.repository.SupportProgramPeriodExtractionRepository
import ai.govbiz.core.supportprogram.repository.SupportProgramPeriodExtractionRepository.Status
import ai.govbiz.core.supportprogram.repository.mapper.SupportProgramPeriodExtractionTargetDbRow
import ai.govbiz.core.supportprogram.service.period.config.MsitApplicationPeriodExtractionProperties
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

/**
 * 공개 MSIT 공고의 공식 첨부(PDF/HWP/HWPX)를 읽어 신청 기간을 코드 규칙으로 찾습니다. AI를 호출하지 않습니다.
 * 네트워크·파싱은 트랜잭션 밖에서 수행하고, 결과 저장만 짧은 트랜잭션으로 처리합니다.
 */
@Service
class MsitApplicationPeriodExtractionService(
    private val attachmentClient: MsitAttachmentClient,
    private val parser: SupportProgramDocumentParser,
    private val repository: SupportProgramPeriodExtractionRepository,
    private val properties: MsitApplicationPeriodExtractionProperties,
    @param:Qualifier("seoulClock") private val clock: Clock,
) {
    fun runBatch(): Int {
        val reapplied = repository.reapplyUnapplied(SOURCE_CODE)
        if (reapplied > 0) logger.info("msit_period_extraction_reapplied count={}", reapplied)
        val targets = repository.findDueTargets(SOURCE_CODE, LocalDateTime.now(clock), properties.batchSize)
        targets.forEach(::process)
        return targets.size
    }

    private fun process(target: SupportProgramPeriodExtractionTargetDbRow) {
        val started = System.nanoTime()
        val attempt = target.attemptCount + 1
        val now = LocalDateTime.now(clock)
        val outcome = try {
            val attachments = attachmentClient.collect(SOURCE_CODE, target.sourceProgramId, target.sourceUrl)
            val texts = attachments.files.mapNotNull { file ->
                try {
                    parser.parse(file.bytes, file.format).joinToString("\n") { it.text }
                } catch (error: SupportProgramDocumentException) {
                    // 한 첨부가 스캔본·암호화·과대 파일이어도 다른 공식 첨부로 계속 확인합니다.
                    null
                }
            }
            if (texts.isEmpty()) {
                repository.saveOutcome(SOURCE_CODE, target.sourceProgramId, Status.DOCUMENT_UNAVAILABLE, "NO_READABLE_ATTACHMENT", attempt, now, now.plus(properties.recheckAfter))
                "DOCUMENT_UNAVAILABLE"
            } else {
                val period = SupportProgramApplicationPeriodExtractorHelper.extract(texts.joinToString("\n"), publishedOn(target.sourceSortTimestamp))
                if (period == null) {
                    repository.saveOutcome(SOURCE_CODE, target.sourceProgramId, Status.NOT_FOUND, "PERIOD_NOT_FOUND", attempt, now, now.plus(properties.recheckAfter))
                    "NOT_FOUND"
                } else {
                    repository.saveExtracted(SOURCE_CODE, target.sourceProgramId, period, attempt, now, now.plus(properties.extractedRecheckAfter))
                    "EXTRACTED"
                }
            }
        } catch (error: SupportProgramDocumentException) {
            when (error.reason) {
                SupportProgramDocumentException.Reason.UNAVAILABLE, SupportProgramDocumentException.Reason.INVALID -> {
                    // 일시 장애는 짧게 재시도하고, 반복되면 재확인 주기로 늦춥니다.
                    val next = if (attempt < MAX_QUICK_RETRIES) now.plus(properties.retryAfter.multipliedBy(attempt.toLong())) else now.plus(properties.recheckAfter)
                    repository.saveOutcome(SOURCE_CODE, target.sourceProgramId, Status.RETRY_WAITING, error.reason.name, attempt, now, next)
                    "RETRY_WAITING"
                }
                else -> {
                    repository.saveOutcome(SOURCE_CODE, target.sourceProgramId, Status.DOCUMENT_UNAVAILABLE, error.reason.name, attempt, now, now.plus(properties.recheckAfter))
                    "DOCUMENT_UNAVAILABLE"
                }
            }
        }
        logger.info(
            "msit_period_extraction sourceProgramId={} status={} attempt={} durationMs={}",
            target.sourceProgramId, outcome, attempt, (System.nanoTime() - started) / 1_000_000,
        )
    }

    private fun publishedOn(sortTimestamp: String?): LocalDate? =
        try { sortTimestamp?.takeIf { it.length >= 10 }?.let { LocalDate.parse(it.substring(0, 10)) } } catch (_: DateTimeParseException) { null }

    private companion object {
        const val SOURCE_CODE = "MSIT"
        const val MAX_QUICK_RETRIES = 3
        val logger = LoggerFactory.getLogger(MsitApplicationPeriodExtractionService::class.java)
    }
}
