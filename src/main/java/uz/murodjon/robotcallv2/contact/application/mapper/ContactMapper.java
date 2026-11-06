package uz.murodjon.robotcallv2.contact.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.contact.application.dto.ContactCallHistoryRow;
import uz.murodjon.robotcallv2.contact.application.dto.ContactRow;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactCrmLink;
import uz.murodjon.robotcallv2.report.domain.entity.PhoneCallHistoryEntry;
import uz.murodjon.robotcallv2.report.domain.entity.PhoneCallStats;

/** Domain → DTO for the contact endpoints. */
@Component
public class ContactMapper {

    public ContactCallHistoryRow toContactCallHistoryRow(PhoneCallHistoryEntry entry) {
        return new ContactCallHistoryRow(entry.callId(), entry.campaignName(), entry.startedAt(),
                entry.durationSec(), entry.disposition());
    }

    /** {@code stats} null — the number was never called; {@code link} null — not imported from a CRM. */
    public ContactRow toContactRow(Contact contact, PhoneCallStats stats, ContactCrmLink link) {
        return new ContactRow(contact.id(), contact.name(), contact.phone(), contact.address(), contact.tags(),
                contact.notes(), contact.customFields(), contact.createdAt(),
                stats != null ? stats.callCount() : 0, stats != null ? stats.lastCallAt() : null,
                link != null ? link.provider() : null, link != null ? link.externalId() : null);
    }
}
