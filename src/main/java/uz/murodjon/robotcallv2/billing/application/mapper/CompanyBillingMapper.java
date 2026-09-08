package uz.murodjon.robotcallv2.billing.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.billing.domain.entity.CompanyBilling;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.CompanyBillingEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

@Component
public class CompanyBillingMapper {

    public CompanyBilling toCompanyBilling(CompanyBillingEntity entity) {
        if (entity == null) {
            return null;
        }
        return new CompanyBilling(
                entity.getId(),
                entity.getCompanyId(),
                entity.getPlanCode(),
                entity.getPlanName(),
                entity.getBalanceUzs() != null ? entity.getBalanceUzs() : 0L,
                entity.getReservedUzs() != null ? entity.getReservedUzs() : 0L,
                Boolean.TRUE.equals(entity.getAutoRecharge()),
                entity.getNextBillingDate(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public CompanyBillingEntity toEntity(CompanyBilling billing, CompanyEntity company) {
        if (billing == null) {
            return null;
        }
        CompanyBillingEntity entity = new CompanyBillingEntity();
        entity.setId(billing.id());
        entity.setCompany(company);
        entity.setPlanCode(billing.planCode());
        entity.setPlanName(billing.planName());
        entity.setBalanceUzs(billing.balanceUzs());
        entity.setReservedUzs(billing.reservedUzs());
        entity.setAutoRecharge(billing.autoRecharge());
        entity.setNextBillingDate(billing.nextBillingDate());
        entity.setCreatedAt(billing.createdAt());
        entity.setUpdatedAt(billing.updatedAt());
        return entity;
    }
}
