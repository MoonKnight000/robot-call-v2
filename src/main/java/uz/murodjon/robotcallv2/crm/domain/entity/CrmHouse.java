package uz.murodjon.robotcallv2.crm.domain.entity;

/**
 * One residential complex (Uysot {@code house}).
 *
 * @param onSale       whether the complex is currently selling
 * @param address      its street address, or {@code null}
 * @param floors       how many floors it has, or {@code null}
 * @param handOverDate the planned hand-over date as the CRM writes it ({@code dd.MM.yyyy}), or {@code null}
 * @param buildings    its blocks' names, in display order
 */
public record CrmHouse(long id, String name, boolean onSale, String address, Integer floors, String handOverDate,
                       java.util.List<String> buildings) {

    public CrmHouse {
        buildings = buildings == null ? java.util.List.of() : java.util.List.copyOf(buildings);
    }
}
