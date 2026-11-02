package uz.murodjon.robotcallv2.integration.domain.entity;

/**
 * What a group of integrations is for — how the catalog lists them (CRM, file manager, SMS...).
 *
 * @param key       stable key, UPPER_SNAKE_CASE
 * @param name      the heading companies see
 * @param sortOrder position among the categories, ascending
 */
public record IntegrationCategory(String key, String name, int sortOrder) {
}
