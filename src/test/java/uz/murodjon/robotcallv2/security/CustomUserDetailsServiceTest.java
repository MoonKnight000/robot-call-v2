package uz.murodjon.robotcallv2.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import uz.murodjon.robotcallv2.role.application.port.input.RoleUseCase;
import uz.murodjon.robotcallv2.role.domain.entity.Role;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.user.application.port.output.UserRepository;
import uz.murodjon.robotcallv2.user.domain.entity.User;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomUserDetailsServiceTest {

    private UserRepository userRepository;
    private RoleUseCase roleUseCase;
    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleUseCase = mock(RoleUseCase.class);
        userDetailsService = new CustomUserDetailsService(userRepository, roleUseCase);
    }

    @Test
    void loadUserByUsernameReturnsUserDetailsWithAuthorities() {
        User user = new User(1L, 10L, "John Doe", "johndoe", "john@example.com",
                "$2a$12$hashedpassword", 2L, "ADMIN", "Administrator",
                UserStatus.ACTIVE, null, null, null, null, Instant.now(), Instant.now(),
                null, null, null, null, null);

        Role role = new Role(2L, 10L, null, "Custom Admin", "Custom Admin role",
                false, Set.of(Permission.USER_READ, Permission.USER_EDIT), Instant.now());

        when(userRepository.findByUsername("johndoe")).thenReturn(Optional.of(user));
        when(roleUseCase.findRole(10L, 2L)).thenReturn(role);

        UserDetails userDetails = userDetailsService.loadUserByUsername("johndoe");

        assertThat(userDetails.getUsername()).isEqualTo("johndoe");
        assertThat(userDetails.getPassword()).isEqualTo("$2a$12$hashedpassword");
        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isTrue();
        assertThat(userDetails.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("USER_READ", "USER_EDIT");
    }

    @Test
    void loadUserByUsernameThrowsExceptionWhenUserNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("unknown"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void loadUserByUsernameMarksBlockedUserAsLockedAndDisabled() {
        User user = new User(1L, 10L, "Blocked User", "blocked", "blocked@example.com",
                "$2a$12$hash", 2L, "USER", "User",
                UserStatus.BLOCKED, null, null, null, null, Instant.now(), Instant.now(),
                null, null, null, null, null);

        when(userRepository.findByUsername("blocked")).thenReturn(Optional.of(user));
        when(roleUseCase.findRole(10L, 2L)).thenReturn(null);

        UserDetails userDetails = userDetailsService.loadUserByUsername("blocked");

        assertThat(userDetails.isEnabled()).isFalse();
        assertThat(userDetails.isAccountNonLocked()).isFalse();
        assertThat(userDetails.getAuthorities()).isEmpty();
    }
}
