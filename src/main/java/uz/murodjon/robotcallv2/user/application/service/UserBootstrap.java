package uz.murodjon.robotcallv2.user.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.company.infrastructure.config.CompanyProperties;
import uz.murodjon.robotcallv2.role.application.port.input.RoleUseCase;
import uz.murodjon.robotcallv2.role.domain.entity.Role;
import uz.murodjon.robotcallv2.role.domain.enums.SystemRole;
import uz.murodjon.robotcallv2.user.application.port.output.UserRepository;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;
import uz.murodjon.robotcallv2.user.infrastructure.config.UserBootstrapProperties;

/**
 * Seeds the very first ACTIVE/ADMIN account on first startup (ROADMAP E.1).
 */
@Component
public class UserBootstrap {

    private static final Logger log = LoggerFactory.getLogger(UserBootstrap.class);

    private final UserRepository users;
    private final RoleUseCase roleUseCase;
    private final CompanyProperties companyProperties;
    private final UserBootstrapProperties userBootstrapProperties;
    private final PasswordEncoder passwordEncoder;

    public UserBootstrap(UserRepository users, RoleUseCase roleUseCase, CompanyProperties companyProperties,
                         UserBootstrapProperties userBootstrapProperties, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roleUseCase = roleUseCase;
        this.companyProperties = companyProperties;
        this.userBootstrapProperties = userBootstrapProperties;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedFirstAdmin() {
        long companyId = companyProperties.defaultId();
        if (users.hasAnyUser(companyId)) {
            return;
        }
        if (!userBootstrapProperties.configured()) {
            log.warn("No app_user rows and voice-agent.user.bootstrap-admin-email/-password are "
                    + "blank — nobody can log in until one is created directly in the database "
                    + "or the bootstrap properties are set and the app restarted.");
            return;
        }
        Role adminRole = roleUseCase.findRoleByCode(companyId, SystemRole.ADMIN.name());
        if (adminRole == null) {
            log.error("Company {} has no {} role — cannot seed the first admin (RoleBootstrap "
                    + "should have created it)", companyId, SystemRole.ADMIN);
            return;
        }
        String name = userBootstrapProperties.bootstrapAdminName() == null || userBootstrapProperties.bootstrapAdminName().isBlank()
                ? "Admin" : userBootstrapProperties.bootstrapAdminName();
        long id = users.create(companyId, name, userBootstrapProperties.bootstrapAdminUsername(),
                userBootstrapProperties.bootstrapAdminEmail(), passwordEncoder.encode(userBootstrapProperties.bootstrapAdminPassword()),
                adminRole.id(), UserStatus.ACTIVE);
        log.info("Seeded first admin user {} ({}) for company {}", id, userBootstrapProperties.bootstrapAdminEmail(), companyId);
    }
}
