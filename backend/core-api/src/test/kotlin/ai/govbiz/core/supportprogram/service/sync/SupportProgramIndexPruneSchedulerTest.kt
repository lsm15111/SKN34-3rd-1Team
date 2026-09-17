package ai.govbiz.core.supportprogram.service.sync

import ai.govbiz.core.supportprogram.client.elasticsearch.exception.ElasticsearchClientException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class SupportProgramIndexPruneSchedulerTest {
    private val context = ApplicationContextRunner()
        .withBean(SupportProgramIndexPruneService::class.java, { mock(SupportProgramIndexPruneService::class.java) })
        .withUserConfiguration(SupportProgramIndexPruneScheduler::class.java)

    @Test
    fun deletesNothingUnlessExplicitlyEnabled() {
        context.run { assertThat(it).doesNotHaveBean(SupportProgramIndexPruneScheduler::class.java) }
        context.withPropertyValues("app.support-program-index.prune.enabled=false")
            .run { assertThat(it).doesNotHaveBean(SupportProgramIndexPruneScheduler::class.java) }
        context.withPropertyValues("app.support-program-index.prune.enabled=true")
            .run { assertThat(it).hasSingleBean(SupportProgramIndexPruneScheduler::class.java) }
    }

    @Test
    fun keepsTheScheduleAliveAfterAFailure() {
        val service = mock(SupportProgramIndexPruneService::class.java)
        doThrow(ElasticsearchClientException("down"))
            .doReturn(listOf(SupportProgramIndexPruneResult("BIZINFO", null, SupportProgramIndexPruneSkipReason.SYNC_NOT_SETTLED)))
            .`when`(service).prune()
        val scheduler = SupportProgramIndexPruneScheduler(service)

        scheduler.prune()
        scheduler.prune()

        verify(service, times(2)).prune()
    }
}
