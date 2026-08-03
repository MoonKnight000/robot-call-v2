package uz.murodjon.uysotvoice.contact.dto;

import java.util.List;

/** {@code GET /api/contacts/{id}} response: profile + call-history timeline (§10.8 drawer). */
public record ContactDetail(Contact contact, List<ContactCallHistoryRow> callHistory) {
}
