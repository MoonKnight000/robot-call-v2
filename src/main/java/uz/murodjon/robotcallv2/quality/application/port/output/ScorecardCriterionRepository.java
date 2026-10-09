package uz.murodjon.robotcallv2.quality.application.port.output;

import uz.murodjon.robotcallv2.quality.domain.entity.ScorecardCriterion;
import uz.murodjon.robotcallv2.quality.domain.entity.ScorecardCriterionFilter;

import java.util.List;

public interface ScorecardCriterionRepository {

    ScorecardCriterion create(long companyId, ScorecardCriterion criterion);

    ScorecardCriterion update(long companyId, long id, ScorecardCriterion criterion);

    void delete(long companyId, long id);

    ScorecardCriterion find(long companyId, long id);

    List<ScorecardCriterion> findAll(long companyId, ScorecardCriterionFilter filter);

    long count(long companyId, ScorecardCriterionFilter filter);

    /** What a call is marked on now, in a stable order. */
    List<ScorecardCriterion> findEnabledByCompanyId(long companyId);
}
