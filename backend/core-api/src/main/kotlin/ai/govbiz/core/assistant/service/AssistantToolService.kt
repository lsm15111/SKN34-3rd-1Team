package ai.govbiz.core.assistant.service

import ai.govbiz.core.account.repository.CompanyPartnerProfileRepository
import ai.govbiz.core.account.repository.CompanyRepository
import ai.govbiz.core.assistant.domain.AssistantCompanyProfile
import ai.govbiz.core.assistant.domain.AssistantProgramSearchResult
import ai.govbiz.core.assistant.domain.AssistantRecruitmentSummary
import ai.govbiz.core.assistant.domain.AssistantSavedProgramSummary
import ai.govbiz.core.partner.domain.PartnerRecruitmentQuery
import ai.govbiz.core.partner.domain.PartnerRecruitmentSort
import ai.govbiz.core.partner.domain.PartnerRecruitmentStatus
import ai.govbiz.core.partner.domain.PartnerRole
import ai.govbiz.core.supportprogram.domain.SupportProgramCatalogSort
import ai.govbiz.core.supportprogram.domain.SupportProgramStatus
import ai.govbiz.core.supportprogram.repository.SupportProgramRepository
import ai.govbiz.core.supportprogram.service.catalog.SupportProgramCatalogService
import ai.govbiz.core.supportprogram.service.catalog.exception.SupportProgramCatalogFilterException
import ai.govbiz.core.supportprogram.service.saved.SavedSupportProgramService
import org.springframework.stereotype.Service

/**
 * 도우미 도구 에이전트가 읽는 자료를 만듭니다. 전부 읽기 전용이며 기존 Service·Repository를 그대로 씁니다.
 * 담당자 연락처·이메일·사업자등록번호는 어떤 응답에도 넣지 않고, 사용자가 쓴 본문은 개인정보를 가린 뒤 잘라 넣습니다.
 */
