package uz.murodjon.robotcallv2.role.application.dto;

import uz.murodjon.robotcallv2.role.domain.entity.RoleTemplate;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.time.Instant;
import java.util.Set;

/** Row for the platform's default-roles page. */
public record RoleTemplateRow(
        long id,
        String code,
        String name,
        String description,
        boolean assignableByCompany,
        Set<Permission> permissions,
        Instant createdAt
) {

    public static RoleTemplateRow of(RoleTemplate template) {
        return new RoleTemplateRow(template.id(), template.code(), template.name(), template.description(),
                template.assignableByCompany(), template.permissions(), template.createdAt());
    }
}
