package uz.murodjon.robotcallv2.contact.application.dto;

import uz.murodjon.robotcallv2.contact.domain.entity.Contact;

import java.util.List;

/** GET /api/contacts/{id} response: profile + call-history timeline (§10.8 drawer). */
public record ContactDetail(Contact contact, List<ContactCallHistoryRow> callHistory) {
}
