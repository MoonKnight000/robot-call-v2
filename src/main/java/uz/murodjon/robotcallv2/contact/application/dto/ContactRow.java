package uz.murodjon.robotcallv2.contact.application.dto;

import java.time.Instant;
import java.util.Map;

/**
 * One line of the contacts table: the {@code Contact} fields plus how often the number was
 * called ({@code callCount}, 0 when never) and when last ({@code lastCallAt}, {@code null} when never),
 * and the CRM record it was imported from ({@code crmProvider}/{@code externalId}, {@code null} when none).
 */
public record ContactRow(
        long id,
        String name,
        String phone,
        String address,
        String tags,
        String notes,
        Map<String, String> customFields,
        Instant createdAt,
        long callCount,
        Instant lastCallAt,
        String crmProvider,
        String externalId
) {
}
