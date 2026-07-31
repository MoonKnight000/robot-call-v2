package uz.murodjon.uysotvoice.company.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import uz.murodjon.uysotvoice.company.service.CurrentCompany;

/**
 * Bound from {@code voice-agent.company.*} (ROADMAP Bosqich B).
 *
 * @param defaultId the company every request is scoped to until real per-request
 *                  company resolution exists — see {@link CurrentCompany}. Matches the
 *                  bootstrap row {@code V12__company.sql} seeds as id 1
 */
@ConfigurationProperties(prefix = "voice-agent.company")
public record CompanyProperties(long defaultId) {

    public CompanyProperties {
        if (defaultId <= 0) {
            defaultId = 1;
        }
    }
}
