package uz.murodjon.robotcallv2.donotcall.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallRow;
import uz.murodjon.robotcallv2.donotcall.domain.entity.DoNotCall;
import uz.murodjon.robotcallv2.donotcall.infrastructure.persistence.entity.DoNotCallEntity;

/** Mapper between domain, entity, and DTO representations of DoNotCall. */
@Component
public class DoNotCallMapper {

    public DoNotCall entityToDomain(DoNotCallEntity entity) {
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

    public DoNotCallEntity domainToEntity(DoNotCall domain, CompanyEntity company) {
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

    public DoNotCallRow domainToRow(DoNotCall domain, String contactName) {
        return DoNotCallRow.of(domain, contactName);
    }
}
