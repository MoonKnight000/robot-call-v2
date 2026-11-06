package uz.murodjon.robotcallv2.role.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.role.domain.entity.RoleTemplate;
import uz.murodjon.robotcallv2.role.infrastructure.persistence.entity.RoleTemplateEntity;

@Component
public class RoleTemplateEntityMapper {

    public RoleTemplate toRoleTemplate(RoleTemplateEntity entity) {
        if (entity == null) {
            return null;
        }
        return new RoleTemplate(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.isAssignableByCompany(),
                entity.getPermissions(),
                entity.getCreatedAt()
        );
    }
}
