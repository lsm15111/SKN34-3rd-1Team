package ai.govbiz.core.recruitment.domain

import ai.govbiz.core.recruitment.helper.RecruitmentTestHelper
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ProposalStatusResolverTest {

    private val sentAt = LocalDateTime.of(2026, 9, 6, 12, 0)
    private val pending = RecruitmentTestHelper.proposal(createdAt = sentAt)

    @Test
    fun staysPendingForSevenDaysThenExpires() {
        assertEquals(ProposalStatus.PENDING, ProposalStatusResolver.resolve(pending, RecruitmentPostStatus.OPEN, sentAt))
        assertEquals(
            ProposalStatus.PENDING,
            ProposalStatusResolver.resolve(pending, RecruitmentPostStatus.OPEN, sentAt.plusDays(7)),
        )
        assertEquals(
            ProposalStatus.EXPIRED,
            ProposalStatusResolver.resolve(pending, RecruitmentPostStatus.OPEN, sentAt.plusDays(7).plusSeconds(1)),
        )
        assertEquals(
            ProposalStatus.PENDING,
            ProposalStatusResolver.resolve(pending, RecruitmentPostStatus.OPEN, sentAt.plusDays(10), expiryDays = 14),
        )
    }

    @Test
    fun closesAPendingProposalWhenThePostIsNoLongerOpen() {
        assertEquals(ProposalStatus.CLOSED, ProposalStatusResolver.resolve(pending, RecruitmentPostStatus.CLOSED, sentAt))
        assertEquals(ProposalStatus.CLOSED, ProposalStatusResolver.resolve(pending, RecruitmentPostStatus.HIDDEN, sentAt))
        assertEquals(
            ProposalStatus.EXPIRED,
            ProposalStatusResolver.resolve(pending, RecruitmentPostStatus.CLOSED, sentAt.plusDays(8)),
        )
    }

    @Test
    fun keepsDecidedProposalsAsStoredRegardlessOfTimeOrPost() {
        val accepted = RecruitmentTestHelper.proposal(decision = ProposalDecision.ACCEPTED, decidedAt = sentAt.plusHours(1), createdAt = sentAt)
        val declined = RecruitmentTestHelper.proposal(decision = ProposalDecision.DECLINED, decidedAt = sentAt.plusHours(1), createdAt = sentAt)
        val withdrawn = RecruitmentTestHelper.proposal(decision = ProposalDecision.WITHDRAWN, decidedAt = sentAt.plusHours(1), createdAt = sentAt)

        assertEquals(ProposalStatus.ACCEPTED, ProposalStatusResolver.resolve(accepted, RecruitmentPostStatus.CLOSED, sentAt.plusDays(30)))
        assertEquals(ProposalStatus.DECLINED, ProposalStatusResolver.resolve(declined, RecruitmentPostStatus.OPEN, sentAt.plusDays(30)))
        assertEquals(ProposalStatus.WITHDRAWN, ProposalStatusResolver.resolve(withdrawn, RecruitmentPostStatus.HIDDEN, sentAt))
    }

    @Test
    fun rejectsInconsistentDecisionsAndInvalidMessages() {
        assertThrows(IllegalArgumentException::class.java) {
            RecruitmentTestHelper.proposal(decision = ProposalDecision.ACCEPTED, decidedAt = null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecruitmentTestHelper.proposal(decision = ProposalDecision.PENDING, decidedAt = sentAt)
        }
        assertThrows(IllegalArgumentException::class.java) { RecruitmentTestHelper.proposal(message = " ") }
        assertThrows(IllegalArgumentException::class.java) { RecruitmentTestHelper.proposal(message = "a".repeat(501)) }
    }
}
