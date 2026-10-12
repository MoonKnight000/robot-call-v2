package uz.murodjon.robotcallv2.callrecord.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallAttemptRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallResultRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallTalkMetricsRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallTechnicalRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallTranscriptRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.OutboxMessageRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallAttempt;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallResult;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallTranscript;
import uz.murodjon.robotcallv2.callrecord.domain.entity.OutboxMessage;
import uz.murodjon.robotcallv2.callrecord.domain.enums.OutboxMessageKind;
import uz.murodjon.robotcallv2.company.infrastructure.config.CompanyProperties;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.dialog.ReasonCode;
import uz.murodjon.robotcallv2.shared.dialog.Sentiment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CallRecordServiceTest {

    private static final long COMPANY_ID = 7L;
    /** {@link CompanyProperties}' own default. */
    private static final long DEFAULT_COMPANY_ID = 1L;
    private static final long CALL_ID = 55L;
    private static final long TARGET_ID = 21L;
    private static final String CHANNEL = "PJSIP/trunk-00000001";
    private static final String PHONE = "+998901234567";

    private CallAttemptRepository callAttemptRepository;
    private CallTranscriptRepository callTranscriptRepository;
    private CallResultRepository callResultRepository;
    private OutboxMessageRepository outboxMessageRepository;
    private CallRecordService service;

    @BeforeEach
    void setUp() {
        callAttemptRepository = mock(CallAttemptRepository.class);
        callTranscriptRepository = mock(CallTranscriptRepository.class);
        callResultRepository = mock(CallResultRepository.class);
        outboxMessageRepository = mock(OutboxMessageRepository.class);
        service = new CallRecordService(callAttemptRepository, callTranscriptRepository, callResultRepository,
                mock(CallTechnicalRepository.class), mock(CallTalkMetricsRepository.class), new CompanyProperties(),
                outboxMessageRepository, new ObjectMapper(), mock(ApplicationEventPublisher.class));
    }

    // --- attempt lifecycle

    @Test
    void dialledCallIsOpenedBeforeAnyoneAnswers() {
        when(callAttemptRepository.create(any())).thenReturn(CALL_ID);

        assertEquals(CALL_ID, service.startAttempt(COMPANY_ID, TARGET_ID, CHANNEL, PHONE, "uz-UZ", null, 3L));

        CallAttempt opened = createdAttempt();
        assertEquals(COMPANY_ID, opened.companyId());
        assertEquals(TARGET_ID, opened.targetId());
        assertEquals(CHANNEL, opened.asteriskChannel());
        assertEquals(PHONE, opened.phone());
        assertEquals(3L, opened.variantId());
        assertNull(opened.answeredAt());
        assertNull(opened.endedAt());
        assertNull(opened.disposition());
    }

    @Test
    void attemptThatCannotBeOpenedIsZeroRatherThanAFailedCall() {
        when(callAttemptRepository.create(any())).thenThrow(new IllegalStateException("db down"));

        assertEquals(0L, service.startAttempt(COMPANY_ID, TARGET_ID, CHANNEL, PHONE, "uz-UZ", null, null));
    }

    @Test
    void unplacedAttemptIsWrittenClosedWithItsReasonAndNoDuration() {
        when(callAttemptRepository.create(any())).thenReturn(CALL_ID);

        assertEquals(CALL_ID, service.recordUnplacedAttempt(COMPANY_ID, TARGET_ID, PHONE, "uz-UZ",
                Disposition.FAILED, "originate failed"));

        CallAttempt unplaced = createdAttempt();
        assertEquals(COMPANY_ID, unplaced.companyId());
        assertEquals(Disposition.FAILED, unplaced.disposition());
        assertEquals("originate failed", unplaced.errorMessage());
        assertNull(unplaced.durationSec(), "a call that never reached the trunk has no duration");
        assertNull(unplaced.asteriskChannel());
        assertNull(unplaced.answeredAt());
    }

    @Test
    void answerIsStampedOnARealAttemptOnly() {
        service.markAnswered(0L);
        verifyNoInteractions(callAttemptRepository);

        service.markAnswered(CALL_ID);
        verify(callAttemptRepository).markAnswered(eq(CALL_ID), any(Instant.class));
    }

    @Test
    void finishedCallIsClosedWithItsOutcomeRecordingAndDuration() {
        service.finishAttempt(CALL_ID, Disposition.REFUSED, 9L, 42);

        verify(callAttemptRepository).finishAttempt(eq(CALL_ID), any(Instant.class), eq(42), eq(Disposition.REFUSED),
                eq(9L));
    }

    @Test
    void lifecycleWritesThatFailDoNotReachTheCall() {
        doThrow(new IllegalStateException("db down")).when(callAttemptRepository).markAnswered(anyLong(), any());
        doThrow(new IllegalStateException("db down")).when(callAttemptRepository)
                .finishUnanswered(anyString(), any(), any());
        doThrow(new IllegalStateException("db down")).when(callAttemptRepository)
                .finishAttempt(anyLong(), any(), anyInt(), any(), any());

        assertDoesNotThrow(() -> {
            service.markAnswered(CALL_ID);
            service.finishUnansweredAttempt(CHANNEL, Disposition.NO_ANSWER);
            service.finishAttempt(CALL_ID, Disposition.HUNG_UP, null, 10);
        });
        verify(callAttemptRepository).finishUnanswered(eq(CHANNEL), any(Instant.class), eq(Disposition.NO_ANSWER));
    }

    // --- transcript

    @Test
    void transcriptLinesAreNumberedPerCall() {
        service.addTranscript(CALL_ID, "AGENT", "Assalomu alaykum", "GREETING", 0, null);
        service.addTranscript(56L, "AGENT", "Salom", "GREETING", 0, null);
        service.addTranscript(CALL_ID, "CLIENT", "Va alaykum assalom", "GREETING", 1500, 0.93f);

        ArgumentCaptor<CallTranscript> saved = ArgumentCaptor.forClass(CallTranscript.class);
        verify(callTranscriptRepository, times(3)).save(saved.capture());
        List<CallTranscript> lines = saved.getAllValues();
        assertEquals(List.of(CALL_ID, 56L, CALL_ID), lines.stream().map(CallTranscript::callId).toList());
        assertEquals(List.of(1, 1, 2), lines.stream().map(CallTranscript::seq).toList());
        assertEquals(0.93f, lines.get(2).sttConfidence());
    }

    @Test
    void blankLineIsNotStoredAndTakesNoNumber() {
        service.addTranscript(CALL_ID, "CLIENT", "   ", "GREETING", 0, null);
        service.addTranscript(0L, "CLIENT", "Ha", "GREETING", 0, null);
        service.addTranscript(CALL_ID, "CLIENT", "Ha", "GREETING", 0, null);

        ArgumentCaptor<CallTranscript> saved = ArgumentCaptor.forClass(CallTranscript.class);
        verify(callTranscriptRepository).save(saved.capture());
        assertEquals(1, saved.getValue().seq());
    }

    @Test
    void transcriptTextIsTheLinesInOrderAndEmptyWhenUnreadable() {
        when(callTranscriptRepository.findTranscriptLines(CALL_ID)).thenReturn(List.of("AGENT: Salom", "CLIENT: Ha"));
        assertEquals("AGENT: Salom\nCLIENT: Ha\n", service.findTranscriptText(CALL_ID));

        when(callTranscriptRepository.findTranscriptLines(CALL_ID)).thenThrow(new IllegalStateException("db down"));
        assertEquals("", service.findTranscriptText(CALL_ID));
    }

    // --- lookups by attempt id

    @Test
    void attemptBelongsToItsOwnCompanyAndOnlyAMissingOneFallsBackToTheDefault() {
        when(callAttemptRepository.findCompanyIdById(CALL_ID)).thenReturn(COMPANY_ID);
        when(callAttemptRepository.findCompanyIdById(56L)).thenReturn(null);

        assertEquals(COMPANY_ID, service.findCompanyIdByAttemptId(CALL_ID));
        assertEquals(DEFAULT_COMPANY_ID, service.findCompanyIdByAttemptId(56L));
        assertEquals(DEFAULT_COMPANY_ID, service.findCompanyIdByAttemptId(0L));
    }

    /** Guessing the default company on a database error filed one tenant's call under another. */
    @Test
    void unreadableCompanyIsAnErrorRatherThanTheDefaultCompany() {
        when(callAttemptRepository.findCompanyIdById(CALL_ID)).thenThrow(new IllegalStateException("db down"));

        assertThrows(IllegalStateException.class, () -> service.findCompanyIdByAttemptId(CALL_ID));
    }

    @Test
    void phoneOfAnAttemptIsNullWhenThereIsNoneOrItCannotBeRead() {
        when(callAttemptRepository.findPhoneById(CALL_ID)).thenReturn(PHONE);
        assertEquals(PHONE, service.findPhoneByAttemptId(CALL_ID));
        assertNull(service.findPhoneByAttemptId(0L));

        when(callAttemptRepository.findPhoneById(CALL_ID)).thenThrow(new IllegalStateException("db down"));
        assertNull(service.findPhoneByAttemptId(CALL_ID));
        verify(callAttemptRepository, never()).findPhoneById(0L);
    }

    // --- result

    @Test
    void resultIsStoredWithItsOutcomeReadAndItsNoteQueuedWithIt() {
        OutboxMessage note = OutboxMessage.pending(OutboxMessageKind.CRM_NOTE, CALL_ID,
                Map.of("leadId", 42L, "noteText", "note"));
        when(outboxMessageRepository.enqueue(COMPANY_ID, note)).thenReturn(9L);
        CallSummary summary = new CallSummary("Mijoz 20-sentabrda to'laydi.", Map.of("reasonCode", " no_money ",
                "promisedDate", "2026-09-20", "promisedAmount", 1500000), Sentiment.NEUTRAL, true, "Eslatish", null);

        assertEquals(List.of(9L), service.writeResult(COMPANY_ID, CALL_ID, summary, true, List.of(note)));

        CallResult stored = storedResult();
        assertEquals(CALL_ID, stored.callId());
        assertEquals("Mijoz 20-sentabrda to'laydi.", stored.summary());
        assertEquals(ReasonCode.NO_MONEY, stored.reasonCode());
        assertEquals(LocalDate.parse("2026-09-20"), stored.promisedDate());
        assertEquals(new BigDecimal("1500000"), stored.promisedAmount());
        assertTrue(stored.escalated());
        assertTrue(stored.needsFollowUp());
        assertTrue(stored.outcome().contains("\"promisedDate\":\"2026-09-20\""), stored.outcome());
    }

    @Test
    void outcomeTheModelGotWrongIsDroppedRatherThanStored() {
        CallSummary summary = new CallSummary(null, Map.of("reasonCode", "MAYBE", "promisedDate", "ertaga",
                "promisedAmount", "ko'p"), null, false, null, null);

        assertEquals(List.of(), service.writeResult(COMPANY_ID, CALL_ID, summary, false, List.of()));

        CallResult stored = storedResult();
        assertEquals("", stored.summary());
        assertNull(stored.reasonCode());
        assertNull(stored.promisedDate());
        assertNull(stored.promisedAmount());
        verifyNoInteractions(outboxMessageRepository);
    }

    /** Unlike the other writes here this one is not fail-open: no note is queued for a result that was not stored. */
    @Test
    void resultThatCannotBeStoredReachesTheCallerAndQueuesNoNote() {
        doThrow(new IllegalStateException("db down")).when(callResultRepository).insertIgnoringConflict(any());
        OutboxMessage note = OutboxMessage.pending(OutboxMessageKind.CRM_NOTE, CALL_ID, Map.of());
        CallSummary summary = new CallSummary("Mijoz rad etdi.", Map.of(), null, false, null, null);

        assertThrows(IllegalStateException.class,
                () -> service.writeResult(COMPANY_ID, CALL_ID, summary, false, List.of(note)));
        verifyNoInteractions(outboxMessageRepository);
    }

    private CallAttempt createdAttempt() {
        ArgumentCaptor<CallAttempt> created = ArgumentCaptor.forClass(CallAttempt.class);
        verify(callAttemptRepository).create(created.capture());
        return created.getValue();
    }

    private CallResult storedResult() {
        ArgumentCaptor<CallResult> stored = ArgumentCaptor.forClass(CallResult.class);
        verify(callResultRepository).insertIgnoringConflict(stored.capture());
        return stored.getValue();
    }
}
