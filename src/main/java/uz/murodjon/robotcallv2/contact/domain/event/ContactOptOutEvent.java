package uz.murodjon.robotcallv2.contact.domain.event;

/**
 * A contact asked not to be called again. Published by the contact feature and handled, on the
 * publishing thread, by the do-not-call list — so contacts need not know the list exists.
 *
 * @param phone  the contact's number, as stored on the contact
 * @param reason why the number is being listed, as it is recorded on the entry
 */
public record ContactOptOutEvent(long companyId, String phone, String reason) {
}
