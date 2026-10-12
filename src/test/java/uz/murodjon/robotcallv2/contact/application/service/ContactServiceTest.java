package uz.murodjon.robotcallv2.contact.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallTranscriptUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.OutboxMessageUseCase;
import uz.murodjon.robotcallv2.contact.application.dto.ContactDncResponse;
import uz.murodjon.robotcallv2.contact.application.dto.CreateContactRequest;
import uz.murodjon.robotcallv2.contact.application.dto.UpdateContactRequest;
import uz.murodjon.robotcallv2.contact.application.mapper.ContactMapper;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactPrivacyRepository;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactRepository;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactFilter;
import uz.murodjon.robotcallv2.contact.domain.event.ContactOptOutEvent;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;
import uz.murodjon.robotcallv2.report.application.port.input.ReportUseCase;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.storage.application.service.FileStorageService;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ContactServiceTest {

    private ContactRepository contactRepository;
    private ReportUseCase reportUseCase;
    private ApplicationEventPublisher applicationEventPublisher;
    private AuditService auditService;
    private ContactService contactService;

    @BeforeEach
    void setUp() {
        contactRepository = mock(ContactRepository.class);
        reportUseCase = mock(ReportUseCase.class);
        applicationEventPublisher = mock(ApplicationEventPublisher.class);
        auditService = mock(AuditService.class);

        contactService = new ContactService(
                contactRepository, providerOf(reportUseCase), applicationEventPublisher, auditService,
                mock(ContactPrivacyRepository.class),
                mock(ClientMemoryUseCase.class),
                mock(CallTranscriptUseCase.class),
                mock(OutboxMessageUseCase.class),
                mock(FileStorageService.class),
                new ContactMapper());
    }

    /** ContactService resolves the report use case lazily (bean cycle); the test hands it the mock directly. */
    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(bean);
        when(provider.getIfAvailable()).thenReturn(bean);
        return provider;
    }

    private Contact sampleContact(long id, String name, String phone) {
        return new Contact(id, name, phone, "Tashkent", "vip", "notes", Instant.now());
    }

    @Test
    void createSuccess() {
        CreateContactRequest req = new CreateContactRequest("Aziz", "+998901234567", "Tashkent", "vip", "Client");
        String normalizedPhone = "+998901234567";

        when(contactRepository.existsByPhone(1L, normalizedPhone)).thenReturn(false);
        when(contactRepository.create(1L, Contact.of("Aziz", normalizedPhone, "Tashkent", "vip", "Client"))).thenReturn(10L);
        when(contactRepository.find(1L, 10L)).thenReturn(sampleContact(10L, "Aziz", normalizedPhone));

        Contact created = contactService.create(1L, req);

        assertThat(created).isNotNull();
        assertThat(created.id()).isEqualTo(10L);
        assertThat(created.phone()).isEqualTo(normalizedPhone);
        verify(auditService).record(1L, "CONTACT_CREATE", "contact", "10", normalizedPhone);
    }

    @Test
    void createThrowsConflictWhenPhoneAlreadyExists() {
        CreateContactRequest req = new CreateContactRequest("Aziz", "+998901234567", null, null, null);
        when(contactRepository.existsByPhone(1L, "+998901234567")).thenReturn(true);

        assertThatThrownBy(() -> contactService.create(1L, req))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateSuccess() {
        Contact existing = sampleContact(10L, "Aziz", "+998901234567");
        when(contactRepository.find(1L, 10L)).thenReturn(existing);

        UpdateContactRequest req = new UpdateContactRequest("Aziz Updated", "Samarkand", "lead", "Updated notes");
        Contact updatedContact = new Contact(10L, "Aziz Updated", "+998901234567", "Samarkand", "lead", "Updated notes", Instant.now());
        when(contactRepository.find(1L, 10L)).thenReturn(existing, updatedContact);

        Contact result = contactService.update(1L, 10L, req);

        assertThat(result.name()).isEqualTo("Aziz Updated");
        verify(contactRepository).update(1L, 10L, Contact.profile("Aziz Updated", "Samarkand", "lead", "Updated notes"));
        verify(auditService).record(1L, "CONTACT_UPDATE", "contact", "10", "+998901234567");
    }

    @Test
    void deleteSuccess() {
        Contact existing = sampleContact(10L, "Aziz", "+998901234567");
        when(contactRepository.find(1L, 10L)).thenReturn(existing);

        contactService.delete(1L, 10L);

        verify(contactRepository).delete(1L, 10L);
        verify(auditService).record(1L, "CONTACT_DELETE", "contact", "10", "+998901234567");
    }

    @Test
    void listReturnsPageableData() {
        ContactFilter filter = new ContactFilter(0, 10, null, null);
        Contact c = sampleContact(1L, "Ali", "+998901112233");

        when(contactRepository.findPage(1L, filter)).thenReturn(PageableData.of(List.of(c), 0, 10, 1L));

        PageableData<Contact> result = contactService.list(1L, filter);

        assertThat(result.data()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    void addToDoNotCallMarksOptOut() {
        Contact existing = sampleContact(5L, "Bobur", "+998905556677");
        when(contactRepository.find(1L, 5L)).thenReturn(existing);

        ContactDncResponse response = contactService.addToDoNotCall(1L, 5L);

        assertThat(response.contactId()).isEqualTo(5L);
        assertThat(response.doNotCall()).isTrue();
        verify(applicationEventPublisher).publishEvent(new ContactOptOutEvent(1L, "+998905556677", "opted out via contact"));
        verify(auditService).record(1L, "CONTACT_DNC", "contact", "5", "+998905556677");
    }

    @Test
    void scrubPhonesHandlesEmptyOrNull() {
        uz.murodjon.robotcallv2.contact.application.dto.ContactScrubResponse res =
                contactService.scrubPhones(1L, new uz.murodjon.robotcallv2.contact.application.dto.ContactScrubRequest(List.of()));
        assertThat(res.total()).isZero();
        assertThat(res.items()).isEmpty();
    }

    @Test
    void scrubPhonesValidatesCarriersAndIdentifiesBlocked() {
        uz.murodjon.robotcallv2.donotcall.application.port.input.DoNotCallUseCase dnc =
                mock(uz.murodjon.robotcallv2.donotcall.application.port.input.DoNotCallUseCase.class);
        when(dnc.findBlockedPhones(eq(1L), anyCollection())).thenReturn(java.util.Set.of("+998901234567"));

        ContactService serviceWithDnc = new ContactService(
                contactRepository, providerOf(reportUseCase), applicationEventPublisher, auditService,
                mock(ContactPrivacyRepository.class),
                mock(ClientMemoryUseCase.class),
                mock(CallTranscriptUseCase.class),
                mock(OutboxMessageUseCase.class),
                mock(FileStorageService.class),
                new ContactMapper(),
                new uz.murodjon.robotcallv2.dialer.infrastructure.adapter.DefaultPhoneNumberScrubberAdapter(),
                providerOf(dnc)
        );

        uz.murodjon.robotcallv2.contact.application.dto.ContactScrubRequest req =
                new uz.murodjon.robotcallv2.contact.application.dto.ContactScrubRequest(List.of(
                        "+998901234567", // DNC blocked
                        "998931112233",  // Valid Ucell
                        "+998011234567", // Unallocated prefix
                        "invalid"        // Format error
                ));

        uz.murodjon.robotcallv2.contact.application.dto.ContactScrubResponse res =
                serviceWithDnc.scrubPhones(1L, req);

        assertThat(res.total()).isEqualTo(4);
        assertThat(res.validCount()).isEqualTo(1);
        assertThat(res.invalidCount()).isEqualTo(3);
        assertThat(res.dncCount()).isEqualTo(1);

        assertThat(res.items().get(0).inDoNotCall()).isTrue();
        assertThat(res.items().get(0).status()).isEqualTo(uz.murodjon.robotcallv2.dialer.domain.enums.ScrubStatus.BLOCKED);

        assertThat(res.items().get(1).valid()).isTrue();
        assertThat(res.items().get(1).carrierOrRegion()).isEqualTo("Ucell");

        assertThat(res.items().get(2).valid()).isFalse();
        assertThat(res.items().get(2).status()).isEqualTo(uz.murodjon.robotcallv2.dialer.domain.enums.ScrubStatus.UNALLOCATED_PREFIX);

        assertThat(res.items().get(3).valid()).isFalse();
        assertThat(res.items().get(3).status()).isEqualTo(uz.murodjon.robotcallv2.dialer.domain.enums.ScrubStatus.INVALID_FORMAT);
    }
}
