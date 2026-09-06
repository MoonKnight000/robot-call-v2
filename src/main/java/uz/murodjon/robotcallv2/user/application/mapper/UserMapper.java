package uz.murodjon.robotcallv2.user.application.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.role.infrastructure.persistence.entity.RoleEntity;
import uz.murodjon.robotcallv2.user.domain.entity.User;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.entity.UserEntity;

@Component
public class UserMapper {

    public User entityToDomain(UserEntity e) {
        if (e == null) {
            return null;
        }
        RoleEntity role = e.getRole();
        return new User(
                e.getId(),
                e.getCompanyId(),
                e.getName(),
                e.getUsername(),
                e.getEmail(),
                e.getPasswordHash(),
                role == null ? 0L : role.getId(),
                role == null ? null : role.getCode(),
                role == null ? null : role.getName(),
                e.getStatus(),
                e.getInviteTokenHash(),
                e.getInviteExpiresAt(),
                e.getResetTokenHash(),
                e.getResetExpiresAt(),
                e.getLastLoginAt(),
                e.getCreatedAt(),
                e.getPhone(),
                e.getPosition(),
                e.getAvatarFileId(),
                e.getCallColumns(),
                e.getSipExtension()
        );
    }
}
