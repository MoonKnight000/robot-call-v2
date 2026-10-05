package uz.murodjon.robotcallv2.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.role.application.port.input.RoleUseCase;
import uz.murodjon.robotcallv2.role.domain.entity.Role;
import uz.murodjon.robotcallv2.user.application.port.output.UserRepository;
import uz.murodjon.robotcallv2.user.domain.entity.User;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.util.Collections;
import java.util.List;

/**
 * Standard Spring Security {@link UserDetailsService} implementation.
 *
 * <p>Loads the user by username from the database along with their role's permissions as authorities.
 * Providing this bean ensures Spring Boot does not auto-configure an in-memory user with a
 * generated temporary security password.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleUseCase roleUseCase;

    public CustomUserDetailsService(UserRepository userRepository, RoleUseCase roleUseCase) {
        this.userRepository = userRepository;
        this.roleUseCase = roleUseCase;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        Role role = roleUseCase.findRole(user.companyId(), user.roleId());
        List<SimpleGrantedAuthority> authorities = role == null
                ? Collections.emptyList()
                : role.permissions().stream()
                        .map(p -> new SimpleGrantedAuthority(p.name()))
                        .toList();

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.username())
                .password(user.passwordHash() != null ? user.passwordHash() : "")
                .disabled(user.status() != UserStatus.ACTIVE)
                .accountLocked(user.status() == UserStatus.BLOCKED)
                .authorities(authorities)
                .build();
    }
}
