package uz.murodjon.robotcallv2.contact.domain.entity;

import java.time.Instant;

/**
 * The CRM record a contact was imported from.
 *
 * @param provider   the CRM product's name ({@code BITRIX24}, {@code AMOCRM}, …)
 * @param externalId the record's id in that CRM
 * @param syncedAt   when the record was last imported
 */
public record ContactCrmLink(long contactId, String provider, String externalId, Instant syncedAt) {
}
