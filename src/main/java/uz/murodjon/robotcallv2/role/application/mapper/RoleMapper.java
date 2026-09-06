package uz.murodjon.robotcallv2.role.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.role.domain.entity.Role;
import uz.murodjon.robotcallv2.role.infrastructure.persistence.entity.RoleEntity;

@Component
public class RoleMapper {

    public Role toRole(RoleEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Role(
                entity.getId(),
                entity.getCompanyId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.isSystem(),
                entity.getPermissions(),
                entity.getCreatedAt()
        );
    }
}
