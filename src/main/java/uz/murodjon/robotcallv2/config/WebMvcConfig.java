package uz.murodjon.robotcallv2.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import uz.murodjon.robotcallv2.security.ClientAddressArgumentResolver;
import uz.murodjon.robotcallv2.security.CurrentCompanyIdArgumentResolver;
import uz.murodjon.robotcallv2.security.RateLimitProperties;

import java.util.List;

/**
 * Registers {@link CurrentCompanyIdArgumentResolver}. Without this registration a
 * {@code @CurrentCompanyId long} parameter falls through to Spring's default
 * request-parameter binding, which would let {@code ?companyId=999} choose the tenant —
 * the resolver and this registration belong together. The same holds for
 * {@link ClientAddressArgumentResolver} and {@code ?clientAddress=}.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitProperties rateLimitProperties;

    public WebMvcConfig(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentCompanyIdArgumentResolver());
        resolvers.add(new ClientAddressArgumentResolver(rateLimitProperties));
    }
}
