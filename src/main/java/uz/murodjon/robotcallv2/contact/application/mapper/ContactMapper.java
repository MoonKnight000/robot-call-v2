package uz.murodjon.robotcallv2.contact.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.contact.application.dto.ContactCallHistoryRow;
import uz.murodjon.robotcallv2.report.domain.entity.PhoneCallHistoryEntry;

/** Domain → DTO for the contact endpoints. */
@Component
public class ContactMapper {

    public ContactCallHistoryRow toContactCallHistoryRow(PhoneCallHistoryEntry entry) {
        return new ContactCallHistoryRow(entry.callId(), entry.campaignName(), entry.startedAt(),
                entry.durationSec(), entry.disposition());
    }
}
