package uz.murodjon.robotcallv2.crm.domain.entity;

/**
 * One residential complex (Uysot {@code house}).
 *
 * @param onSale   whether the complex is currently selling
 * @param currency the currency its prices are in (e.g. {@code USD}), or {@code null}
 */
public record CrmHouse(long id, String name, boolean onSale, String branchName, String currency) {
}
