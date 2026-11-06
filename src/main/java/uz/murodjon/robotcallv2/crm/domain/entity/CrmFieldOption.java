package uz.murodjon.robotcallv2.crm.domain.entity;

/**
 * One contact field the CRM offers for mapping.
 *
 * @param id    what a {@link CrmContactFieldMapping#crmField()} names
 * @param label the CRM's own title of the field
 * @param type  the CRM's own type name ({@code string}, {@code enumeration}, …), for display only
 */
public record CrmFieldOption(String id, String label, String type) {
}
