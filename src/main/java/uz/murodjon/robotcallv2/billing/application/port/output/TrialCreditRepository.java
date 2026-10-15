package uz.murodjon.robotcallv2.billing.application.port.output;

import uz.murodjon.robotcallv2.billing.domain.entity.TrialCredit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TrialCreditRepository {

    Optional<TrialCredit> findByCompanyId(long companyId);

    /**
     * Writes the trial unless the company already has one.
     *
     * @return true when this call wrote it — the unique company key settles two racing grants
     */
    boolean create(TrialCredit trial);

    /** Trials whose time is up and whose remainder has not been taken back yet. */
    List<TrialCredit> findUnsettledExpiredBefore(Instant now);

    /**
     * Marks the trial settled, unless it already is.
     *
     * @return true when this call settled it — only one of two racing sweeps may take money back
     */
    boolean settle(long companyId, long clawbackUzs, Instant settledAt);
}
