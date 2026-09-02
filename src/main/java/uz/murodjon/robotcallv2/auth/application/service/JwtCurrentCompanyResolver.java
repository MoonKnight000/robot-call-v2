package uz.murodjon.robotcallv2.auth.application.service;

import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.auth.application.dto.AuthenticatedUser;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.company.application.service.DefaultCompanyResolver;

@Component
@Primary
public class JwtCurrentCompanyResolver implements CurrentCompany {

    private final DefaultCompanyResolver fallback;

    public JwtCurrentCompanyResolver(DefaultCompanyResolver fallback) {
        this.fallback = fallback;
    }

    @Override
    public long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user.companyId();
        }
        return fallback.id();
    }
}
