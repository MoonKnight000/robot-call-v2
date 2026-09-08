package uz.murodjon.robotcallv2.billing.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.billing.domain.entity.LedgerEntry;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.BillingLedgerEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

/**
 * Write-only: the ledger is append-only and nothing reads a row back through the port,
 * so there is no entity → domain direction here.
 */
@Component
public class BillingLedgerMapper {

    /** The id and {@code createdAt} belong to the ledger row itself, so they are not copied in. */
    public BillingLedgerEntity toEntity(LedgerEntry entry, CompanyEntity company) {
        if (entry == null) {
            return null;
        }
        BillingLedgerEntity entity = new BillingLedgerEntity();
        entity.setCompany(company);
        entity.setEntryType(entry.entryType());
        entity.setAmountUzs(entry.amountUzs());
        entity.setBalanceBeforeUzs(entry.balanceBeforeUzs());
        entity.setBalanceAfterUzs(entry.balanceAfterUzs());
        entity.setReferenceType(entry.referenceType());
        entity.setReferenceId(entry.referenceId());
        entity.setIdempotencyKey(entry.idempotencyKey());
        return entity;
    }
}
