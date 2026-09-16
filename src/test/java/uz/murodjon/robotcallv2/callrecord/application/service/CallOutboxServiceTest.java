package uz.murodjon.robotcallv2.callrecord.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.agent.summary.SummarizedCall;
import uz.murodjon.robotcallv2.agent.summary.SummaryService;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallOutboxRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallAttempt;
import uz.murodjon.robotcallv2.callrecord.domain.entity.PendingSummary;
import uz.murodjon.robotcallv2.callrecord.infrastructure.config.OutboxProperties;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CallOutboxServiceTest {

    private static final long COMPANY_ID = 7L;
    private static final long CALL_ID = 55L;
    private static final long LEAD_ID = 42L;
    private static final long SCENARIO_ID = 3L;
    private static final long AGENT_ID = 5L;
    private static final long TARGET_ID = 21L;
    private static final String PHONE = "+998901234567";
    private static final String TRANSCRIPT = "AGENT: Salom\nCLIENT: Ertaga to'layman\n";
    private static final Instant NOW = Instant.parse("2026-09-14T10:00:00Z");
    private static final CallSummary REFUSAL = new CallSummary("Mijoz rad etdi.", Map.of(), null, false, null, null);
    private static final CallSummary PROMISE = new CallSummary("Mijoz 20-sentabrda to'laydi.",
            Map.of("promisedDate", "2026-09-20"), null, false, null, null);

    private CallOutboxRepository repository;
    private AiAgentUseCase aiAgentUseCase;
    private CallRecordService callRecordService;
    private SummaryService summaryService;
    private ScenarioUseCase scenarioUseCase;
    private CallFinalizer callFinalizer;
    private CampaignTargetUseCase campaignTargetUseCase;
    private ClusterJobLock clusterJobLock;
    private Scenario scenario;
    private AiAgent agent;
    private CallOutboxService service;

    @BeforeEach
    void setUp() {
        repository = mock(CallOutboxRepository.class);
        aiAgentUseCase = mock(AiAgentUseCase.class);
        callRecordService = mock(CallRecordService.class);
        summaryService = mock(SummaryService.class);
        scenarioUseCase = mock(ScenarioUseCase.class);
        callFinalizer = mock(CallFinalizer.class);
        campaignTargetUseCase = mock(CampaignTargetUseCase.class);
        clusterJobLock = mock(ClusterJobLock.class);
        scenario = new Scenario(SCENARIO_ID, "debt", 1, "Qarz", null, false, true, mock(ScenarioDefinition.class),
                NOW, null);
        agent = AiAgentFixtures.agent(AGENT_ID, COMPANY_ID, SCENARIO_ID, "uz-UZ", null);

        when(clusterJobLock.runExclusively(anyString(), any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return true;
        });
        when(callRecordService.findTranscriptText(CALL_ID)).thenReturn(TRANSCRIPT);
        when(callRecordService.findCompanyIdByAttemptId(CALL_ID)).thenReturn(COMPANY_ID);
        when(callRecordService.findPhoneByAttemptId(CALL_ID)).thenReturn(PHONE);
        when(callRecordService.findTargetIdByAttemptId(CALL_ID)).thenReturn(TARGET_ID);
        when(scenarioUseCase.requireScenario(COMPANY_ID, SCENARIO_ID)).thenReturn(scenario);
        when(aiAgentUseCase.findAgent(COMPANY_ID, AGENT_ID)).thenReturn(agent);
        when(aiAgentUseCase.requirePublishedAgent(COMPANY_ID, AGENT_ID)).thenReturn(agent);

        service = serviceWith(new OutboxProperties(true, 5, 3, 20, 30, 120));
    }

    @Test
    void nothingIsClaimedWhenTheOutboxIsOff() {
        service = serviceWith(new OutboxProperties(false, 5, 3, 20, 30, 120));

        service.sweep();

        verifyNoInteractions(clusterJobLock, repository);
    }

    @Test
    void sweepIsSkippedWhileAnotherInstanceHoldsTheLock() {
        when(clusterJobLock.runExclusively(anyString(), any())).thenReturn(false);

        service.sweep();

        verifyNoInteractions(repository, summaryService);
    }

    @Test
    void claimLooksBackADayAndHidesEachCallForOneInterval() {
        service.sweep();

        verify(repository).claimAttemptsAwaitingSummary(3, NOW.minus(Duration.ofDays(1)), NOW,
                NOW.plus(Duration.ofMinutes(5)), 20);
    }

    @Test
    void failedClaimEndsTheSweepQuietly() {
        when(repository.claimAttemptsAwaitingSummary(anyInt(), any(), any(), any(), anyInt()))
                .thenThrow(new IllegalStateException("db down"));

        service.sweep();

        verifyNoInteractions(summaryService, callFinalizer);
    }

    @Test
    void retriedSummaryGetsEverythingTheFirstPassWouldHaveGiven() {
        claim(pending(Disposition.REFUSED));
        SummarizedCall summarized = summarizes(REFUSAL);

        service.sweep();

        verify(summaryService).summarize(TRANSCRIPT, scenario.definition(), agent);
        verify(callFinalizer).applySummary(COMPANY_ID, CALL_ID, LEAD_ID, PHONE, agent, scenario, Disposition.REFUSED,
                REFUSAL, false);
        verify(callFinalizer).chargeSummary(COMPANY_ID, CALL_ID, summarized);
        verify(callRecordService, never()).updateDisposition(anyLong(), any());
        verifyNoInteractions(campaignTargetUseCase);
    }

    @Test
    void transferredCallIsStoredAsEscalated() {
        claim(pending(Disposition.TRANSFERRED));
        summarizes(REFUSAL);

        service.sweep();

        verify(callFinalizer).applySummary(COMPANY_ID, CALL_ID, LEAD_ID, PHONE, agent, scenario,
                Disposition.TRANSFERRED, REFUSAL, true);
    }

    /** A debtor who promised to pay must not stay on the retry list because the first summary failed. */
    @Test
    void promiseFoundOnRetryBecomesTheOutcomeAndSettlesTheTarget() {
        claim(pending(null));
        summarizes(PROMISE);

        service.sweep();

        verify(callRecordService).updateDisposition(CALL_ID, Disposition.PROMISE_TO_PAY);
        verify(campaignTargetUseCase).applyOutcome(COMPANY_ID, TARGET_ID, Disposition.PROMISE_TO_PAY);
        verify(callFinalizer).applySummary(COMPANY_ID, CALL_ID, LEAD_ID, PHONE, agent, scenario,
                Disposition.PROMISE_TO_PAY, PROMISE, false);
    }

    @Test
    void promiseOnACallNoCampaignPlacedIsRecordedOnTheCallOnly() {
        when(callRecordService.findTargetIdByAttemptId(CALL_ID)).thenReturn(CallAttempt.NO_TARGET);
        claim(pending(null));
        summarizes(PROMISE);

        service.sweep();

        verify(callRecordService).updateDisposition(CALL_ID, Disposition.PROMISE_TO_PAY);
        verifyNoInteractions(campaignTargetUseCase);
    }

    @Test
    void callWhoseSummaryIsStillUnavailableIsLeftForTheNextSweep() {
        claim(pending(Disposition.REFUSED));
        when(summaryService.summarize(anyString(), any(), any())).thenReturn(null);

        service.sweep();

        verify(callFinalizer, never()).applySummary(anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any(),
                anyBoolean());
        verify(callFinalizer, never()).chargeSummary(anyLong(), anyLong(), any());
    }

    @Test
    void callWhoseAgentIsGoneIsSummarizedWithoutOne() {
        when(aiAgentUseCase.findAgent(COMPANY_ID, AGENT_ID)).thenReturn(null);
        claim(pending(Disposition.REFUSED));
        summarizes(REFUSAL);

        service.sweep();

        verify(aiAgentUseCase, never()).requirePublishedAgent(anyLong(), anyLong());
        verify(summaryService).summarize(TRANSCRIPT, scenario.definition(), null);
        verify(callFinalizer).applySummary(COMPANY_ID, CALL_ID, LEAD_ID, PHONE, null, scenario, Disposition.REFUSED,
                REFUSAL, false);
    }

    @Test
    void oneCallFailingDoesNotStopTheRestOfTheBatch() {
        when(callRecordService.findCompanyIdByAttemptId(56L)).thenThrow(new IllegalStateException("db down"));
        claim(new PendingSummary(56L, LEAD_ID, SCENARIO_ID, Disposition.REFUSED, AGENT_ID), pending(Disposition.REFUSED));
        summarizes(REFUSAL);

        service.sweep();

        verify(callFinalizer, never()).applySummary(anyLong(), eq(56L), anyLong(), any(), any(), any(), any(), any(),
                anyBoolean());
        verify(callFinalizer).applySummary(COMPANY_ID, CALL_ID, LEAD_ID, PHONE, agent, scenario, Disposition.REFUSED,
                REFUSAL, false);
    }

    private CallOutboxService serviceWith(OutboxProperties outboxProperties) {
        return new CallOutboxService(repository, aiAgentUseCase, callRecordService, summaryService,
                scenarioUseCase, callFinalizer, campaignTargetUseCase, clusterJobLock, outboxProperties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void claim(PendingSummary... pending) {
        when(repository.claimAttemptsAwaitingSummary(anyInt(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(pending));
    }

    private SummarizedCall summarizes(CallSummary summary) {
        SummarizedCall summarized = new SummarizedCall(summary, 800, 200, 0);
        when(summaryService.summarize(eq(TRANSCRIPT), any(), any())).thenReturn(summarized);
        return summarized;
    }

    private static PendingSummary pending(Disposition disposition) {
        return new PendingSummary(CALL_ID, LEAD_ID, SCENARIO_ID, disposition, AGENT_ID);
    }
}
