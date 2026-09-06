package uz.murodjon.robotcallv2.role.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.role.application.port.input.RoleUseCase;

/**
 * Makes sure every company owns its system roles before anything tries to assign one —
 * after {@code CompanyBootstrap} (order 10) seeds the default company and before
 * {@code UserBootstrap}, which needs the ADMIN role to seed the first account.
 */
@Component
public class RoleBootstrap {

    private static final Logger log = LoggerFactory.getLogger(RoleBootstrap.class);

    private final CompanyUseCase companyUseCase;
    private final RoleUseCase roleUseCase;

    public RoleBootstrap(CompanyUseCase companyUseCase, RoleUseCase roleUseCase) {
        this.companyUseCase = companyUseCase;
        this.roleUseCase = roleUseCase;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(20)
    public void seedSystemRoles() {
        for (Company company : companyUseCase.findAllForWarmup()) {
            roleUseCase.createSystemRoles(company.id());
        }
        log.info("System roles verified for every company");
    }
}
