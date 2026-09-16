package uz.murodjon.robotcallv2.donotcall.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.contact.application.port.input.ContactUseCase;
import uz.murodjon.robotcallv2.contact.domain.event.ContactOptOutEvent;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallRemoveResponse;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallRow;
import uz.murodjon.robotcallv2.donotcall.application.mapper.DoNotCallMapper;
import uz.murodjon.robotcallv2.donotcall.application.port.output.DoNotCallRepository;
import uz.murodjon.robotcallv2.donotcall.domain.entity.DoNotCall;
import uz.murodjon.robotcallv2.donotcall.domain.entity.DoNotCallFilter;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DoNotCallServiceTest {

    private DoNotCallRepository doNotCallRepository;
    private ContactUseCase contactService;
    private AuditService auditService;
    private DoNotCallMapper doNotCallMapper;
    private DoNotCallService service;

    @BeforeEach
    void setUp() {
        doNotCallRepository = mock(DoNotCallRepository.class);
        contactService = mock(ContactUseCase.class);
        auditService = mock(AuditService.class);
        doNotCallMapper = mock(DoNotCallMapper.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.actorName()).thenReturn("tester");

        service = new DoNotCallService(
                doNotCallRepository, contactService, auditService, doNotCallMapper, currentUser);
    }

    @Test
    void listReturnsEnrichedPageableData() {
        DoNotCall entry = new DoNotCall(1L, "998901234567", "Asked not to call", DoNotCallSource.CALL, Instant.now());
        DoNotCallFilter filter = new DoNotCallFilter(0, 10, null);
        DoNotCallRow row = new DoNotCallRow(1L, "998901234567", "Ali Valiyev", "Asked not to call",
                DoNotCallSource.CALL, Instant.now());

        when(doNotCallRepository.findAll(1L, filter)).thenReturn(List.of(entry));
        when(doNotCallRepository.count(1L, filter)).thenReturn(1L);
        when(contactService.findNamesByPhones(1L, List.of("998901234567"))).thenReturn(Map.of("998901234567", "Ali Valiyev"));
        when(doNotCallMapper.toRow(entry, "Ali Valiyev")).thenReturn(row);

        PageableData<DoNotCallRow> result = service.list(1L, filter);

        assertThat(result).isNotNull();
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).phone()).isEqualTo("998901234567");
        assertThat(result.data().get(0).contactName()).isEqualTo("Ali Valiyev");
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    void removeSuccessfulLogsAuditAndReturnsResponse() {
        String phone = "998901234567";
        when(doNotCallRepository.remove(eq(1L), eq(phone), anyString())).thenReturn(true);

        DoNotCallRemoveResponse response = service.remove(1L, phone);

        assertThat(response.phone()).isEqualTo(phone);
        assertThat(response.removed()).isTrue();
        verify(auditService).record(1L, "DNC_REMOVE", "do_not_call", phone, null);
    }

    @Test
    void removeThrowsNotFoundWhenEntryMissing() {
        String phone = "998909999999";
        when(doNotCallRepository.remove(eq(1L), eq(phone), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.remove(1L, phone))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void addStoresTheNormalisedNumber() {
        service.add(1L, "998901234567", "Requested", DoNotCallSource.MANUAL);
        verify(doNotCallRepository).add(1L, "+998901234567", "Requested", DoNotCallSource.MANUAL);
    }

    /** The list used to match exactly what was typed, so a different spelling slipped past it. */
    @Test
    void containsMatchesAnyFormattingOfAListedNumber() {
        when(doNotCallRepository.existsByPhone(1L, "+998901234567")).thenReturn(true);
        assertThat(service.contains(1L, "+998 90 123-45-67")).isTrue();
    }

    /** One lookup for a whole import, matching a number under either spelling the way contains() does. */
    @Test
    void findBlockedPhonesMatchesEitherSpellingInOneLookup() {
        when(doNotCallRepository.findListedPhones(eq(1L), anyCollection())).thenReturn(Set.of("+998901234567"));

        Set<String> blocked = service.findBlockedPhones(1L, List.of("998901234567", "+998907654321"));

        assertThat(blocked).containsExactly("998901234567");
        verify(doNotCallRepository, times(1)).findListedPhones(1L,
                Set.of("998901234567", "+998901234567", "+998907654321"));
        verify(doNotCallRepository, never()).existsByPhone(anyLong(), anyString());
    }

    /** A contact's opt-out reaches the list through the event, stored as a manual entry. */
    @Test
    void aContactOptOutIsListedAsAManualEntry() {
        service.onContactOptOut(new ContactOptOutEvent(1L, "+998905556677", "opted out via contact"));

        verify(doNotCallRepository).add(1L, "+998905556677", "opted out via contact", DoNotCallSource.MANUAL);
    }

    @Test
    void containsDelegatesToRepository() {
        when(doNotCallRepository.existsByPhone(1L, "998901234567")).thenReturn(true);
        assertThat(service.contains(1L, "998901234567")).isTrue();
    }
}
