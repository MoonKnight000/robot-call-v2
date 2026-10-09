package uz.murodjon.robotcallv2.promise.application.port.output;

import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromise;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseFilter;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseStats;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseTally;
import uz.murodjon.robotcallv2.promise.domain.enums.PaymentPromiseStatus;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface PaymentPromiseRepository {

    /** Writes the promise unless its call already has one — the result may be written twice. */
    void createIgnoringConflict(PaymentPromise promise);

    /** Cancels the number's open promises from other calls: the newest promise is the one that stands. */
    void cancelOpenByCompanyIdAndPhone(long companyId, String phone, long exceptCallAttemptId, Instant resolvedAt);

    PaymentPromise findByCompanyIdAndId(long companyId, long id);

    PageableData<PaymentPromise> findPageByCompanyId(long companyId, PaymentPromiseFilter filter);

    PaymentPromiseStats findStatsByCompanyId(long companyId, LocalDate from, LocalDate to);

    /** Open promises of every company, by id, after {@code afterId} — the scheduler walks them in batches. */
    List<PaymentPromise> findOpenAfterId(long afterId, int limit);

    /** Moves an open promise to {@code status}; false when it was no longer open. */
    boolean updateStatusIfOpen(long id, PaymentPromiseStatus status, Instant resolvedAt);

    void markReminded(long id, Instant sentAt);

    /** Each number's kept and broken promises made since {@code since}; numbers with none are left out. */
    Map<String, PaymentPromiseTally> findTalliesByPhones(long companyId, Collection<String> phones, Instant since);
}
