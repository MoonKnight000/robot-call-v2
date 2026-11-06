package uz.murodjon.robotcallv2.contact.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactCrmLinkRepository;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactRepository;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.contact.domain.entity.PendingCrmPush;
import uz.murodjon.robotcallv2.contact.domain.service.CrmPushRetryPolicy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContactCrmPushServiceTest {

    private static final PendingCrmPush PUSH = new PendingCrmPush(1L, 7L, "BITRIX24", "42",
            Instant.parse("2026-11-06T10:00:00Z"), 0, Map.of());

    private ContactCrmLinkRepository linkRepository;
    private ContactRepository contactRepository;
    private ContactCrmPushService service;

    @BeforeEach
    void setUp() {
        linkRepository = mock(ContactCrmLinkRepository.class);
        contactRepository = mock(ContactRepository.class);
        service = new ContactCrmPushService(linkRepository, contactRepository);
    }

    @Test
    void dueWritesCarryTheContactsCurrentValuesAndSkipDeletedContacts() {
        PendingCrmPush gone = new PendingCrmPush(1L, 8L, "BITRIX24", "43", PUSH.requestedAt(), 0, Map.of());
        when(linkRepository.findDuePushes(any(), eq(10))).thenReturn(List.of(PUSH, gone));
        when(contactRepository.find(1L, 7L)).thenReturn(new Contact(7L, "Ali", "+998901234567", null, null, null,
                Map.of("city", "Toshkent"), null));

        List<PendingCrmPush> due = service.findDueCrmPushes(Instant.now(), 10);

        assertThat(due).singleElement().satisfies(push -> {
            assertThat(push.contactId()).isEqualTo(7L);
            assertThat(push.customFields()).containsEntry("city", "Toshkent");
        });
    }

    @Test
    void successClearsTheRequest() {
        service.finishCrmPush(PUSH, null);

        verify(linkRepository).savePushSuccess(eq(PUSH), any());
        verify(linkRepository, never()).savePushFailure(any(), anyInt(), any(), any());
    }

    @Test
    void failureIsRetriedThenGivenUp() {
        service.finishCrmPush(PUSH, "down");
        verify(linkRepository).savePushFailure(eq(PUSH), eq(1), notNull(), eq("down"));

        PendingCrmPush last = new PendingCrmPush(1L, 7L, "BITRIX24", "42", PUSH.requestedAt(),
                CrmPushRetryPolicy.MAX_ATTEMPTS - 1, Map.of());
        service.finishCrmPush(last, "down");
        verify(linkRepository).savePushFailure(eq(last), eq(CrmPushRetryPolicy.MAX_ATTEMPTS), isNull(), eq("down"));
    }
}
