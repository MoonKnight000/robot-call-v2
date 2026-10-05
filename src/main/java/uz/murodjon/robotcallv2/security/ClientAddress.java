package uz.murodjon.robotcallv2.security;

import java.lang.annotation.*;

/**
 * Marks a {@code String} controller parameter that receives the caller's network address,
 * resolved once at the HTTP edge like {@link CurrentCompanyId} so that nothing below the
 * controller has to touch the servlet request.
 *
 * @see ClientAddressArgumentResolver
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ClientAddress {
}
