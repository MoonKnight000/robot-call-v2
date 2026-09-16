package uz.murodjon.robotcallv2.contact.application.dto;

/** Response for {@code POST /api/contacts/{id}/dnc}. */
public record ContactDncResponse(long contactId, boolean doNotCall) {
}
