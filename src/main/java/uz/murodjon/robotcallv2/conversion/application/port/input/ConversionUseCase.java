package uz.murodjon.robotcallv2.conversion.application.port.input;

import uz.murodjon.robotcallv2.conversion.application.dto.ConversionGoalRequest;
import uz.murodjon.robotcallv2.conversion.application.dto.ConversionResultResponse;
import uz.murodjon.robotcallv2.conversion.application.dto.ReportConversionRequest;
import uz.murodjon.robotcallv2.conversion.domain.entity.ConversionGoal;
import uz.murodjon.robotcallv2.conversion.domain.entity.VariantConversions;

import java.util.List;

/** Conversion goals, and the events a company's own systems report against them. */
public interface ConversionUseCase {

    List<ConversionGoal> findGoals(long companyId);

    /** Creates the goal, or updates the one already under that key. */
    ConversionGoal upsertGoal(long companyId, ConversionGoalRequest request);

    void deleteGoal(long companyId, long id);

    /**
     * Records one reported event and works out which call, if any, earned it.
     *
     * <p>Attribution runs now rather than on a schedule so the poster is told straight
     * away whether its event matched — a webhook that always answers 200 and drops half
     * the events is how a campaign's numbers go wrong unnoticed.
     */
    ConversionResultResponse reportConversion(long companyId, ReportConversionRequest request);

    /**
     * What each A/B variant of a campaign actually earned, for the A/B report.
     *
     * <p>Empty for a company that has configured no goals — which is why the report keeps
     * its disposition-based columns alongside these rather than replacing them.
     */
    List<VariantConversions> findVariantConversions(long companyId, long campaignId);
}
