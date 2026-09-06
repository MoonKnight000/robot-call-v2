package uz.murodjon.robotcallv2.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds the company id carried by the caller's token to a {@code long} controller
 * parameter. The value comes from the authenticated principal only — a request that
 * spells out {@code ?companyId=999} cannot talk an endpoint into serving another
 * tenant's rows.
 *
 * <p>Declare it in the controller <em>interface</em> next to the other web annotations
 * (§3); the implementation keeps the bare parameter.
 *
 * @see CurrentCompanyIdArgumentResolver
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentCompanyId {
}
