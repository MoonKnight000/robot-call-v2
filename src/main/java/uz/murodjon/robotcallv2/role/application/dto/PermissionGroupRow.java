package uz.murodjon.robotcallv2.role.application.dto;

import uz.murodjon.robotcallv2.role.domain.enums.Feature;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.role.domain.enums.PermissionGroup;

import java.util.List;

/** One page's worth of the permission catalog the role editor renders. */
public record PermissionGroupRow(PermissionGroup group, Feature feature, List<Permission> permissions) {
}
