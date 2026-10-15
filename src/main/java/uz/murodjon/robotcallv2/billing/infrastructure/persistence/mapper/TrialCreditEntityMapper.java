package uz.murodjon.robotcallv2.billing.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.billing.domain.entity.TrialCredit;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.TrialCreditEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

@Component
public class TrialCreditEntityMapper {

    public TrialCredit toTrialCredit(TrialCreditEntity entity) {
        return new TrialCredit(
                entity.getId(),
                entity.getCompanyId(),
                entity.getCreditUzs(),
                entity.getGrantedAt(),
                entity.getExpiresAt(),
                entity.getSettledAt(),
                entity.getClawbackUzs());
    }

    public TrialCreditEntity toTrialCreditEntity(TrialCredit trial, CompanyEntity company) {
        TrialCreditEntity entity = new TrialCreditEntity();
        entity.setCompany(company);
        entity.setCreditUzs(trial.creditUzs());
        entity.setGrantedAt(trial.grantedAt());
        entity.setExpiresAt(trial.expiresAt());
        entity.setSettledAt(trial.settledAt());
        entity.setClawbackUzs(trial.clawbackUzs());
        return entity;
    }
}
