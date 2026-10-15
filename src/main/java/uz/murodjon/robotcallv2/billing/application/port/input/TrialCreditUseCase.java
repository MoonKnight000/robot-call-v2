package uz.murodjon.robotcallv2.billing.application.port.input;

import uz.murodjon.robotcallv2.billing.application.dto.TrialCreditResponse;
import uz.murodjon.robotcallv2.billing.domain.entity.TrialCredit;

public interface TrialCreditUseCase {

    /**
     * Gives the company its free trial money, at most once per company. Does nothing when
     * trials are turned off or the company already had one.
     */
    void grantTrialCredit(long companyId);

    /** The company's trial, or null when it never had one. */
    TrialCreditResponse findTrialCredit(long companyId);

    /** Takes back the unspent part of a trial that has ended; a second call does nothing. */
    void expireTrialCredit(TrialCredit trial);
}
