package uz.murodjon.robotcallv2.user.application.service;

import uz.murodjon.robotcallv2.user.domain.enums.UserRole;

import java.util.Optional;

/**
 * The app_user making the current request, if any (ROADMAP E.1).
 */
public interface CurrentUser {

    Optional<Long> id();

    Optional<UserRole> role();
}
