package uz.murodjon.robotcallv2.contact.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.contact.application.dto.ContactFilter;
import uz.murodjon.robotcallv2.contact.application.dto.CreateContactRequest;
import uz.murodjon.robotcallv2.contact.application.dto.UpdateContactRequest;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactRepository;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.donotcall.application.dto.ContactDncResponse;
import uz.murodjon.robotcallv2.donotcall.application.port.output.DoNotCallRepository;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;
import uz.murodjon.robotcallv2.report.application.port.output.ReportRepository;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ContactServiceTest {

    private ContactRepository contactRepository;
    private ReportRepository reportRepository;
    private DoNotCallRepository doNotCallRepository;
    private AuditService auditService;
    private CurrentCompany currentCompany;
    private ContactService contactService;

    @BeforeEach
    void setUp() {
        contactRepository = mock(ContactRepository.class);
        reportRepository = mock(ReportRepository.class);
        doNotCallRepository = mock(DoNotCallRepository.class);
        auditService = mock(AuditService.class);
        currentCompany = mock(CurrentCompany.class);

        when(currentCompany.id()).thenReturn(1L);

        contactService = new ContactService(
                contactRepository, reportRepository, doNotCallRepository,
                auditService, currentCompany
        );
    }

    private Contact sampleContact(long id, String name, String phone) {
        return new Contact(id, name, phone, "Tashkent", "vip", "notes", Instant.now());
    }

    @Test
    void createSuccess() {
        CreateContactRequest req = new CreateContactRequest("Aziz", "+998901234567", "Tashkent", "vip", "Client");
        String normalizedPhone = "+998901234567";

        when(contactRepository.existsByPhone(1L, normalizedPhone)).thenReturn(false);
        when(contactRepository.create(1L, "Aziz", normalizedPhone, "Tashkent", "vip", "Client")).thenReturn(10L);
        when(contactRepository.find(1L, 10L)).thenReturn(sampleContact(10L, "Aziz", normalizedPhone));

        Contact created = contactService.create(req);

        assertThat(created).isNotNull();
        assertThat(created.id()).isEqualTo(10L);
        assertThat(created.phone()).isEqualTo(normalizedPhone);
        verify(auditService).record("CONTACT_CREATE", "contact", "10", normalizedPhone);
    }

    @Test
    void createThrowsConflictWhenPhoneAlreadyExists() {
        CreateContactRequest req = new CreateContactRequest("Aziz", "+998901234567", null, null, null);
        when(contactRepository.existsByPhone(1L, "+998901234567")).thenReturn(true);

        assertThatThrownBy(() -> contactService.create(req))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateSuccess() {
        Contact existing = sampleContact(10L, "Aziz", "+998901234567");
        when(contactRepository.find(1L, 10L)).thenReturn(existing);

        UpdateContactRequest req = new UpdateContactRequest("Aziz Updated", "Samarkand", "lead", "Updated notes");
        Contact updatedContact = new Contact(10L, "Aziz Updated", "+998901234567", "Samarkand", "lead", "Updated notes", Instant.now());
        when(contactRepository.find(1L, 10L)).thenReturn(existing, updatedContact);

        Contact result = contactService.update(10L, req);

        assertThat(result.name()).isEqualTo("Aziz Updated");
        verify(contactRepository).update(1L, 10L, "Aziz Updated", "Samarkand", "lead", "Updated notes");
        verify(auditService).record("CONTACT_UPDATE", "contact", "10", "+998901234567");
    }

    @Test
    void deleteSuccess() {
        Contact existing = sampleContact(10L, "Aziz", "+998901234567");
        when(contactRepository.find(1L, 10L)).thenReturn(existing);

        contactService.delete(10L);

        verify(contactRepository).delete(1L, 10L);
        verify(auditService).record("CONTACT_DELETE", "contact", "10", "+998901234567");
    }

    @Test
    void listReturnsPageableData() {
        ContactFilter filter = new ContactFilter(0, 10, null, null);
        Contact c = sampleContact(1L, "Ali", "+998901112233");

        when(contactRepository.findAll(1L, filter)).thenReturn(List.of(c));
        when(contactRepository.count(1L, filter)).thenReturn(1L);

        PageableData<Contact> result = contactService.list(filter);

        assertThat(result.data()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    void addToDoNotCallMarksOptOut() {
        Contact existing = sampleContact(5L, "Bobur", "+998905556677");
        when(contactRepository.find(1L, 5L)).thenReturn(existing);

        ContactDncResponse response = contactService.addToDoNotCall(5L);

        assertThat(response.contactId()).isEqualTo(5L);
        assertThat(response.doNotCall()).isTrue();
        verify(doNotCallRepository).add(1L, "+998905556677", "opted out via contact", DoNotCallSource.MANUAL);
        verify(auditService).record("CONTACT_DNC", "contact", "5", "+998905556677");
    }
}
