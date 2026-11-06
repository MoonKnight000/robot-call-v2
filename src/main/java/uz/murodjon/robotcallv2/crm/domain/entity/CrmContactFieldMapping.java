package uz.murodjon.robotcallv2.crm.domain.entity;

/**
 * One CRM contact field imported into a contact field.
 *
 * @param crmField     the CRM's field id — Bitrix24 {@code UF_CRM_…}/{@code EMAIL}, amoCRM the
 *                     custom field id or its code ({@code EMAIL})
 * @param contactField the company contact field's key
 */
public record CrmContactFieldMapping(String crmField, String contactField) {
}
