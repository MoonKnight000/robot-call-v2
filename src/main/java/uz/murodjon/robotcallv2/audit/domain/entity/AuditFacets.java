package uz.murodjon.robotcallv2.audit.domain.entity;

import java.util.List;

/**
 * The values the company's audit log actually holds — what the panel offers in its filter
 * dropdowns, so a filter can never be set to something that matches nothing.
 */
public record AuditFacets(
        List<String> actors,
        List<String> actions,
        List<String> entities
) {
}
