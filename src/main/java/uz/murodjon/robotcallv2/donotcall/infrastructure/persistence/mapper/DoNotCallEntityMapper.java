package uz.murodjon.robotcallv2.donotcall.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.donotcall.domain.entity.DoNotCall;
import uz.murodjon.robotcallv2.donotcall.infrastructure.persistence.entity.DoNotCallEntity;

/** Entity ↔ domain for the do-not-call list. */
@Component
public class DoNotCallEntityMapper {

    public DoNotCall toDoNotCall(DoNotCallEntity entity) {
        if (entity == null) {
            return null;
        }
        return new DoNotCall(
                entity.getId(),
                entity.getPhone(),
                entity.getReason(),
                entity.getSource(),
                entity.getCreatedAt(),
                entity.getRemovedAt(),
                entity.getRemovedBy(),
                entity.getCompanyId()
        );
    }

    public DoNotCallEntity toDoNotCallEntity(DoNotCall domain, CompanyEntity company) {
        if (domain == null) {
            return null;
        }
        DoNotCallEntity entity = new DoNotCallEntity();
        entity.setId(domain.getId());
        entity.setPhone(domain.getPhone());
        entity.setReason(domain.getReason());
        entity.setSource(domain.getSource());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setRemovedAt(domain.getRemovedAt());
        entity.setRemovedBy(domain.getRemovedBy());
        entity.setCompany(company);
        return entity;
    }
}
