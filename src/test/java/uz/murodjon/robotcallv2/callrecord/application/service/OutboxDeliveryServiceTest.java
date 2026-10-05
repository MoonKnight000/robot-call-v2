package uz.murodjon.robotcallv2.callrecord.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallResultRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.OutboxMessageRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.OutboxMessage;
import uz.murodjon.robotcallv2.callrecord.domain.enums.OutboxMessageKind;
import uz.murodjon.robotcallv2.callrecord.domain.enums.OutboxMessageStatus;
import uz.murodjon.robotcallv2.callrecord.infrastructure.config.OutboxProperties;
import uz.murodjon.robotcallv2.crm.application.dto.CreateCrmTaskRequest;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmUseCase;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmCallHistoryEntry;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxDeliveryServiceTest {

    private static final long COMPANY_ID = 7L;
    private static final long CALL_ID = 55L;
    private static final long LEAD_ID = 42L;
    private static final int MAX_ATTEMPTS = 3;
    private static final Instant NOW = Instant.parse("2026-09-14T10:00:00Z");
    private static final Instant LEASE_END = NOW.plus(Duration.ofMinutes(5));

    private OutboxMessageRepository outboxMessageRepository;
    private CallResultRepository callResultRepository;
    private CrmUseCase crmClient;
    private NotificationUseCase notificationService;
    private OutboxDeliveryService service;

    @BeforeEach
    void setUp() {
        outboxMessageRepository = mock(OutboxMessageRepository.class);
        callResultRepository = mock(CallResultRepository.class);
        crmClient = mock(CrmUseCase.class);
        notificationService = mock(NotificationUseCase.class);
        when(crmClient.isConnected(COMPANY_ID)).thenReturn(true);
        when(crmClient.composeNoteText(any(), any())).thenReturn("note");
        when(outboxMessageRepository.update(any())).thenReturn(true);
        service = serviceWith(new OutboxProperties(true, 5, MAX_ATTEMPTS, 20, 30, 120));
    }

    @Test
    void syncLeadStageDelegatesToCrmUseCase() {
        CallSummary meetingSummary = new CallSummary("Shourumga kelishga rozi bo'ldi",
                Map.of("meetingBooked", true), null, false, null, null);

        service.syncLeadStageIfActionable(COMPANY_ID, LEAD_ID, meetingSummary, Disposition.GOAL_REACHED);

        verify(crmClient).syncLeadStage(COMPANY_ID, LEAD_ID, meetingSummary, Disposition.GOAL_REACHED);
    }

    @Test
    void prepareCrmNoteWithRecordingFileIdCallsComposedNote() {
        CallSummary summary = new CallSummary("Suhbat", Map.of(), null, false, null, null);
        when(crmClient.isConnected(COMPANY_ID)).thenReturn(true);
        when(crmClient.composeNoteText(summary, 999L)).thenReturn("Suhbat\nAudio yozuv: /api/files/999");

        OutboxMessage message = service.prepareCrmNote(COMPANY_ID, CALL_ID, LEAD_ID, summary, 999L);

        assertNotNull(message);
        assertEquals("Suhbat\nAudio yozuv: /api/files/999", message.payload().get("noteText"));
        verify(crmClient).composeNoteText(summary, 999L);
    }

    // --- no CRM lead, no row

    @Test
    void callWithoutALeadGetsNoNote() {
        assertNull(service.prepareCrmNote(COMPANY_ID, CALL_ID, 0L, summary(), null));
        verify(crmClient, never()).isConnected(anyLong());
        verify(crmClient, never()).composeNoteText(any(), any());
    }

    @Test
    void companyWithoutACrmGetsNoNote() {
        when(crmClient.isConnected(COMPANY_ID)).thenReturn(false);

        assertNull(service.prepareCrmNote(COMPANY_ID, CALL_ID, LEAD_ID, summary(), null));
    }

    @Test
    void noteIsKeyedByKindAndCall() {
        OutboxMessage note = service.prepareCrmNote(COMPANY_ID, CALL_ID, LEAD_ID, summary(), null);

        assertEquals(OutboxMessageKind.CRM_NOTE, note.kind());
        assertEquals("CRM_NOTE:55", note.idempotencyKey());
        assertEquals(OutboxMessageStatus.PENDING, note.status());
        assertEquals(Map.of("leadId", LEAD_ID, "noteText", "note"), note.payload());
    }

    @Test
    void callWithoutALeadQueuesNoCallHistory() {
        service.enqueueCallHistory(COMPANY_ID, CALL_ID, historyEntry(0L));

        verifyNoInteractions(outboxMessageRepository);
    }

    @Test
    void callHistoryIsQueuedAndDeliveredAtOnce() {
        when(outboxMessageRepository.enqueue(eq(COMPANY_ID), any())).thenReturn(9L);

        service.enqueueCallHistory(COMPANY_ID, CALL_ID, historyEntry(LEAD_ID));

        ArgumentCaptor<OutboxMessage> queued = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).enqueue(eq(COMPANY_ID), queued.capture());
        assertEquals("CRM_CALL_HISTORY:55", queued.getValue().idempotencyKey());
        verify(outboxMessageRepository).claimFresh(9L, LEASE_END);
    }

    @Test
    void callHistoryQueuedBeforeIsNotDeliveredAgain() {
        when(outboxMessageRepository.enqueue(eq(COMPANY_ID), any())).thenReturn(null);

        service.enqueueCallHistory(COMPANY_ID, CALL_ID, historyEntry(LEAD_ID));

        verify(outboxMessageRepository, never()).claimFresh(anyLong(), any());
    }

    @Test
    void messageAWorkerClaimedFirstIsNotDeliveredNow() {
        when(outboxMessageRepository.claimFresh(9L, LEASE_END)).thenReturn(null);

        service.deliverNow(9L);

        verify(crmClient, never()).postNote(anyLong(), anyLong(), any());
        verify(outboxMessageRepository, never()).update(any());
    }

    // --- worker transitions

    @Test
    void acceptedNoteIsSentAndItsIdStored() {
        claimDue(claimedNote(1));
        when(crmClient.postNote(COMPANY_ID, LEAD_ID, "note")).thenReturn("req-1");

        service.deliverDue();

        OutboxMessage written = lastUpdate();
        assertEquals(OutboxMessageStatus.SENT, written.status());
        assertEquals(NOW, written.sentAt());
        assertNull(written.lastError());
        assertTrue(written.payload().isEmpty(), "a sent row keeps no copy of what it carried");
        verify(callResultRepository).markCrmPosted(CALL_ID, "req-1");
        verifyNoInteractions(notificationService);
    }

    @Test
    void refusedWriteIsRetriedWithDoublingBackoff() {
        claimDue(claimedNote(2));
        when(crmClient.postNote(anyLong(), anyLong(), any())).thenReturn(null);

        service.deliverDue();

        OutboxMessage written = lastUpdate();
        assertEquals(OutboxMessageStatus.PENDING, written.status());
        assertEquals(NOW.plusSeconds(240), written.nextAttemptAt()); // 120 s, doubled once
        assertNotNull(written.lastError());
        verify(callResultRepository, never()).markCrmPosted(anyLong(), any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void crmExceptionCountsAsAFailedAttempt() {
        claimDue(claimedNote(1));
        when(crmClient.postNote(anyLong(), anyLong(), any())).thenThrow(new IllegalStateException("boom"));

        service.deliverDue();

        OutboxMessage written = lastUpdate();
        assertEquals(OutboxMessageStatus.PENDING, written.status());
        assertEquals(NOW.plusSeconds(120), written.nextAttemptAt());
        assertEquals("boom", written.lastError());
    }

    @Test
    void lastAttemptFailingMakesTheMessageDeadAndAlerts() {
        claimDue(claimedNote(MAX_ATTEMPTS));
        when(crmClient.postNote(anyLong(), anyLong(), any())).thenReturn(null);

        service.deliverDue();

        assertEquals(OutboxMessageStatus.DEAD, lastUpdate().status());
        verify(notificationService).notify(eq(COMPANY_ID), eq(NotificationType.ERROR_OCCURRED), anyString(),
                anyString(), isNull());
    }

    @Test
    void messageOfACompanyThatLostItsCrmDiesWithoutAnAlert() {
        claimDue(claimedNote(1));
        when(crmClient.isConnected(COMPANY_ID)).thenReturn(false);

        service.deliverDue();

        assertEquals(OutboxMessageStatus.DEAD, lastUpdate().status());
        verify(crmClient, never()).postNote(anyLong(), anyLong(), any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void acceptedCallHistoryIsSent() {
        Instant startedAt = Instant.parse("2026-09-14T09:58:00Z");
        // Numbers come back from the JSON column as Integer when they fit.
        OutboxMessage history = new OutboxMessage(2L, COMPANY_ID, OutboxMessageKind.CRM_CALL_HISTORY, CALL_ID,
                Map.of("leadId", 42, "callUuid", "voice-55", "startedAt", startedAt.toString(),
                        "durationSeconds", 95, "inbound", false, "answered", true, "clientPhone", "+998901234567"),
                "CRM_CALL_HISTORY:55", OutboxMessageStatus.PENDING, 1, LEASE_END, null, NOW, null);
        claimDue(history);
        when(crmClient.postCallHistory(COMPANY_ID, LEAD_ID, "voice-55", startedAt, 95, false, true,
                "+998901234567", null)).thenReturn(true);

        service.deliverDue();

        assertEquals(OutboxMessageStatus.SENT, lastUpdate().status());
        verify(callResultRepository, never()).markCrmPosted(anyLong(), any());
    }

    @Test
    void backoffIsCappedAtAnHour() {
        service = serviceWith(new OutboxProperties(true, 5, 50, 20, 30, 120));
        claimDue(claimedNote(20));
        when(crmClient.postNote(anyLong(), anyLong(), any())).thenReturn(null);

        service.deliverDue();

        assertEquals(NOW.plus(Duration.ofHours(1)), lastUpdate().nextAttemptAt());
    }

    @Test
    void nothingIsRetriedWhenTheOutboxIsOff() {
        service = serviceWith(new OutboxProperties(false, 5, MAX_ATTEMPTS, 20, 30, 120));

        service.deliverDue();

        verifyNoInteractions(outboxMessageRepository);
    }

    @Test
    void meetingBookedCreatesCrmTask() {
        CallSummary meetingSummary = new CallSummary("Shourumga kelishga rozi bo'ldi",
                Map.of("meetingBooked", true, "meetingTime", "2026-09-20 15:00", "roomsNeeded", "3 xonali", "budgetRange", "50000 USD"),
                null, false, null, null);

        service.scheduleCrmTaskIfActionable(COMPANY_ID, LEAD_ID, meetingSummary, Disposition.GOAL_REACHED);

        ArgumentCaptor<CreateCrmTaskRequest> captor = ArgumentCaptor.forClass(CreateCrmTaskRequest.class);
        verify(crmClient).createTask(eq(COMPANY_ID), captor.capture());
        assertEquals(LEAD_ID, captor.getValue().leadId());
        assertTrue(captor.getValue().title().contains("Sotuv ofisiga uchrashuv"));
        assertTrue(captor.getValue().description().contains("3 xonali"));
        assertTrue(captor.getValue().description().contains("2026-09-20 15:00"));
    }

    @Test
    void paymentPromiseCreatesCrmTask() {
        CallSummary promiseSummary = new CallSummary("Ertaga to'laydi",
                Map.of("promisedDate", "2026-09-18", "promisedAmount", "12000000"),
                null, false, null, null);

        service.scheduleCrmTaskIfActionable(COMPANY_ID, LEAD_ID, promiseSummary, Disposition.PROMISE_TO_PAY);

        ArgumentCaptor<CreateCrmTaskRequest> captor = ArgumentCaptor.forClass(CreateCrmTaskRequest.class);
        verify(crmClient).createTask(eq(COMPANY_ID), captor.capture());
        assertEquals(LEAD_ID, captor.getValue().leadId());
        assertTrue(captor.getValue().title().contains("To'lov va'dasi"));
    }

    private OutboxDeliveryService serviceWith(OutboxProperties outboxProperties) {
        return new OutboxDeliveryService(outboxMessageRepository, callResultRepository, crmClient,
                notificationService, outboxProperties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void claimDue(OutboxMessage message) {
        when(outboxMessageRepository.claimDue(eq(NOW), eq(LEASE_END), anyInt())).thenReturn(List.of(message));
    }

    private OutboxMessage lastUpdate() {
        ArgumentCaptor<OutboxMessage> written = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).update(written.capture());
        return written.getValue();
    }

    private static OutboxMessage claimedNote(int attempts) {
        return new OutboxMessage(1L, COMPANY_ID, OutboxMessageKind.CRM_NOTE, CALL_ID,
                Map.of("leadId", LEAD_ID, "noteText", "note"), "CRM_NOTE:55", OutboxMessageStatus.PENDING,
                attempts, LEASE_END, null, NOW, null);
    }

    private static CrmCallHistoryEntry historyEntry(long leadId) {
        return new CrmCallHistoryEntry(leadId, "voice-55", NOW.minusSeconds(95), 95, false, true,
                "+998901234567", null);
    }

    private static CallSummary summary() {
        return new CallSummary("Mijoz ertaga to'lashini aytdi.", Map.of(), null, false, null, null);
    }
}
