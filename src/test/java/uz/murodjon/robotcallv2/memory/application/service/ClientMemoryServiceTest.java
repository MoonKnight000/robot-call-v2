package uz.murodjon.robotcallv2.memory.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.memory.application.dto.UpdateClientMemoryRequest;
import uz.murodjon.robotcallv2.memory.application.port.output.ClientMemoryRepository;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.memory.domain.entity.RememberedCall;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Memory is what the agent hears about a client before the next call, so what gets
 * kept, in which order, and what gets dropped is the behaviour worth pinning down.
 */
class ClientMemoryServiceTest {

    private static final long COMPANY = 7L;
    private static final String PHONE = "+998901234567";

    private ClientMemoryRepository repository;
    private ClientMemoryService service;

    @BeforeEach
    void setUp() {
        repository = mock(ClientMemoryRepository.class);
        when(repository.upsert(eq(COMPANY), any())).thenAnswer(inv -> inv.getArgument(1));
        service = new ClientMemoryService(repository, mock(AuditService.class));
    }

    @Test
    void firstCallCreatesMemoryUnderTheNormalizedPhone() {
        service.rememberCall(COMPANY, "90 123 45 67",
                call("2026-08-01T09:00:00Z", Disposition.REFUSED, "Mijoz hozircha to'lay olmasligini aytdi"),
                Map.of("reasonCode", "NO_MONEY"));

        ClientMemory saved = savedMemory();
        assertThat(saved.phone()).isEqualTo(PHONE);
        assertThat(saved.recentCalls()).hasSize(1);
        assertThat(saved.recentCalls().get(0).disposition()).isEqualTo(Disposition.REFUSED);
        assertThat(saved.facts()).containsEntry("reasonCode", "NO_MONEY");
    }

    @Test
    void keepsOnlyTheThreeNewestCallsNewestFirst() {
        List<RememberedCall> existing = List.of(
                call("2026-08-03T09:00:00Z", Disposition.HUNG_UP, "third"),
                call("2026-08-02T09:00:00Z", Disposition.HUNG_UP, "second"),
                call("2026-08-01T09:00:00Z", Disposition.HUNG_UP, "first"));
        when(repository.findByCompanyIdAndPhone(COMPANY, PHONE)).thenReturn(
                new ClientMemory(1, COMPANY, PHONE, null, null, null, existing, Map.of(), Instant.now()));

        service.rememberCall(COMPANY, PHONE, call("2026-08-04T09:00:00Z", Disposition.PROMISE_TO_PAY, "fourth"), null);

        assertThat(savedMemory().recentCalls()).extracting(RememberedCall::summary)
                .containsExactly("fourth", "third", "second");
    }

    @Test
    void newerFactsOverlayOlderOnesAndBlanksAreSkipped() {
        when(repository.findByCompanyIdAndPhone(COMPANY, PHONE)).thenReturn(
                new ClientMemory(1, COMPANY, PHONE, "Aziz aka", null, "notes", List.of(),
                        Map.of("promisedDate", "2026-08-10", "reasonCode", "NO_MONEY"), Instant.now()));
        Map<String, Object> outcome = new HashMap<>();
        outcome.put("promisedDate", "2026-08-20");
        outcome.put("reasonCode", "");
        outcome.put("note", null);

        service.rememberCall(COMPANY, PHONE, call("2026-08-04T09:00:00Z", Disposition.PROMISE_TO_PAY, "x"), outcome);

        ClientMemory saved = savedMemory();
        assertThat(saved.facts()).containsEntry("promisedDate", "2026-08-20").containsEntry("reasonCode", "NO_MONEY");
        assertThat(saved.facts()).doesNotContainKey("note");
        assertThat(saved.preferredName()).isEqualTo("Aziz aka");
        assertThat(saved.operatorNotes()).isEqualTo("notes");
    }

    @Test
    void placeholderPhonesAreNotRemembered() {
        service.rememberCall(COMPANY, "MANUAL", call("2026-08-04T09:00:00Z", Disposition.COMPLETED, "x"), Map.of());
        service.rememberCall(COMPANY, "INBOUND", call("2026-08-04T09:00:00Z", Disposition.COMPLETED, "x"), Map.of());

        verify(repository, never()).upsert(eq(COMPANY), any());
    }

    @Test
    void operatorUpdateKeepsNullClearsBlankReplacesText() {
        when(repository.findByCompanyIdAndPhone(COMPANY, PHONE)).thenReturn(
                new ClientMemory(1, COMPANY, PHONE, "Aziz aka", "uz-UZ", "old notes", List.of(), Map.of(), Instant.now()));

        ClientMemory updated = service.updateByCompanyIdAndPhone(COMPANY, "998901234567",
                new UpdateClientMemoryRequest(null, "", "  new notes  "));

        assertThat(updated.preferredName()).isEqualTo("Aziz aka");
        assertThat(updated.preferredLanguage()).isNull();
        assertThat(updated.operatorNotes()).isEqualTo("new notes");
    }

    @Test
    void lookupOfUnknownClientIs404AndBadPhoneIs400() {
        assertThatThrownBy(() -> service.requireByCompanyIdAndPhone(COMPANY, PHONE))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.requireByCompanyIdAndPhone(COMPANY, "not-a-phone"))
                .isInstanceOf(ValidationException.class);
    }

    private ClientMemory savedMemory() {
        ArgumentCaptor<ClientMemory> captor = ArgumentCaptor.forClass(ClientMemory.class);
        verify(repository).upsert(eq(COMPANY), captor.capture());
        return captor.getValue();
    }

    private static RememberedCall call(String at, Disposition disposition, String summary) {
        return new RememberedCall(Instant.parse(at), "debt-collection", disposition, summary);
    }
}
