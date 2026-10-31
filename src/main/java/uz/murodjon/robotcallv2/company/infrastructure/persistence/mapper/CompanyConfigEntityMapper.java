package uz.murodjon.robotcallv2.company.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyConfigEntity;

@Component
public class CompanyConfigEntityMapper {

    public CompanyConfig toCompanyConfig(CompanyConfigEntity entity) {
        if (entity == null) {
            return null;
        }
        return new CompanyConfig(
                entity.getId(),
                entity.getCompanyId(),
                entity.getDialWindowStart(),
                entity.getDialWindowEnd(),
                entity.getTimezone(),
                entity.getMaxAttemptsPerPhonePerWeek(),
                entity.getQuietDaysAfterConversation(),
                entity.isRespectPromisedDate(),
                entity.isHumanLikePersonaAllowed(),
                entity.getScorecardReviewThreshold(),
                entity.getTelephonyEngine(),
                entity.getCreatedAt()
        );
    }
}
