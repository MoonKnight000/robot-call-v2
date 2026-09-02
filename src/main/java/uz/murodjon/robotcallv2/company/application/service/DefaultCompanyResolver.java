package uz.murodjon.robotcallv2.company.application.service;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.infrastructure.config.CompanyProperties;

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
