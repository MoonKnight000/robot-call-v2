package uz.murodjon.robotcallv2.campaign.application.port.output;

import uz.murodjon.robotcallv2.crm.domain.entity.Debtor;

import java.util.List;

/**
 * Today's overdue clients, for a campaign whose target source is a CRM rather than a URL.
 *
 * <p>Implemented per CRM ({@code UysotDebtorSourceAdapter}); the campaign side only asks
 * for the list and turns it into targets.
 */
public interface DebtorSourcePort {

    /**
     * @param limit most debtors to return. Bounded because the campaign is about to dial
     *              every one of them, and because reaching a phone number costs several
     *              requests against an API with a per-minute rate limit
     * @return debtors with a callable phone number, most overdue first; empty when the CRM
     *         has none or is not configured
     */
    List<Debtor> findOverdueDebtors(long companyId, int limit);
}
