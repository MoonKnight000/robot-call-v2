package uz.murodjon.robotcallv2.company.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.company.application.port.output.CompanyConfigRepository;
import uz.murodjon.robotcallv2.company.application.port.output.CompanyRepository;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.company.infrastructure.config.CompanyProperties;

/**
 * Bootstrap service that ensures the default company (tenant) and its initial configuration
 * exist upon application startup if not already seeded by Flyway migration (ROADMAP Bosqich B).
 */
@Component
public class CompanyBootstrap {

    private static final Logger log = LoggerFactory.getLogger(CompanyBootstrap.class);

    private static final String DEFAULT_COMPANY_NAME = "Default Company";

    private final CompanyRepository companyRepository;
    private final CompanyConfigRepository companyConfigRepository;
    private final CompanyProperties companyProperties;

    public CompanyBootstrap(CompanyRepository companyRepository,
                            CompanyConfigRepository companyConfigRepository,
                            CompanyProperties companyProperties) {
        this.companyRepository = companyRepository;
        this.companyConfigRepository = companyConfigRepository;
        this.companyProperties = companyProperties;
    }

    /**
     * Seeds the default company and operational config if absent.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    @Transactional
    public void seedDefaultCompany() {
        long defaultId = companyProperties.defaultId();
        if (!companyRepository.existsById(defaultId)) {
            companyRepository.createWithId(defaultId, DEFAULT_COMPANY_NAME);
            log.info("Seeded default company {} ({})", defaultId, DEFAULT_COMPANY_NAME);
        }

        if (!companyConfigRepository.existsByCompanyId(defaultId)) {
            companyConfigRepository.create(defaultId, CompanyConfig.defaults());
            log.info("Seeded default company_config for company {}", defaultId);
        }
    }
}
