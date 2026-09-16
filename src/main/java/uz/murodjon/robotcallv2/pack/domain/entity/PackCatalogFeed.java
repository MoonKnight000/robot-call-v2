package uz.murodjon.robotcallv2.pack.domain.entity;

/**
 * The catalog a pack's agent searches (UNIVERSAL-OPERATOR-PLAN B.4). Where it is fetched
 * from is not here: that is the company's own feed address, given at install time.
 *
 * @param feedKeyField   the column every row is keyed by — the SKU the order tool is handed
 * @param refreshMinutes how often the feed is fetched again; null never
 */
public record PackCatalogFeed(String name, String feedKeyField, Integer refreshMinutes) {
}
