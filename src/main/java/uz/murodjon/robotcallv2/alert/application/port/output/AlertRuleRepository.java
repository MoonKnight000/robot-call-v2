package uz.murodjon.robotcallv2.alert.application.port.output;

import uz.murodjon.robotcallv2.alert.domain.entity.AlertRule;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertRuleFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.time.Instant;
import java.util.List;

public interface AlertRuleRepository {

    AlertRule create(AlertRule rule);

    /** Replaces the settings of the company's rule; its evaluation state stays. Null when there is no such rule. */
    AlertRule update(long companyId, long id, AlertRule settings);

    void deleteByCompanyIdAndId(long companyId, long id);

    AlertRule findByCompanyIdAndId(long companyId, long id);

    PageableData<AlertRule> findPageByCompanyId(long companyId, AlertRuleFilter filter);

    long countByCompanyId(long companyId);

    /** Every enabled rule of every company — what the scheduler evaluates. */
    List<AlertRule> findEnabled();

    /** Stores an evaluation: the value, when, and — when it fired — {@code evaluatedAt} as the last firing. */
    void recordEvaluation(long id, Double value, Instant evaluatedAt, boolean triggered);
}