@Service
class AssistantToolService(
    private val companyRepository: CompanyRepository,
    private val partnerProfileRepository: CompanyPartnerProfileRepository,
    private val recruitmentService: ai.govbiz.core.partner.service.PartnerRecruitmentService,
    private val savedSupportProgramService: SavedSupportProgramService,
    private val supportProgramRepository: SupportProgramRepository,
    private val catalogService: SupportProgramCatalogService,
) {
    fun companyProfile(accountId: Long): AssistantCompanyProfile {
        val company = companyRepository.findByAccountId(accountId) ?: return AssistantCompanyProfile.NOT_REGISTERED
        val partner = partnerProfileRepository.findByCompanyId(company.id).input
        return AssistantCompanyProfile(
            registered = true,
            companyName = company.companyName,
            region = company.profile.region,
            industry = company.profile.industry,
            foundedYear = company.profile.foundedYear,
            roles = partner?.roles?.map { it.name } ?: emptyList(),
            interestAreas = partner?.interestAreas ?: emptyList(),
            introduction = partner?.introduction?.let { clip(it, INTRODUCTION_MAX) },
            capabilities = partner?.capabilities ?: emptyList(),
        )
    }

    /** 모집 중인 남의 글만 마감 임박순으로 최대 [RECRUITMENT_MAX]건 돌려줍니다. 조건이 틀리면 조건을 무시하지 않고 빈 목록입니다. */
    fun searchRecruitments(accountId: Long, region: String?, seekingRole: String?, keyword: String?): List<AssistantRecruitmentSummary> {
        val role = seekingRole?.trim()?.takeIf { it.isNotEmpty() }?.let { name -> PartnerRole.entries.firstOrNull { it.name == name } ?: return emptyList() }
        val normalizedRegion = region?.trim()?.takeIf { it.isNotEmpty() && it.length <= REGION_MAX }
        val normalizedKeyword = keyword?.trim()?.take(PartnerRecruitmentQuery.MAX_KEYWORD_LENGTH) ?: ""
        val page = recruitmentService.findPage(
            PartnerRecruitmentQuery(
                keyword = normalizedKeyword,
                seekingRoles = role?.let { setOf(it) } ?: emptySet(),
                regions = normalizedRegion?.let { setOf(it) } ?: emptySet(),
                mineAccountId = null,
                sort = PartnerRecruitmentSort.DEADLINE,
                page = 1,
                pageSize = PartnerRecruitmentQuery.MAX_PAGE_SIZE,
            ),
        )
        return page.recruitments.asSequence()
            .filter { it.status == PartnerRecruitmentStatus.OPEN && !it.recruitment.isOwnedBy(accountId) }
            .take(RECRUITMENT_MAX)
            .map { view ->
                val recruitment = view.recruitment
                AssistantRecruitmentSummary(
                    id = recruitment.id,
                    title = recruitment.content.title,
                    companyName = recruitment.company.companyName,
                    companyRegion = recruitment.company.region,
                    companyIndustry = recruitment.company.industry,
                    ownRole = recruitment.content.ownRole.name,
                    seekingRole = recruitment.content.seekingRole.name,
                    seekingCount = recruitment.content.seekingCount,
                    region = recruitment.content.region,
                    minimumCompanyAgeYears = recruitment.content.minimumCompanyAgeYears,
                    capabilities = recruitment.content.capabilities,
                    recruitmentDeadline = recruitment.content.recruitmentDeadline,
                    programTitle = recruitment.program.title,
                    programApplicationEndDate = recruitment.program.applicationEndDate,
                    body = clip(recruitment.content.body, BODY_MAX),
                )
            }
            .toList()
    }

    /** 관심 공고 최대 [SAVED_MAX]건입니다. 원문이 수집된 공고만 `documentId`를 갖습니다. */
    fun savedPrograms(accountId: Long): List<AssistantSavedProgramSummary> =
        savedSupportProgramService.list(accountId).take(SAVED_MAX).map { saved ->
            val program = saved.program
            val document = try {
                supportProgramRepository.findPresentSourceDocument(program.sourceCode, program.id)
            } catch (_: IllegalArgumentException) {
                null
            }
            AssistantSavedProgramSummary(
                sourceCode = program.sourceCode,
                sourceProgramId = program.id,
                title = program.title,
                organization = program.organization,
                applicationEndDate = program.applicationEndDate,
                status = program.status.name,
                documentId = if (document != null && document.sourceUrl == program.sourceUrl) program.sourceQualifiedId else null,
            )
        }

    /**
     * 공개 공고를 키워드·지역으로 찾습니다. 카탈로그(DB) 검색이라 AI 점수화·임베딩을 쓰지 않습니다.
     *
     * 카탈로그 검색은 제목·기관을 통째로 포함하는지 보므로 "창업 지원 사업" 같은 말은 한 건도 찾지 못합니다.
     * 그래서 가장 긴 낱말로 마감 임박순 [PROGRAM_SCAN_MAX]건을 훑은 뒤 나머지 낱말이 더 많이 맞는 공고를 앞에 둡니다.
     * 이미 담은 공고인지 함께 표시해, 가이드가 담기 카드와 빼기 카드를 잘못 고르지 않게 합니다.
     */
    fun findPrograms(accountId: Long, keyword: String?, region: String?): List<AssistantProgramSearchResult> {
        val normalizedKeyword = keyword?.trim()?.take(KEYWORD_MAX).orEmpty()
        val normalizedRegion = region?.trim()?.take(REGION_MAX).orEmpty()
        if (normalizedKeyword.isEmpty() && normalizedRegion.isEmpty()) return emptyList()
        val words = normalizedKeyword.split(WHITESPACE).filter { it.isNotBlank() }.take(KEYWORD_WORD_MAX)
        val found = try {
            catalogService.browse(
                rawKeyword = words.maxByOrNull { it.length }.orEmpty(),
                rawRegion = normalizedRegion,
                status = SupportProgramStatus.OPEN,
                sort = SupportProgramCatalogSort.DEADLINE,
                page = 1,
                pageSize = PROGRAM_SCAN_MAX,
            )
        } catch (_: SupportProgramCatalogFilterException) {
            // 모르는 지역 이름 같은 조건은 조건을 무시한 결과로 답하지 않고 빈 목록으로 둡니다.
            return emptyList()
        }
        val saved = savedSupportProgramService.list(accountId).map { it.program.sourceCode to it.program.id }.toSet()
        // 정렬은 안정적이라 맞는 낱말 수가 같으면 마감 임박순이 그대로 유지됩니다.
        return found.programs
            .sortedByDescending { program -> words.count { program.title.contains(it, true) || program.organization.contains(it, true) } }
            .take(PROGRAM_SEARCH_MAX)
            .map { program ->
                AssistantProgramSearchResult(
                    sourceCode = program.sourceCode,
                    sourceProgramId = program.id,
                    title = program.title,
                    organization = program.organization,
                    applicationEndDate = program.applicationEndDate,
                    status = program.status.name,
                    regions = program.regions.take(REGION_LIST_MAX),
                    saved = (program.sourceCode to program.id) in saved,
                )
            }
    }

    /** 개인정보를 가린 뒤 자릅니다. 잘린 본문은 끝에 줄임표를 붙여 모델이 문장이 끝난 것으로 오해하지 않게 합니다. */
    private fun clip(text: String, max: Int): String {
        val masked = AssistantPiiMasker.mask(text.trim())
        return if (masked.length <= max) masked else masked.take(max).trimEnd() + "…"
    }

    companion object {
        const val RECRUITMENT_MAX = 30
        const val SAVED_MAX = 10
        const val BODY_MAX = 600
        const val INTRODUCTION_MAX = 300
        const val REGION_MAX = 50
        const val KEYWORD_MAX = 100
        const val PROGRAM_SEARCH_MAX = 5
        const val PROGRAM_SCAN_MAX = 50
        const val KEYWORD_WORD_MAX = 4
        const val REGION_LIST_MAX = 5
        private val WHITESPACE = Regex("\\s+")
    }
}
