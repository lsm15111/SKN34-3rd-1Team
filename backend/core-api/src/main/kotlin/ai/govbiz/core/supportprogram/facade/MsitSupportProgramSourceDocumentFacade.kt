package ai.govbiz.core.supportprogram.facade

import ai.govbiz.core.supportprogram.client.document.SupportProgramDocumentException
import ai.govbiz.core.supportprogram.client.document.SupportProgramDocumentParser
import ai.govbiz.core.supportprogram.client.msit.MsitAttachmentClient
import ai.govbiz.core.supportprogram.domain.SupportProgram
import ai.govbiz.core.supportprogram.domain.SupportProgramSourceDocument
import ai.govbiz.core.supportprogram.facade.exception.SupportProgramSourceDocumentFacadeException
import ai.govbiz.core.supportprogram.helper.MsitNoticeContentHelper
import ai.govbiz.core.supportprogram.helper.SupportProgramContentHashHelper
import java.time.Clock
import java.time.LocalDateTime
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * 과기정통부 공고는 목록·상세 페이지에 본문이 거의 없어, 상세가 직접 연결한 공식 첨부 공고문을 근거 원문으로 씁니다.
 *
 * 공고문 파일을 신청서 서식보다 먼저 싣고, 근거 청크 한도(50개) 안에 들도록 줄 단위로 길이를 제한합니다.
 * 잘린 뒤쪽 조건은 근거가 없으므로 답변은 `근거 부족`이 될 수 있으며 내용을 추측하지 않습니다.
 */
@Component
class MsitSupportProgramSourceDocumentFacade(
    private val attachmentClient: MsitAttachmentClient,
    private val parser: SupportProgramDocumentParser,
    @param:Qualifier("seoulClock") private val clock: Clock,
) {
    fun load(program: SupportProgram): SupportProgramSourceDocument {
        check(program.sourceCode == MSIT_SOURCE_CODE) { "MSIT source document requires MSIT" }
        val attachments = try {
            attachmentClient.collect(MSIT_SOURCE_CODE, program.id, program.sourceUrl)
        } catch (exception: SupportProgramDocumentException) {
            throw failure(exception)
        }
        val ordered = attachments.files.sortedBy { file -> if (FORM_FILE.containsMatchIn(file.fileName)) 1 else 0 }
        val lines = ordered.flatMap { file ->
            try {
                parser.parse(file.bytes, file.format).flatMap { block ->
                    // HWP 글꼴 기호 같은 사용자 정의 영역 문자는 AI 서비스가 청크를 거부하므로 공백으로 바꿉니다.
                    block.text.lineSequence().map { MsitNoticeContentHelper.sanitize(it).trim() }.filter(String::isNotBlank).toList()
                }
            } catch (_: SupportProgramDocumentException) {
                // 스캔본·암호화된 한 첨부는 건너뛰고 읽을 수 있는 다른 공식 첨부로 근거를 만듭니다.
                emptyList()
            }
        }
        val content = boundedContent(lines)
        if (content.isBlank()) {
            throw SupportProgramSourceDocumentFacadeException.fromClient(
                SupportProgramSourceDocumentFacadeException.Failure.INVALID_RESPONSE,
                "MSIT notice has no readable official attachment",
                SupportProgramDocumentException(SupportProgramDocumentException.Reason.UNSUPPORTED),
            )
        }
        return SupportProgramSourceDocument(
            sourceCode = MSIT_SOURCE_CODE,
            sourceProgramId = program.id,
            sourceUrl = program.sourceUrl,
            content = content,
            contentHash = SupportProgramContentHashHelper.sha256(content),
            fetchedAt = LocalDateTime.now(clock),
        )
    }

    private fun boundedContent(lines: List<String>): String {
        val builder = StringBuilder()
        for (line in lines) {
            val value = line.take(MAX_SOURCE_CHARACTERS)
            if (builder.length + value.length + 1 > MAX_SOURCE_CHARACTERS) break
            if (builder.isNotEmpty()) builder.append('\n')
            builder.append(value)
        }
        return builder.toString()
    }

    private fun failure(exception: SupportProgramDocumentException) = SupportProgramSourceDocumentFacadeException.fromClient(
        failure = when (exception.reason) {
            SupportProgramDocumentException.Reason.UNAVAILABLE -> SupportProgramSourceDocumentFacadeException.Failure.UNAVAILABLE
            SupportProgramDocumentException.Reason.NOT_FOUND -> SupportProgramSourceDocumentFacadeException.Failure.UPSTREAM_ERROR
            else -> SupportProgramSourceDocumentFacadeException.Failure.INVALID_RESPONSE
        },
        message = "MSIT official attachment could not be collected: ${exception.reason}",
        cause = exception,
    )

    companion object {
        /** 근거 청커가 1,500자 청크 50개를 넘기지 않도록 줄 경계에서 자르는 상한입니다. */
        const val MAX_SOURCE_CHARACTERS = 55_000
        private const val MSIT_SOURCE_CODE = "MSIT"
        private val FORM_FILE = Regex("서식|신청서|양식|작성\\s*요령|계획서|동의서|확인서|체크리스트")
    }
}
