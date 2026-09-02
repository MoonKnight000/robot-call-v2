package uz.murodjon.robotcallv2.company.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.company.infrastructure.config.CompanyProperties;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;

/**
 * Bootstrap service that ensures the default company (tenant) and its initial configuration
 * exist upon application startup if not already seeded by Flyway migration (ROADMAP Bosqich B).
 */
@Component
public class CompanyBootstrap {

    private static final Logger log = LoggerFactory.getLogger(CompanyBootstrap.class);

    private final CompanyJpaRepository companyJpa;
    private final JdbcTemplate jdbcTemplate;
    private final CompanyProperties props;

    public CompanyBootstrap(CompanyJpaRepository companyJpa, JdbcTemplate jdbcTemplate, CompanyProperties props) {
        this.companyJpa = companyJpa;
        this.jdbcTemplate = jdbcTemplate;
        this.props = props;
    }

    /**
     * Seeds the default company and operational config if absent.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    @Transactional
    public void seedDefaultCompany() {
        long defaultId = props.defaultId();
        if (!companyJpa.existsById(defaultId)) {
            jdbcTemplate.update(
                    "INSERT INTO company(id, name, status, created_at) VALUES (?, ?, ?, now()) ON CONFLICT (id) DO NOTHING",
                    defaultId, "Default Company", CompanyStatus.ACTIVE.name()
            );
            log.info("Seeded default company {} (Default Company)", defaultId);
        }

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM company_config WHERE company_id = ?",
                Integer.class,
                defaultId
        );
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO company_config(company_id, dial_window_start, dial_window_end, timezone, default_language, disclosure_text, created_at) " +
                            "VALUES (?, '07:00:00', '23:00:00', 'Asia/Tashkent', 'uz-UZ', 'Assalomu alaykum! Bu {company} kompaniyasining avtomatik ovozli xizmati. Suhbat yozib olinmoqda.', now()) " +
                            "ON CONFLICT (company_id) DO NOTHING",
                    defaultId
            );
            Long configId = jdbcTemplate.queryForObject(
                    "SELECT id FROM company_config WHERE company_id = ?",
                    Long.class,
                    defaultId
            );
            if (configId != null) {
                jdbcTemplate.update(
                        "INSERT INTO company_config_language(company_config_id, ord, language) VALUES (?, 0, 'uz-UZ'), (?, 1, 'ru-RU') ON CONFLICT DO NOTHING",
                        configId, configId
                );
            }
            log.info("Seeded default company_config for company {}", defaultId);
        }
    }
}
