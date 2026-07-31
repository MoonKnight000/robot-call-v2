package uz.murodjon.uysotvoice.company.service;

import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.company.config.CompanyProperties;

/**
 * {@link CurrentCompany} backed by a single configured constant (ROADMAP Bosqich B —
 * "vaqtincha bitta default company_id"). See {@link CurrentCompany} for why every
 * caller depends on the interface rather than this class or {@link CompanyProperties}
 * directly.
 */
@Component
public class DefaultCompanyResolver implements CurrentCompany {

    private final CompanyProperties props;

    public DefaultCompanyResolver(CompanyProperties props) {
        this.props = props;
    }

    @Override
    public long id() {
        return props.defaultId();
    }
}
