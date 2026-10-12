package uz.murodjon.robotcallv2.callrecord.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;
import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.agent.dialog.DialogTechnicalSnapshot;
import uz.murodjon.robotcallv2.agent.metrics.CallMetrics;
import uz.murodjon.robotcallv2.agent.stt.SttProperties;
import uz.murodjon.robotcallv2.agent.summary.SummarizedCall;
import uz.murodjon.robotcallv2.agent.summary.SummaryService;
import uz.murodjon.robotcallv2.agent.tts.TtsProperties;
import uz.murodjon.robotcallv2.agent.vad.VadProperties;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentDataPolicy;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.billing.domain.entity.CallUsage;
import uz.murodjon.robotcallv2.callrecord.application.port.output.RecordingStoragePort;
import uz.murodjon.robotcallv2.callrecord.domain.entity.OutboxMessage;
import uz.murodjon.robotcallv2.callrecord.domain.enums.OutboxMessageKind;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmCallHistoryEntry;
import uz.murodjon.robotcallv2.inbound.application.port.input.InboundRouteUseCase;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.quality.application.port.input.CallQualityUseCase;
import uz.murodjon.robotcallv2.quality.application.port.input.ScorecardUseCase;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.dialog.Sentiment;
import uz.murodjon.robotcallv2.voice.application.port.input.TtsVoiceUseCase;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CallFinalizerTest {

    private static final long COMPANY_ID = 7L;
    private static final long CALL_ID = 55L;
    private static final long LEAD_ID = 42L;
    private static final long SCENARIO_ID = 3L;
    private static final long TARGET_ID = 21L;
    private static final long RECORDING_ID = 9L;
    private static final long NOTE_MESSAGE_ID = 11L;
    private static final String PHONE = "+998901234567";
    private static final String CHANNEL = "PJSIP/trunk-00000001";
    private static final String TRANSCRIPT = "AGENT: Salom\nCLIENT: Ertaga to'layman\n";
    private static final Path WAV = Path.of("call-55.wav");
    private static final DialogTechnicalSnapshot TECHNICAL = new DialogTechnicalSnapshot(4, 1000, 200, 50, 300,
            null, null, null, null, null, "uz-UZ", null, null, 0, 0, 0);
    private static final CallSummary SUMMARY = new CallSummary("Ali Valiyev maoshdan keyin to'lashini aytdi.",
            Map.of("reasonCode", "NO_MONEY", "client_name", "Ali Valiyev"), Sentiment.NEUTRAL, false, null, null);

    private CallRecordService callRecordService;
    private SummaryService summaryService;
    private RecordingStoragePort recordingStoragePort;
    private OutboxDeliveryService outboxDeliveryService;
    private CampaignTargetUseCase campaignTargetUseCase;
    private NotificationUseCase notificationUseCase;
    private CallMemoryWriter memoryWriter;
    private PostCallActionExecutor postCallActionExecutor;
    private CallBillingUseCase callBilling;
    private CompanyConfigUseCase companyConfigUseCase;
    private Scenario scenario;
    private OutboxMessage crmNote;
    private Instant startedAt;
    private CallFinalizer finalizer;

    @BeforeEach
    void setUp() {
        callRecordService = mock(CallRecordService.class);
        summaryService = mock(SummaryService.class);
        recordingStoragePort = mock(RecordingStoragePort.class);
        outboxDeliveryService = mock(OutboxDeliveryService.class);
        ScenarioUseCase scenarioUseCase = mock(ScenarioUseCase.class);
        campaignTargetUseCase = mock(CampaignTargetUseCase.class);
        notificationUseCase = mock(NotificationUseCase.class);
        memoryWriter = mock(CallMemoryWriter.class);
        postCallActionExecutor = mock(PostCallActionExecutor.class);
        callBilling = mock(CallBillingUseCase.class);
        companyConfigUseCase = mock(CompanyConfigUseCase.class);
        scenario = new Scenario(SCENARIO_ID, "debt", 1, "Qarz", null, false, true, mock(ScenarioDefinition.class),
                Instant.now(), null);
        crmNote = OutboxMessage.pending(OutboxMessageKind.CRM_NOTE, CALL_ID,
                Map.of("leadId", LEAD_ID, "noteText", "note"));
        startedAt = Instant.now().minusSeconds(95);

        when(callRecordService.findCompanyIdByAttemptId(CALL_ID)).thenReturn(COMPANY_ID);
        when(callRecordService.findTranscriptText(CALL_ID)).thenReturn(TRANSCRIPT);
        when(callRecordService.findTargetIdByAttemptId(CALL_ID)).thenReturn(TARGET_ID);
        when(scenarioUseCase.requireScenario(COMPANY_ID, SCENARIO_ID)).thenReturn(scenario);
        when(recordingStoragePort.uploadRecording(eq(WAV), eq(COMPANY_ID), anyString())).thenReturn(RECORDING_ID);
        when(outboxDeliveryService.prepareCrmNote(eq(COMPANY_ID), eq(CALL_ID), eq(LEAD_ID), any(), any())).thenReturn(crmNote);
        when(callRecordService.writeResult(eq(COMPANY_ID), eq(CALL_ID), any(), anyBoolean(), any()))
                .thenReturn(List.of(NOTE_MESSAGE_ID));

        finalizer = new CallFinalizer(callRecordService, summaryService, mock(CallQualityUseCase.class),
                mock(ScorecardUseCase.class),
                recordingStoragePort, outboxDeliveryService, scenarioUseCase, campaignTargetUseCase,
                notificationUseCase, mock(CallMetrics.class), mock(SttProperties.class), mock(TtsProperties.class),
                mock(TtsVoiceUseCase.class), mock(VadProperties.class), memoryWriter, postCallActionExecutor,
                callBilling, mock(InboundRouteUseCase.class), companyConfigUseCase, "gemini-2.5-flash");
    }

    // --- the summary and what it sets off

    @Test
    void summaryIsStoredAndItsNoteDeliveredRightAfter() {
        summarizes(SUMMARY);
        AiAgent agent = agent();

        assertEquals(Disposition.REFUSED, finalizeCall(Disposition.REFUSED, agent));

        InOrder order = inOrder(callRecordService, outboxDeliveryService);
        order.verify(callRecordService).writeResult(COMPANY_ID, CALL_ID, SUMMARY, false, List.of(crmNote));
        order.verify(outboxDeliveryService).deliverNow(NOTE_MESSAGE_ID);
        verify(memoryWriter).remember(CALL_ID, COMPANY_ID, scenario, Disposition.REFUSED, SUMMARY);
        verify(postCallActionExecutor).executeActions(COMPANY_ID, CALL_ID, PHONE, agent, Disposition.REFUSED, SUMMARY);
    }

    @Test
    void transferredCallIsStoredAsEscalated() {
        summarizes(SUMMARY);

        finalizeCall(Disposition.TRANSFERRED, agent());

        verify(callRecordService).writeResult(COMPANY_ID, CALL_ID, SUMMARY, true, List.of(crmNote));
    }

    @Test
    void recordingIsStoredUnderTheCallsCompanyAndClosesTheAttempt() {
        finalizeCall(Disposition.REFUSED, agent());

        verify(recordingStoragePort).uploadRecording(WAV, COMPANY_ID, "call-55.wav");
        verify(recordingStoragePort).deleteLocalCopy(WAV);
        verify(callRecordService).finishAttempt(eq(CALL_ID), eq(Disposition.REFUSED), eq(RECORDING_ID),
                intThat(seconds -> seconds >= 95));
    }

    /** The company's own CRM and post-call actions are where the words were meant to go; storage keeps none. */
    @Test
    void zeroPiiAgentsCallStoresTheOutcomeWithoutTheWordsWhileItsCrmGetsTheWholeSummary() {
        summarizes(SUMMARY);
        AiAgent agent = zeroPiiAgent();

        finalizeCall(Disposition.REFUSED, agent);

        CallSummary stripped = SUMMARY.stripPersonalData(null);
        verify(callRecordService).writeResult(COMPANY_ID, CALL_ID, stripped, false, List.of(crmNote));
        verify(memoryWriter).remember(CALL_ID, COMPANY_ID, scenario, Disposition.REFUSED, stripped);
        verify(outboxDeliveryService).prepareCrmNote(eq(COMPANY_ID), eq(CALL_ID), eq(LEAD_ID), eq(SUMMARY), any());
        verify(postCallActionExecutor).executeActions(COMPANY_ID, CALL_ID, PHONE, agent, Disposition.REFUSED, SUMMARY);
    }

    @Test
    void hostileCallerAlertCarriesOnlyWhatTheCallMayKeep() {
        summarizes(new CallSummary("Ali Valiyev qo'pol gapirdi.", Map.of(), Sentiment.HOSTILE, false, null, null));

        finalizeCall(Disposition.HUNG_UP, zeroPiiAgent());

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationUseCase).notify(eq(COMPANY_ID), eq(NotificationType.OPERATOR_REQUEST), anyString(),
                message.capture(), isNull());
        assertFalse(message.getValue().contains("Ali"), message.getValue());
    }

    @Test
    void callWithNoNoteQueuedDeliversNothing() {
        summarizes(SUMMARY);
        when(outboxDeliveryService.prepareCrmNote(anyLong(), anyLong(), anyLong(), any(), any())).thenReturn(null);
        when(callRecordService.writeResult(eq(COMPANY_ID), eq(CALL_ID), any(), anyBoolean(), isNull()))
                .thenReturn(null);

        finalizeCall(Disposition.REFUSED, agent());

        verify(callRecordService).writeResult(COMPANY_ID, CALL_ID, SUMMARY, false, null);
        verify(outboxDeliveryService, never()).deliverNow(anyLong());
    }

    @Test
    void promiseTheSummaryFoundBecomesTheOutcomeOfACallThatHadNone() {
        summarizes(new CallSummary("Mijoz 20-sentabrda to'laydi.", Map.of("promisedDate", "2026-09-20"),
                Sentiment.POSITIVE, false, null, null));

        assertEquals(Disposition.PROMISE_TO_PAY, finalizeCall(null, agent()));

        verify(callRecordService).finishAttempt(eq(CALL_ID), eq(Disposition.PROMISE_TO_PAY), eq(RECORDING_ID),
                anyInt());
    }

    @Test
    void callbackAskedForIsReadInTheCompanysClockAndReschedulesTheTarget() {
        CompanyConfig config = mock(CompanyConfig.class);
        when(config.zoneId()).thenReturn(ZoneOffset.ofHours(5));
        when(companyConfigUseCase.find(COMPANY_ID)).thenReturn(config);
        summarizes(new CallSummary("Ertaga soat 10 da qo'ng'iroq qiling.", Map.of(), Sentiment.NEUTRAL, false, null,
                "2099-01-02T10:00:00"));

        finalizeCall(Disposition.CALLBACK_REQUESTED, agent());

        verify(campaignTargetUseCase).scheduleCallback(COMPANY_ID, TARGET_ID, Instant.parse("2099-01-02T05:00:00Z"));
    }

    // --- steps that must happen whatever else failed

    @Test
    void callHistoryIsQueuedEvenWhenNoSummaryCouldBeMade() {
        when(summaryService.summarize(anyLong(), anyString(), any(), any())).thenThrow(new IllegalStateException("LLM down"));

        finalizeCall(Disposition.REFUSED, agent());

        CrmCallHistoryEntry entry = queuedCallHistory();
        assertEquals(LEAD_ID, entry.leadId());
        assertEquals("voice-55", entry.callUuid());
        assertEquals(startedAt, entry.startedAt());
        assertTrue(entry.answered());
        assertEquals(PHONE, entry.clientPhone());
        assertNull(entry.recordingUrl(), "the recording bucket is not reachable from the CRM");
        verify(callRecordService, never()).writeResult(anyLong(), anyLong(), any(), anyBoolean(), any());
        verify(callBilling).settleCall(eq(COMPANY_ID), eq(CALL_ID), eq(TARGET_ID), any());
        verify(callBilling, never()).chargeSummary(anyLong(), anyLong(), any());
    }

    @Test
    void callNoAgentRanIsFiledUnansweredWithoutASummary() {
        finalizer.finalizeCall(CALL_ID, LEAD_ID, 0L, WAV, startedAt, Disposition.NO_ANSWER, CHANNEL, "trunk",
                null, null, PHONE, null);

        verifyNoInteractions(summaryService);
        assertFalse(queuedCallHistory().answered());
        verify(callRecordService, never()).writeResult(anyLong(), anyLong(), any(), anyBoolean(), any());
        verify(callBilling).settleCall(eq(COMPANY_ID), eq(CALL_ID), eq(TARGET_ID), any());
    }

    @Test
    void failedResultWriteDoesNotStopTheRestOfThePostCallWork() {
        summarizes(SUMMARY);
        when(callRecordService.writeResult(anyLong(), anyLong(), any(), anyBoolean(), any()))
                .thenThrow(new IllegalStateException("db down"));
        AiAgent agent = agent();

        finalizeCall(Disposition.REFUSED, agent);

        verify(outboxDeliveryService, never()).deliverNow(anyLong());
        verify(memoryWriter).remember(CALL_ID, COMPANY_ID, scenario, Disposition.REFUSED, SUMMARY);
        verify(postCallActionExecutor).executeActions(COMPANY_ID, CALL_ID, PHONE, agent, Disposition.REFUSED, SUMMARY);
        verify(callBilling).settleCall(eq(COMPANY_ID), eq(CALL_ID), eq(TARGET_ID), any());
        verify(callBilling).chargeSummary(eq(COMPANY_ID), eq(CALL_ID), any());
    }

    @Test
    void failedUploadAndUnqueuedCallHistoryDoNotStopTheRest() {
        summarizes(SUMMARY);
        when(recordingStoragePort.uploadRecording(any(), anyLong(), anyString()))
                .thenThrow(new IllegalStateException("minio down"));
        doThrow(new IllegalStateException("db down")).when(outboxDeliveryService)
                .enqueueCallHistory(anyLong(), anyLong(), any());

        finalizeCall(Disposition.REFUSED, agent());

        verify(callRecordService).finishAttempt(eq(CALL_ID), eq(Disposition.REFUSED), isNull(), anyInt());
        verify(recordingStoragePort, never()).deleteLocalCopy(any());
        verify(callRecordService).writeResult(COMPANY_ID, CALL_ID, SUMMARY, false, List.of(crmNote));
        verify(callBilling).settleCall(eq(COMPANY_ID), eq(CALL_ID), eq(TARGET_ID), any());
    }

    @Test
    void callIsSettledOnWhatItUsedAndItsSummaryChargedAfter() {
        summarizes(SUMMARY);

        finalizeCall(Disposition.REFUSED, agent());

        InOrder order = inOrder(callBilling);
        ArgumentCaptor<CallUsage> usage = ArgumentCaptor.forClass(CallUsage.class);
        order.verify(callBilling).settleCall(eq(COMPANY_ID), eq(CALL_ID), eq(TARGET_ID), usage.capture());
        order.verify(callBilling).chargeSummary(COMPANY_ID, CALL_ID, new CallUsage(0, 1200, 300, 100, 0));
        assertEquals(1000, usage.getValue().promptTokens());
        assertEquals(300, usage.getValue().ttsChars());
        assertFalse(usage.getValue().realtime());
        assertTrue(usage.getValue().durationSec() >= 95);
    }

    @Test
    void onTheCompanysOwnLlmNeitherTheCallNorItsSummaryIsBilledForTokens() {
        when(summaryService.summarize(anyLong(), eq(TRANSCRIPT), any(), any()))
                .thenReturn(new SummarizedCall(SUMMARY, 1200, 300, 100, true));
        DialogTechnicalSnapshot ownLlm = new DialogTechnicalSnapshot(4, 1000, 200, 50, 300,
                null, null, null, null, null, "uz-UZ", null, "qwen3-32b", 0, 0, 0, true);

        finalizer.finalizeCall(CALL_ID, LEAD_ID, SCENARIO_ID, WAV, startedAt, Disposition.REFUSED, CHANNEL, "trunk",
                ownLlm, agent(), PHONE, null);

        ArgumentCaptor<CallUsage> usage = ArgumentCaptor.forClass(CallUsage.class);
        verify(callBilling).settleCall(eq(COMPANY_ID), eq(CALL_ID), eq(TARGET_ID), usage.capture());
        verify(callBilling).chargeSummary(COMPANY_ID, CALL_ID, new CallUsage(0, 0, 0, 0, 0));
        assertEquals(0, usage.getValue().promptTokens());
        assertEquals(0, usage.getValue().completionTokens());
        assertEquals(300, usage.getValue().ttsChars());
    }

    private Disposition finalizeCall(Disposition disposition, AiAgent agent) {
        return finalizer.finalizeCall(CALL_ID, LEAD_ID, SCENARIO_ID, WAV, startedAt, disposition, CHANNEL, "trunk",
                TECHNICAL, agent, PHONE, null);
    }

    private void summarizes(CallSummary summary) {
        when(summaryService.summarize(anyLong(), eq(TRANSCRIPT), any(), any()))
                .thenReturn(new SummarizedCall(summary, 1200, 300, 100));
    }

    private CrmCallHistoryEntry queuedCallHistory() {
        ArgumentCaptor<CrmCallHistoryEntry> entry = ArgumentCaptor.forClass(CrmCallHistoryEntry.class);
        verify(outboxDeliveryService).enqueueCallHistory(eq(COMPANY_ID), eq(CALL_ID), entry.capture());
        return entry.getValue();
    }

    private static AiAgent agent() {
        return AiAgentFixtures.agent(5L, COMPANY_ID, SCENARIO_ID, "uz-UZ", null);
    }

    private static AiAgent zeroPiiAgent() {
        AiAgent agent = agent();
        return agent.withAdvanced(agent.ambience(), agent.callBehaviour(), new AiAgentDataPolicy(true, false, null, null),
                agent.voice(), agent.preemptiveGeneration(), agent.ivrNavigationEnabled(), agent.useRag(),
                agent.initiationWebhook(), agent.postCallWebhook());
    }
}
