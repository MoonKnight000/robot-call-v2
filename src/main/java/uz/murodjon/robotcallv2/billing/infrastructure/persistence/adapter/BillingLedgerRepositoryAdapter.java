package uz.murodjon.robotcallv2.billing.infrastructure.persistence.adapter;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.billing.application.port.output.BillingLedgerRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.DailySpend;
import uz.murodjon.robotcallv2.billing.domain.entity.ExtraCharge;
import uz.murodjon.robotcallv2.billing.domain.entity.LedgerEntry;
import uz.murodjon.robotcallv2.billing.domain.entity.MonthlySpend;
import uz.murodjon.robotcallv2.billing.domain.enums.LedgerEntryType;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.mapper.BillingLedgerEntityMapper;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository.BillingLedgerJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
public class BillingLedgerRepositoryAdapter implements BillingLedgerRepository {

    private final BillingLedgerJpaRepository jpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final BillingLedgerEntityMapper mapper;

    public BillingLedgerRepositoryAdapter(BillingLedgerJpaRepository jpaRepository,
                                          CompanyJpaRepository companyJpaRepository,
                                          BillingLedgerEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public boolean append(LedgerEntry entry) {
        // Checked first for the common case, then again by the unique index: two retries
        // arriving together both pass the check, and only the index can settle that.
        if (jpaRepository.existsByCompanyIdAndIdempotencyKey(entry.companyId(), entry.idempotencyKey())) {
            return false;
        }
        try {
            CompanyEntity company = companyJpaRepository.getReferenceById(entry.companyId());
            jpaRepository.save(mapper.toBillingLedgerEntity(entry, company));
            return true;
        } catch (DataIntegrityViolationException alreadyWritten) {
            return false;
        }
    }

    @Override
    public long sumAmountByCompanyIdAndEntryTypeSince(long companyId, LedgerEntryType entryType, Instant since) {
        return jpaRepository.sumAmount(companyId, entryType, since);
    }

    @Override
    public List<ExtraCharge> findExtraCharges(long companyId, Instant from, Instant to) {
        return jpaRepository.findExtraCharges(companyId, from, to).stream()
                .map(row -> new ExtraCharge((String) row[0], number(row[1]), number(row[2])))
                .toList();
    }

    @Override
    public List<DailySpend> findDailyExtraSpend(long companyId, Instant from, Instant to) {
        return jpaRepository.findDailyExtraSpend(companyId, from, to).stream()
                .map(row -> new DailySpend(localDate(row[0]), number(row[1])))
                .toList();
    }

    @Override
    public List<MonthlySpend> findMonthlyExtraSpend(long companyId, int limit) {
        return jpaRepository.findMonthlyExtraSpend(companyId, limit).stream()
                .map(row -> new MonthlySpend((String) row[0], number(row[1]), 0))
                .toList();
    }

    /** A native {@code date} column arrives as java.sql.Date or LocalDate depending on the driver path. */
    private static LocalDate localDate(Object value) {
        return value instanceof java.sql.Date date ? date.toLocalDate() : (LocalDate) value;
    }

    /** SUM over a bigint answers as BigInteger, COUNT as Long — both arrive here. */
    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
