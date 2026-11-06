package uz.murodjon.robotcallv2.memory.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.memory.application.dto.UpdateClientMemoryRequest;
import uz.murodjon.robotcallv2.memory.application.dto.UpdateRememberedCallRequest;
import uz.murodjon.robotcallv2.memory.application.dto.UpsertClientMemoryFactRequest;
import uz.murodjon.robotcallv2.memory.application.port.output.ClientMemoryRepository;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemoryBuilder;
import uz.murodjon.robotcallv2.memory.domain.entity.MemorySource;
import uz.murodjon.robotcallv2.memory.domain.entity.RememberedCall;
import uz.murodjon.robotcallv2.memory.domain.enums.MemorySourceKind;
import uz.murodjon.robotcallv2.memory.infrastructure.config.ClientMemoryProperties;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
        service = new ClientMemoryService(repository, mock(AuditService.class), new ClientMemoryProperties(3, 4));
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
    void rememberCallCapturesPreferredNameAndLanguageWhenPresentInFacts() {
        service.rememberCall(COMPANY, PHONE,
                call("2026-08-01T09:00:00Z", Disposition.SUCCESSFUL, "Chat summary"),
                Map.of("clientName", "Jasur", "language", "uz-UZ"));

        ClientMemory saved = savedMemory();
        assertThat(saved.preferredName()).isEqualTo("Jasur");
        assertThat(saved.preferredLanguage()).isEqualTo("uz-UZ");
    }

    @Test
    void keepsOnlyTheConfiguredNumberOfCallsNewestFirst() {
        givenMemory(memory().recentCalls(List.of(
                call("2026-08-03T09:00:00Z", Disposition.HUNG_UP, "third"),
                call("2026-08-02T09:00:00Z", Disposition.HUNG_UP, "second"),
                call("2026-08-01T09:00:00Z", Disposition.HUNG_UP, "first"))).build());

        service.rememberCall(COMPANY, PHONE, call("2026-08-04T09:00:00Z", Disposition.PROMISE_TO_PAY, "fourth"), null);

        assertThat(savedMemory().recentCalls()).extracting(RememberedCall::summary)
                .containsExactly("fourth", "third", "second");
    }

    @Test
    void theSameCallRememberedTwiceReplacesItsEntryAndGetsItsOwnId() {
        RememberedCall first = RememberedCall.ofCall(42L, Instant.parse("2026-08-01T09:00:00Z"), "debt",
                Disposition.HUNG_UP, "first try").withId("old-id");
        givenMemory(memory().recentCalls(List.of(first)).build());

        service.rememberCall(COMPANY, PHONE, RememberedCall.ofCall(42L, Instant.parse("2026-08-01T09:01:00Z"),
                "debt", Disposition.HUNG_UP, "outbox retry"), null);

        List<RememberedCall> calls = savedMemory().recentCalls();
        assertThat(calls).singleElement().satisfies(call -> {
            assertThat(call.summary()).isEqualTo("outbox retry");
            assertThat(call.id()).isNotEqualTo("old-id").isNotBlank();
        });
    }

    @Test
    void factsFromACallRememberWhichCallSaidThem() {
        service.rememberCall(COMPANY, PHONE, RememberedCall.ofCall(42L, Instant.parse("2026-08-01T09:00:00Z"),
                "debt", Disposition.PROMISE_TO_PAY, "x"), Map.of("promisedDate", "2026-08-10"));

        MemorySource source = savedMemory().factSources().get("promisedDate");
        assertThat(source.kind()).isEqualTo(MemorySourceKind.CALL);
        assertThat(source.callAttemptId()).isEqualTo(42L);
        assertThat(source.at()).isEqualTo(Instant.parse("2026-08-01T09:00:00Z"));
    }

    @Test
    void newerFactsOverlayOlderOnesAndBlanksAreSkipped() {
        givenMemory(memory().preferredName("Aziz aka").operatorNotes("notes")
                .facts(Map.of("promisedDate", "2026-08-10", "reasonCode", "NO_MONEY")).build());
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
    void pastTheFactLimitTheOldestGoesButNeverWhatAnOperatorTyped() {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("legacy", "no source");
        facts.put("byOperator", "typed");
        facts.put("older", "a");
        facts.put("newer", "b");
        givenMemory(memory().facts(facts).factSources(Map.of(
                "byOperator", MemorySource.operator(Instant.parse("2026-01-01T00:00:00Z")),
                "older", MemorySource.tool("searchCatalog", 1, 0, Instant.parse("2026-08-01T00:00:00Z")),
                "newer", MemorySource.tool("searchCatalog", 2, 0, Instant.parse("2026-08-02T00:00:00Z")))).build());

        service.rememberFacts(COMPANY, PHONE, Map.of("fresh1", "x", "fresh2", "y"),
                MemorySource.tool("searchFlats", 3, 0, Instant.parse("2026-08-03T00:00:00Z")));

        assertThat(savedMemory().facts()).containsOnlyKeys("byOperator", "newer", "fresh1", "fresh2");
        assertThat(savedMemory().factSources()).containsOnlyKeys("byOperator", "newer", "fresh1", "fresh2");
    }

    @Test
    void placeholderPhonesAreNotRemembered() {
        service.rememberCall(COMPANY, "MANUAL", call("2026-08-04T09:00:00Z", Disposition.COMPLETED, "x"), Map.of());
        service.rememberCall(COMPANY, "INBOUND", call("2026-08-04T09:00:00Z", Disposition.COMPLETED, "x"), Map.of());

        verify(repository, never()).upsert(eq(COMPANY), any());
    }

    @Test
    void operatorUpdateKeepsNullClearsBlankReplacesText() {
        givenMemory(memory().preferredName("Aziz aka").preferredLanguage("uz-UZ").operatorNotes("old notes").build());

        ClientMemory updated = service.updateByCompanyIdAndPhone(COMPANY, "998901234567",
                new UpdateClientMemoryRequest(null, "", "  new notes  "));

        assertThat(updated.preferredName()).isEqualTo("Aziz aka");
        assertThat(updated.preferredLanguage()).isNull();
        assertThat(updated.operatorNotes()).isEqualTo("new notes");
    }

    @Test
    void anOperatorFactIsMarkedAsTheirsAndABadNameIsRejected() {
        givenMemory(memory().build());

        ClientMemory saved = service.upsertFactByCompanyIdAndPhone(COMPANY, PHONE, "visitDay",
                new UpsertClientMemoryFactRequest("  shanba "));

        assertThat(saved.facts()).containsEntry("visitDay", "shanba");
        assertThat(saved.factSources().get("visitDay").kind()).isEqualTo(MemorySourceKind.OPERATOR);
        assertThatThrownBy(() -> service.upsertFactByCompanyIdAndPhone(COMPANY, PHONE, "1 bad name",
                new UpsertClientMemoryFactRequest("x"))).isInstanceOf(ValidationException.class);
    }

    @Test
    void anOperatorCanCorrectAndRemoveARememberedConversationButNotOneThatIsNotThere() {
        RememberedCall kept = call("2026-08-02T09:00:00Z", Disposition.HUNG_UP, "kept");
        RememberedCall wrong = call("2026-08-01T09:00:00Z", Disposition.HUNG_UP, "wrong");
        givenMemory(memory().recentCalls(List.of(kept, wrong)).build());

        ClientMemory corrected = service.updateCallByCompanyIdAndPhone(COMPANY, PHONE, wrong.id(),
                new UpdateRememberedCallRequest("Mijoz ertaga qayta qo'ng'iroq qilishni so'radi"));
        assertThat(corrected.recentCalls().get(1).summary()).isEqualTo("Mijoz ertaga qayta qo'ng'iroq qilishni so'radi");
        assertThat(corrected.recentCalls().get(1).id()).isEqualTo(wrong.id());

        ClientMemory removed = service.deleteCallByCompanyIdAndPhone(COMPANY, PHONE, kept.id());
        assertThat(removed.recentCalls()).extracting(RememberedCall::summary).containsExactly("wrong");

        assertThatThrownBy(() -> service.deleteCallByCompanyIdAndPhone(COMPANY, PHONE, "missing"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.deleteFactByCompanyIdAndPhone(COMPANY, PHONE, "missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void anEntrySavedBeforeIdsExistedKeepsTheSameIdOnEveryRead() {
        RememberedCall legacy = new RememberedCall(null, Instant.parse("2026-08-01T09:00:00Z"), "debt",
                Disposition.HUNG_UP, "old", null, null);
        RememberedCall readAgain = new RememberedCall(null, Instant.parse("2026-08-01T09:00:00Z"), "debt",
                Disposition.HUNG_UP, "old", null, null);

        assertThat(legacy.id()).isNotBlank().isEqualTo(readAgain.id());
    }

    @Test
    void clearingAClientThatHasNoMemoryIs404() {
        when(repository.deleteByCompanyIdAndPhone(COMPANY, PHONE)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteByCompanyIdAndPhone(COMPANY, PHONE))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void lookupOfUnknownClientIs404AndBadPhoneIs400() {
        assertThatThrownBy(() -> service.requireByCompanyIdAndPhone(COMPANY, PHONE))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.requireByCompanyIdAndPhone(COMPANY, "not-a-phone"))
                .isInstanceOf(ValidationException.class);
    }

    private void givenMemory(ClientMemory memory) {
        when(repository.lockByCompanyIdAndPhone(COMPANY, PHONE)).thenReturn(memory);
    }

    private static ClientMemoryBuilder memory() {
        return ClientMemory.builder().id(1).companyId(COMPANY).phone(PHONE).updatedAt(Instant.now());
    }

    private ClientMemory savedMemory() {
        ArgumentCaptor<ClientMemory> captor = ArgumentCaptor.forClass(ClientMemory.class);
        verify(repository, atLeastOnce()).upsert(eq(COMPANY), captor.capture());
        return captor.getValue();
    }

    private static RememberedCall call(String at, Disposition disposition, String summary) {
        return new RememberedCall(null, Instant.parse(at), "debt-collection", disposition, summary, null, null);
    }
}
