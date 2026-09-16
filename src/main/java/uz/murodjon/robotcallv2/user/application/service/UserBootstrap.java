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

    private final UserRepository userRepository;
    private final RoleUseCase roleUseCase;
    private final CompanyProperties companyProperties;
    private final UserBootstrapProperties userBootstrapProperties;
    private final PasswordEncoder passwordEncoder;

    public UserBootstrap(UserRepository userRepository, RoleUseCase roleUseCase, CompanyProperties companyProperties,
                         UserBootstrapProperties userBootstrapProperties, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleUseCase = roleUseCase;
        this.companyProperties = companyProperties;
        this.userBootstrapProperties = userBootstrapProperties;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * BCrypt digest of the password R__seed_data.sql used to ship for its bootstrap account
     * (removed 2026-09-12). Kept only to recognise a database that still carries it.
     */
    private static final String RETIRED_SEED_PASSWORD_HASH =
            "$2a$10$6cGgfpkqI8lHvRx7rlHJKuW7PhH1XttNnYBGhe.lc4rqSMORrizFO";

    @EventListener(ApplicationReadyEvent.class)
    public void seedFirstAdmin() {
        long companyId = companyProperties.defaultId();
        if (userRepository.hasAnyUser(companyId)) {
            if (userRepository.existsByPasswordHash(RETIRED_SEED_PASSWORD_HASH)) {
                log.error("An account still uses the password that used to be committed in "
                        + "R__seed_data.sql — it is public knowledge. Change it now "
                        + "(PUT /api/profile/password or a password reset).");
            }
            return;
        }
        if (!userBootstrapProperties.configured()) {
            log.warn("No app_user rows and voice-agent.user.bootstrap-admin-username/-email/-password are "
                    + "blank, or the password is shorter than 10 characters or still a CHANGE_ME placeholder — "
                    + "nobody can log in until one is created directly in the database "
                    + "or the bootstrap properties are set and the app restarted.");
            return;
        }
        // SUPERADMIN, not ADMIN: only SUPERADMIN holds PLATFORM_ADMIN, and the bootstrap
        // owner has to be able to reach company CRUD and PUT /api/settings/engine.
        Role adminRole = roleUseCase.findRoleByCode(companyId, SystemRole.SUPERADMIN.name());
        if (adminRole == null) {
            log.error("Company {} has no {} role — cannot seed the first admin (RoleBootstrap "
                    + "should have created it)", companyId, SystemRole.SUPERADMIN);
            return;
        }
        String name = userBootstrapProperties.bootstrapAdminName() == null || userBootstrapProperties.bootstrapAdminName().isBlank()
                ? "Admin" : userBootstrapProperties.bootstrapAdminName();
        long id = userRepository.create(companyId, name, userBootstrapProperties.bootstrapAdminUsername(),
                userBootstrapProperties.bootstrapAdminEmail(), passwordEncoder.encode(userBootstrapProperties.bootstrapAdminPassword()),
                adminRole.id(), UserStatus.ACTIVE);
        log.info("Seeded first admin user {} ({}) for company {}", id, userBootstrapProperties.bootstrapAdminEmail(), companyId);
    }
}
