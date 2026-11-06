package uz.murodjon.robotcallv2.contact.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactCrmLinkRepository;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactFieldMergerPort;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactFieldRepository;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactRepository;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactField;
import uz.murodjon.robotcallv2.contact.domain.entity.ExtractedCallValues;
import uz.murodjon.robotcallv2.contact.domain.entity.MergedFieldValue;
import uz.murodjon.robotcallv2.contact.domain.enums.ContactFieldType;
import uz.murodjon.robotcallv2.contact.domain.enums.ContactFieldUpdateMode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContactFieldServiceTest {

    private static final String PHONE = "+998901234567";

    private ContactFieldRepository contactFieldRepository;
    private ContactRepository contactRepository;
    private ContactFieldMergerPort mergerPort;
    private CallBillingUseCase callBillingUseCase;
    private ContactCrmLinkRepository linkRepository;
    private ContactFieldService service;

    @BeforeEach
    void setUp() {
        contactFieldRepository = mock(ContactFieldRepository.class);
        contactRepository = mock(ContactRepository.class);
        mergerPort = mock(ContactFieldMergerPort.class);
        callBillingUseCase = mock(CallBillingUseCase.class);
        linkRepository = mock(ContactCrmLinkRepository.class);
        service = new ContactFieldService(contactFieldRepository, contactRepository, mock(AuditUseCase.class),
                mergerPort, callBillingUseCase, linkRepository);
    }

    private static ContactField field(String key, ContactFieldType type, String extractionKey,
                                      ContactFieldUpdateMode mode) {
        return new ContactField(1, key, key, type, List.of(), null, extractionKey, mode, Instant.now());
    }

    private static Contact contact(Map<String, String> customFields) {
        return new Contact(7, "Aziz", PHONE, null, null, null, customFields, Instant.now());
    }

    private Map<String, String> savedFields() {
        ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository).update(eq(1L), eq(7L), captor.capture());
        return captor.getValue().customFields();
    }

    @Test
    void appliesEachModeByCompanyMapping() {
        when(contactFieldRepository.findAllByCompanyId(1L)).thenReturn(List.of(
                field("lead_status", ContactFieldType.TEXT, "status", ContactFieldUpdateMode.OVERWRITE),
                field("first_source", ContactFieldType.TEXT, "source", ContactFieldUpdateMode.FILL_IF_EMPTY)));
        when(contactRepository.findByPhone(1L, PHONE)).thenReturn(contact(Map.of("lead_status", "warm", "first_source", "ads")));

        service.applyCallValues(1L, PHONE, new ExtractedCallValues(99, null,
                Map.of("status", "hot", "source", "referral"), Map.of()));

        assertThat(savedFields()).containsEntry("lead_status", "hot").containsEntry("first_source", "ads");
    }

    @Test
    void companyMappingWinsOverAgentContactField() {
        when(contactFieldRepository.findAllByCompanyId(1L)).thenReturn(List.of(
                field("lead_status", ContactFieldType.TEXT, "status", ContactFieldUpdateMode.OVERWRITE)));
        when(contactRepository.findByPhone(1L, PHONE)).thenReturn(contact(Map.of()));

        service.applyCallValues(1L, PHONE, new ExtractedCallValues(99, null,
                Map.of("status", "hot"), Map.of("lead_status", "from-agent", "city", "Toshkent")));

        assertThat(savedFields()).containsEntry("lead_status", "hot").containsEntry("city", "Toshkent");
    }

    @Test
    void dropsValueThatDoesNotFitTheType() {
        when(contactFieldRepository.findAllByCompanyId(1L)).thenReturn(List.of(
                field("budget", ContactFieldType.NUMBER, "budget", ContactFieldUpdateMode.OVERWRITE)));
        when(contactRepository.findByPhone(1L, PHONE)).thenReturn(contact(Map.of()));

        service.applyCallValues(1L, PHONE, new ExtractedCallValues(99, null, Map.of("budget", "ko'p"), Map.of()));

        verify(contactRepository, never()).update(anyLong(), anyLong(), any());
        verify(contactRepository, never()).create(anyLong(), any());
    }

    @Test
    void accumulateMergesWithModelAndCharges() {
        ContactField history = field("history", ContactFieldType.TEXT, "notes", ContactFieldUpdateMode.ACCUMULATE);
        when(contactFieldRepository.findAllByCompanyId(1L)).thenReturn(List.of(history));
        when(contactRepository.findByPhone(1L, PHONE)).thenReturn(contact(Map.of("history", "asked about price")));
        when(mergerPort.generateMergedValue(1L, "fast-model", history, "asked about price", "wants 2 rooms"))
                .thenReturn(new MergedFieldValue("asked about price; wants 2 rooms", "fast-model", 100, 20, false));

        service.applyCallValues(1L, PHONE, new ExtractedCallValues(99, "fast-model",
                Map.of("notes", "wants 2 rooms"), Map.of()));

        assertThat(savedFields()).containsEntry("history", "asked about price; wants 2 rooms");
        verify(callBillingUseCase).chargeContactFieldMerge(eq(1L), eq(99L), eq("history"), any());
    }

    @Test
    void accumulateKeepsBothValuesWhenModelGivesNothing() {
        ContactField history = field("history", ContactFieldType.TEXT, "notes", ContactFieldUpdateMode.ACCUMULATE);
        when(contactFieldRepository.findAllByCompanyId(1L)).thenReturn(List.of(history));
        when(contactRepository.findByPhone(1L, PHONE)).thenReturn(contact(Map.of("history", "old")));

        service.applyCallValues(1L, PHONE, new ExtractedCallValues(99, null, Map.of("notes", "new"), Map.of()));

        assertThat(savedFields()).containsEntry("history", "old\n\nnew");
        verifyNoInteractions(callBillingUseCase);
    }

    @Test
    void createsContactForUnknownNumber() {
        when(contactFieldRepository.findAllByCompanyId(1L)).thenReturn(List.of());

        service.applyCallValues(1L, PHONE, new ExtractedCallValues(99, null, Map.of(), Map.of("city", "Toshkent")));

        ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository).create(eq(1L), captor.capture());
        assertThat(captor.getValue().phone()).isEqualTo(PHONE);
        assertThat(captor.getValue().customFields()).containsEntry("city", "Toshkent");
    }
}
