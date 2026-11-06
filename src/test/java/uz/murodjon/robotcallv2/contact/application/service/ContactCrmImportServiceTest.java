package uz.murodjon.robotcallv2.contact.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactCrmLinkRepository;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactFieldRepository;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactRepository;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactCrmLink;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactField;
import uz.murodjon.robotcallv2.contact.domain.entity.ImportedCrmContact;
import uz.murodjon.robotcallv2.contact.domain.enums.ContactFieldType;
import uz.murodjon.robotcallv2.contact.domain.enums.ContactImportOutcome;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContactCrmImportServiceTest {

    private static final String PHONE = "+998901234567";

    private ContactRepository contactRepository;
    private ContactFieldRepository contactFieldRepository;
    private ContactCrmLinkRepository linkRepository;
    private ContactCrmImportService service;

    @BeforeEach
    void setUp() {
        contactRepository = mock(ContactRepository.class);
        contactFieldRepository = mock(ContactFieldRepository.class);
        linkRepository = mock(ContactCrmLinkRepository.class);
        service = new ContactCrmImportService(contactRepository, contactFieldRepository, linkRepository);
        when(contactFieldRepository.findAllByCompanyId(1L)).thenReturn(List.of(
                new ContactField(1, "budget", "Budget", ContactFieldType.NUMBER, List.of(), null, null, null, Instant.now())));
    }

    @Test
    void createsContactAndLinkForNewRecord() {
        when(contactRepository.create(eq(1L), any())).thenReturn(5L);

        ContactImportOutcome outcome = service.importCrmContact(1L,
                new ImportedCrmContact("BITRIX24", "42", "Aziz Karimov", "90 123 45 67", Map.of("budget", "1000")));

        assertThat(outcome).isEqualTo(ContactImportOutcome.CREATED);
        ArgumentCaptor<Contact> contact = ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository).create(eq(1L), contact.capture());
        assertThat(contact.getValue().phone()).isEqualTo(PHONE);
        assertThat(contact.getValue().customFields()).containsEntry("budget", "1000");
        ArgumentCaptor<ContactCrmLink> link = ArgumentCaptor.forClass(ContactCrmLink.class);
        verify(linkRepository).upsert(eq(1L), link.capture());
        assertThat(link.getValue().contactId()).isEqualTo(5L);
        assertThat(link.getValue().externalId()).isEqualTo("42");
    }

    @Test
    void updatesLinkedContactKeepingValuesTheCrmLeftBlankAndDroppingBadTypes() {
        when(linkRepository.findContactId(1L, "BITRIX24", "42")).thenReturn(7L);
        when(contactRepository.find(1L, 7L)).thenReturn(new Contact(7, "Old", PHONE, null, null, null,
                Map.of("city", "Toshkent"), Instant.now()));

        ContactImportOutcome outcome = service.importCrmContact(1L,
                new ImportedCrmContact("BITRIX24", "42", "New Name", PHONE, Map.of("budget", "ko'p", "city", " ")));

        assertThat(outcome).isEqualTo(ContactImportOutcome.UPDATED);
        ArgumentCaptor<Contact> contact = ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository).update(eq(1L), eq(7L), contact.capture());
        assertThat(contact.getValue().name()).isEqualTo("New Name");
        assertThat(contact.getValue().customFields()).containsExactly(Map.entry("city", "Toshkent"));
    }

    @Test
    void unchangedLinkedContactIsNotWritten() {
        when(linkRepository.findContactId(1L, "AMOCRM", "9")).thenReturn(7L);
        when(contactRepository.find(1L, 7L)).thenReturn(new Contact(7, "Aziz", PHONE, null, null, null,
                Map.of(), Instant.now()));

        ContactImportOutcome outcome = service.importCrmContact(1L,
                new ImportedCrmContact("AMOCRM", "9", "Aziz", PHONE, Map.of()));

        assertThat(outcome).isEqualTo(ContactImportOutcome.UNCHANGED);
        verify(contactRepository, never()).update(anyLong(), anyLong(), any());
    }

    @Test
    void recordWithoutUsablePhoneIsSkipped() {
        ContactImportOutcome outcome = service.importCrmContact(1L,
                new ImportedCrmContact("AMOCRM", "9", "Aziz", "abc", Map.of()));

        assertThat(outcome).isEqualTo(ContactImportOutcome.SKIPPED);
        verifyNoInteractions(linkRepository);
    }
}
