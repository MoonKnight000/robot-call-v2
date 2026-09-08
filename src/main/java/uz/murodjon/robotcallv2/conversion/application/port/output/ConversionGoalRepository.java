package uz.murodjon.robotcallv2.conversion.application.port.output;

import uz.murodjon.robotcallv2.conversion.domain.entity.ConversionGoal;

import java.util.List;
import java.util.Optional;

public interface ConversionGoalRepository {

    ConversionGoal save(ConversionGoal goal);

    List<ConversionGoal> findByCompanyId(long companyId);

    Optional<ConversionGoal> findByCompanyIdAndGoalKey(long companyId, String goalKey);

    Optional<ConversionGoal> findByCompanyIdAndId(long companyId, long id);
}
