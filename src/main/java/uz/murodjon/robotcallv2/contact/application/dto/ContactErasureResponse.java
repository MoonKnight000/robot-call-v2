package uz.murodjon.robotcallv2.contact.application.dto;

/**
 * What {@code DELETE /api/contacts/data} did.
 *
 * @param phone           the erased number, masked — the response is logged and shown
 * @param callsAnonymized calls whose number, transcript and summary were removed
 * @param filesDeleted    recordings and voicemail messages deleted from storage
 */
public record ContactErasureResponse(
        String phone,
        int callsAnonymized,
        int filesDeleted
) {
}
