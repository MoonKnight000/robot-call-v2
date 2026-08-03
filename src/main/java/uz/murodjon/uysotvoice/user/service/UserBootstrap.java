package uz.murodjon.uysotvoice.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.company.config.CompanyProperties;
import uz.murodjon.uysotvoice.user.config.UserBootstrapProperties;
import uz.murodjon.uysotvoice.user.enums.UserRole;
import uz.murodjon.uysotvoice.user.enums.UserStatus;
import uz.murodjon.uysotvoice.user.repository.UserRepository;

/**
 * Seeds the very first {@code ACTIVE}/{@code ADMIN} account on first startup (ROADMAP
 * E.1) — without this, nobody could ever call {@code POST /api/auth/login} to create
 * the rest of the users through the API. Mirrors {@code SipTrunkBootstrap}: runs every
 * startup, no-op once the default company already has a user.
 */
@Component
public class UserBootstrap {

    private static final Logger log = LoggerFactory.getLogger(UserBootstrap.class);

    private final UserRepository users;
    private final CompanyProperties companyProps;
    private final UserBootstrapProperties props;
    private final PasswordEncoder passwordEncoder;

    public UserBootstrap(UserRepository users, CompanyProperties companyProps,
                          UserBootstrapProperties props, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.companyProps = companyProps;
        this.props = props;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedFirstAdmin() {
        if (users.hasAnyUser(companyProps.defaultId())) {
            return;
        }
        if (!props.configured()) {
            log.warn("No app_user rows and voice-agent.user.bootstrap-admin-email/-password are "
                    + "blank — nobody can log in until one is created directly in the database "
                    + "or the bootstrap properties are set and the app restarted.");
            return;
        }
        String name = props.bootstrapAdminName() == null || props.bootstrapAdminName().isBlank()
                ? "Admin" : props.bootstrapAdminName();
        long id = users.createForCompany(companyProps.defaultId(), name, props.bootstrapAdminUsername(),
                props.bootstrapAdminEmail(), passwordEncoder.encode(props.bootstrapAdminPassword()),
                UserRole.ADMIN, UserStatus.ACTIVE);
        log.info("Seeded first admin user {} ({}) for company {}", id, props.bootstrapAdminEmail(),
                companyProps.defaultId());
    }
}
