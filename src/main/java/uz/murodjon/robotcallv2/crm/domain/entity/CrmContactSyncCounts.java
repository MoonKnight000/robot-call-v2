package uz.murodjon.robotcallv2.crm.domain.entity;

/**
 * What one sync run did.
 *
 * @param created contacts it created
 * @param updated contacts it changed
 * @param skipped CRM records it left out — no usable phone, or refused
 */
public record CrmContactSyncCounts(int created, int updated, int skipped) {

    public static final CrmContactSyncCounts NONE = new CrmContactSyncCounts(0, 0, 0);
}
