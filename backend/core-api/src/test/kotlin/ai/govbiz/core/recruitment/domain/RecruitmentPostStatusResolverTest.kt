package ai.govbiz.core.recruitment.domain

import ai.govbiz.core.recruitment.helper.RecruitmentTestHelper
import ai.govbiz.core.supportprogram.domain.SupportProgramStatus
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecruitmentPostStatusResolverTest {

    private val program = RecruitmentTestHelper.program()

    @Test
    fun isOpenUntilTheClosingDateInclusive() {
        val post = RecruitmentTestHelper.post(draft = RecruitmentTestHelper.draft(closesOn = LocalDate.of(2026, 9, 20)))

        assertEquals(RecruitmentPostStatus.OPEN, RecruitmentPostStatusResolver.resolve(post, program, LocalDate.of(2026, 9, 20)))
        assertEquals(RecruitmentPostStatus.CLOSED, RecruitmentPostStatusResolver.resolve(post, program, LocalDate.of(2026, 9, 21)))
    }

    @Test
    fun closesWhenTheLinkedProgramIsGoneOrClosed() {
        val post = RecruitmentTestHelper.post()

        assertEquals(RecruitmentPostStatus.CLOSED, RecruitmentPostStatusResolver.resolve(post, null, RecruitmentTestHelper.TODAY))
        assertEquals(
            RecruitmentPostStatus.CLOSED,
            RecruitmentPostStatusResolver.resolve(
                post,
                RecruitmentTestHelper.program(status = SupportProgramStatus.CLOSED),
                RecruitmentTestHelper.TODAY,
            ),
        )
        assertEquals(
            RecruitmentPostStatus.OPEN,
            RecruitmentPostStatusResolver.resolve(
                post,
                RecruitmentTestHelper.program(status = SupportProgramStatus.UNKNOWN, applicationEndDate = null),
                RecruitmentTestHelper.TODAY,
            ),
        )
    }

    @Test
    fun prefersHiddenOverClosedAndClosedEarlyOverDates() {
        val closedEarly = RecruitmentTestHelper.post(closedEarlyAt = RecruitmentTestHelper.NOW)
        val hidden = RecruitmentTestHelper.post(
            closedEarlyAt = RecruitmentTestHelper.NOW,
            hiddenAt = RecruitmentTestHelper.NOW,
            hiddenReason = "연락처 노출",
        )

        assertEquals(RecruitmentPostStatus.CLOSED, RecruitmentPostStatusResolver.resolve(closedEarly, program, RecruitmentTestHelper.TODAY))
        assertEquals(RecruitmentPostStatus.HIDDEN, RecruitmentPostStatusResolver.resolve(hidden, null, LocalDate.of(2030, 1, 1)))
    }

    @Test
    fun rejectsHiddenPostsWithoutAReasonAndInvalidDrafts() {
        assertThrows(IllegalArgumentException::class.java) {
            RecruitmentTestHelper.post(hiddenAt = RecruitmentTestHelper.NOW, hiddenReason = " ")
        }
        assertThrows(IllegalArgumentException::class.java) { RecruitmentTestHelper.draft(title = "") }
        assertThrows(IllegalArgumentException::class.java) { RecruitmentTestHelper.draft(title = "a".repeat(81)) }
        assertThrows(IllegalArgumentException::class.java) {
            RecruitmentTestHelper.draft(requiredCapabilities = listOf("중복", "중복"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecruitmentTestHelper.draft().copy(ourRole = RecruitmentRole.DEMAND)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecruitmentTestHelper.draft().copy(wantedCompanyCount = 11)
        }
    }

    @Test
    fun detectsEmailAddressesAndKoreanPhoneNumbers() {
        assertTrue(ContactPatternPolicy.containsContact("연락은 manager@company.co.kr 로 주세요"))
        assertTrue(ContactPatternPolicy.containsContact("문의 010-1234-5678"))
        assertTrue(ContactPatternPolicy.containsContact("문의 01012345678"))
        assertTrue(ContactPatternPolicy.containsContact("사무실 02-123-4567"))
        assertTrue(ContactPatternPolicy.containsContact("+82-10-1234-5678"))
        assertFalse(ContactPatternPolicy.containsContact("2026-09-30 18:00까지 접수, 사업비 30% 협의"))
        assertFalse(ContactPatternPolicy.containsContact("사업자등록번호 124-81-00998 형식은 연락처가 아닙니다"))
        assertFalse(ContactPatternPolicy.containsContact("PBLN_000000091203 공고에 묶인 모집"))
    }
}
