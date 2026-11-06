package uz.murodjon.robotcallv2.callrecord.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.agent.summary.SummarizedCall;
import uz.murodjon.robotcallv2.agent.summary.SummaryService;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.DataExtractionField;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallReanalysisJobRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallResultRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallTranscriptRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallReanalysisJob;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallReanalysisTarget;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallReanalysisMode;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallReanalysisStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CallReanalysisWorkerTest {

    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");
    private static final long COMPANY_ID = 1L;
    private static final long AGENT_ID = 7L;
    private static final long JOB_ID = 3L;

    private final CallReanalysisJobRepository jobRepository = mock(CallReanalysisJobRepository.class);
    private final CallResultRepository callResultRepository = mock(CallResultRepository.class);
    private final CallTranscriptRepository callTranscriptRepository = mock(CallTranscriptRepository.class);
    private final AiAgentUseCase aiAgentUseCase = mock(AiAgentUseCase.class);
    private final CallBillingUseCase callBillingUseCase = mock(CallBillingUseCase.class);
    private final SummaryService summaryService = mock(SummaryService.class);
    private final CallMemoryWriter callMemoryWriter = mock(CallMemoryWriter.class);
    private final AiAgent agent = mock(AiAgent.class);
    private final CallReanalysisWorker worker = new CallReanalysisWorker(jobRepository, callResultRepository,
            callTranscriptRepository, aiAgentUseCase, callBillingUseCase, summaryService, callMemoryWriter,
            (name, job) -> {
                job.run();
                return true;
            },
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(jobRepository.findNextActive()).thenReturn(job(CallReanalysisMode.ANALYSIS));
        when(aiAgentUseCase.findAgent(COMPANY_ID, AGENT_ID)).thenReturn(agent);
        when(agent.dataNeeded()).thenReturn(List.of(new DataExtractionField("amount", "number", "Summa", "")));
        when(callBillingUseCase.hasBalanceForCall(COMPANY_ID)).thenReturn(true);
        when(jobRepository.updateProgress(any())).thenReturn(true);
        when(callTranscriptRepository.findTranscriptLines(anyLong())).thenReturn(List.of("CLIENT: 250 ming"));
    }

    /** Only the analysis keys change; the call is charged under this job and the job completes. */
    @Test
    void aCallGetsItsAnalysisReplacedAndIsChargedUnderTheJob() {
        when(callResultRepository.findReanalysisTargets(eq(COMPANY_ID), eq(AGENT_ID), any(), any(), eq(0L), anyInt()))
                .thenReturn(List.of(target(11L)));
        when(summaryService.summarize(anyLong(), any(), isNull(), eq(agent), any())).thenReturn(summarized(Map.of("amount", 250)));
        when(callResultRepository.findOutcomeByCallId(11L)).thenReturn(Map.of("reasonCode", "NO_MONEY", "amount", 100));
        when(callBillingUseCase.chargeReanalysis(eq(COMPANY_ID), eq(11L), eq(JOB_ID), any())).thenReturn(40L);

        worker.processNextBatch();

        verify(callResultRepository).updateOutcome(11L, Map.of("reasonCode", "NO_MONEY", "amount", 250));
        ArgumentCaptor<CallReanalysisJob> progress = ArgumentCaptor.forClass(CallReanalysisJob.class);
        verify(jobRepository, atLeastOnce()).updateProgress(progress.capture());
        CallReanalysisJob last = progress.getValue();
        assertThat(last.status()).isEqualTo(CallReanalysisStatus.COMPLETED);
        assertThat(last.updatedCalls()).isEqualTo(1);
        assertThat(last.chargedUzs()).isEqualTo(40L);
        assertThat(last.lastCallId()).isEqualTo(11L);
    }

    /** A call the model gives no answer for keeps its outcome and is not charged. */
    @Test
    void aCallWithoutAnAnswerIsCountedAsFailedAndLeftAlone() {
        when(callResultRepository.findReanalysisTargets(anyLong(), anyLong(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(target(11L)));
        when(summaryService.summarize(anyLong(), any(), any(), any(), any())).thenReturn(null);

        worker.processNextBatch();

        verify(callResultRepository, never()).updateOutcome(anyLong(), any());
        verify(callBillingUseCase, never()).chargeReanalysis(anyLong(), anyLong(), anyLong(), any());
        ArgumentCaptor<CallReanalysisJob> progress = ArgumentCaptor.forClass(CallReanalysisJob.class);
        verify(jobRepository, atLeastOnce()).updateProgress(progress.capture());
        assertThat(progress.getValue().failedCalls()).isEqualTo(1);
    }

    /** A cancel from the panel is noticed after the call in hand, before the next one is paid for. */
    @Test
    void aCancelledJobStopsAfterTheCallInHand() {
        when(callResultRepository.findReanalysisTargets(anyLong(), anyLong(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(target(11L), target(12L)));
        when(summaryService.summarize(anyLong(), any(), any(), any(), any())).thenReturn(summarized(Map.of()));
        when(callResultRepository.findOutcomeByCallId(anyLong())).thenReturn(Map.of());
        when(jobRepository.updateProgress(any())).thenReturn(false);

        worker.processNextBatch();

        verify(summaryService, times(1)).summarize(anyLong(), any(), any(), any(), any());
    }

    /** With no balance left the job stops as FAILED rather than analysing for free. */
    @Test
    void anEmptyBalanceStopsTheJob() {
        when(callResultRepository.findReanalysisTargets(anyLong(), anyLong(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(target(11L)));
        when(callBillingUseCase.hasBalanceForCall(COMPANY_ID)).thenReturn(false);

        worker.processNextBatch();

        verify(summaryService, never()).summarize(anyLong(), any(), any(), any(), any());
        ArgumentCaptor<CallReanalysisJob> progress = ArgumentCaptor.forClass(CallReanalysisJob.class);
        verify(jobRepository).updateProgress(progress.capture());
        assertThat(progress.getValue().status()).isEqualTo(CallReanalysisStatus.FAILED);
    }

    /** Contact backfill from stored outcomes: no model, no charge, no balance check; the stored outcome goes to the contact. */
    @Test
    void storedBackfillHandsTheStoredOutcomeToTheContactWithoutTheModel() {
        when(jobRepository.findNextActive()).thenReturn(job(CallReanalysisMode.CONTACTS_STORED));
        when(callBillingUseCase.hasBalanceForCall(COMPANY_ID)).thenReturn(false);
        when(callResultRepository.findReanalysisTargets(anyLong(), anyLong(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(target(11L), target(12L)));
        when(callResultRepository.findOutcomeByCallId(anyLong())).thenReturn(Map.of("city", "Toshkent"));
        when(callMemoryWriter.saveStoredContactFields(11L, COMPANY_ID, agent, Map.of("city", "Toshkent")))
                .thenReturn(true);

        worker.processNextBatch();

        verify(summaryService, never()).summarize(anyLong(), any(), any(), any(), any());
        verify(callResultRepository, never()).updateOutcome(anyLong(), any());
        verify(callMemoryWriter).saveStoredContactFields(12L, COMPANY_ID, agent, Map.of("city", "Toshkent"));
        ArgumentCaptor<CallReanalysisJob> progress = ArgumentCaptor.forClass(CallReanalysisJob.class);
        verify(jobRepository, atLeastOnce()).updateProgress(progress.capture());
        CallReanalysisJob last = progress.getValue();
        assertThat(last.status()).isEqualTo(CallReanalysisStatus.COMPLETED);
        assertThat(last.processedCalls()).isEqualTo(2);
        assertThat(last.updatedCalls()).isEqualTo(1);
        assertThat(last.failedCalls()).isZero();
    }

    /** Re-extracting backfill: the merged new outcome is saved on the call and then given to the contact. */
    @Test
    void reextractingBackfillGivesTheNewOutcomeToTheContact() {
        when(jobRepository.findNextActive()).thenReturn(job(CallReanalysisMode.CONTACTS_REEXTRACT));
        when(callResultRepository.findReanalysisTargets(anyLong(), anyLong(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(target(11L)));
        when(summaryService.summarize(anyLong(), any(), isNull(), eq(agent), any())).thenReturn(summarized(Map.of("amount", 250)));
        when(callResultRepository.findOutcomeByCallId(11L)).thenReturn(Map.of("reasonCode", "NO_MONEY", "amount", 100));

        worker.processNextBatch();

        Map<String, Object> merged = Map.of("reasonCode", "NO_MONEY", "amount", 250);
        verify(callResultRepository).updateOutcome(11L, merged);
        verify(callMemoryWriter).saveStoredContactFields(11L, COMPANY_ID, agent, merged);
    }

    private static CallReanalysisJob job(CallReanalysisMode mode) {
        return new CallReanalysisJob(JOB_ID, COMPANY_ID, AGENT_ID, mode, NOW.minusSeconds(86_400), NOW,
                CallReanalysisStatus.QUEUED, 1, 0, 0, 0, 0, 0, 0, 0, null, NOW, null, null);
    }

    private static CallReanalysisTarget target(long callId) {
        return new CallReanalysisTarget(callId, NOW.minusSeconds(3600));
    }

    private static SummarizedCall summarized(Map<String, Object> outcome) {
        return new SummarizedCall(new CallSummary("xulosa", outcome, null, false, null, null, 90, 50), 1200, 300, 0);
    }
}
