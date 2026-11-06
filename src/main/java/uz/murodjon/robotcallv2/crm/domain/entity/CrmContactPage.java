package uz.murodjon.robotcallv2.crm.domain.entity;

import java.util.List;

/**
 * One page of CRM contacts, oldest change first.
 *
 * @param nextPageToken what to pass for the next page; {@code null} — this was the last
 */
public record CrmContactPage(List<CrmContactRecord> records, String nextPageToken) {

    public CrmContactPage {
        records = records == null ? List.of() : List.copyOf(records);
    }

    public static CrmContactPage last(List<CrmContactRecord> records) {
        return new CrmContactPage(records, null);
    }
}
