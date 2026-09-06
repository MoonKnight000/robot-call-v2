package uz.murodjon.robotcallv2.role.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.role.domain.entity.Role;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.role.domain.enums.SystemRole;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleValidatorTest {

    private static final Set<Permission> SOME_PERMISSIONS = Set.of(Permission.CAMPAIGN_READ);

    @Test
    void allowsCreatingUpToTheLimit() {
        assertThatCode(() -> RoleValidator.validateCreate(SOME_PERMISSIONS, RoleValidator.MAX_CUSTOM_ROLES - 1))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesTheEleventhRoleOfACompany() {
        assertThatThrownBy(() -> RoleValidator.validateCreate(SOME_PERMISSIONS, RoleValidator.MAX_CUSTOM_ROLES))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(String.valueOf(RoleValidator.MAX_CUSTOM_ROLES));
    }

    @Test
    void refusesARoleWithoutPermissions() {
        assertThatThrownBy(() -> RoleValidator.validatePermissions(Set.of()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void refusesToGrantAPlatformPermissionToACompanyRole() {
        assertThatThrownBy(() -> RoleValidator.validatePermissions(Set.of(Permission.PLATFORM_ADMIN)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(Permission.PLATFORM_ADMIN.name());
    }

    @Test
    void refusesToEditASystemRole() {
        assertThatThrownBy(() -> RoleValidator.validateEditable(systemRole(SystemRole.ADMIN)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void refusesToDeleteARoleUsersStillHold() {
        assertThatThrownBy(() -> RoleValidator.validateNotInUse(customRole(), 3))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("3");
    }

    @Test
    void onlyPlatformStaffMayGrantDeveloper() {
        Role developer = systemRole(SystemRole.DEVELOPER);

        assertThatThrownBy(() -> RoleValidator.validateAssignable(developer, false))
                .isInstanceOf(ForbiddenException.class);
        assertThatCode(() -> RoleValidator.validateAssignable(developer, true))
                .doesNotThrowAnyException();
    }

    @Test
    void aCompanyAdminMayGrantTheEverydayRoles() {
        assertThatCode(() -> RoleValidator.validateAssignable(systemRole(SystemRole.OPERATOR), false))
                .doesNotThrowAnyException();
        assertThatCode(() -> RoleValidator.validateAssignable(customRole(), false))
                .doesNotThrowAnyException();
    }

    /** The developer role is what it is for: everything the company has, computed, never stored. */
    @Test
    void developerHoldsEveryCompanyPermission() {
        assertThat(systemRole(SystemRole.DEVELOPER).permissions())
                .containsExactlyInAnyOrderElementsOf(Permission.findCompanyPermissions())
                .doesNotContain(Permission.PLATFORM_ADMIN);
    }

    private static Role systemRole(SystemRole systemRole) {
        return new Role(1L, 1L, systemRole.name(), systemRole.label(), null, true, Set.of(), Instant.now());
    }

    private static Role customRole() {
        return new Role(2L, 1L, null, "Sotuv menejeri", null, false, SOME_PERMISSIONS, Instant.now());
    }
}
