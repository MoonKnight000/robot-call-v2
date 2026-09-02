package uz.murodjon.robotcallv2.auth.application.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.auth.domain.entity.UserSession;
import uz.murodjon.robotcallv2.auth.infrastructure.persistence.entity.UserSessionEntity;

@Component
public class UserSessionMapper {

    public UserSession entityToDomain(UserSessionEntity e) {
        if (e == null) {
            return null;
        }
        return new UserSession(
                e.getId(),
                e.getCompanyId(),
                e.getUserId(),
                e.getRefreshTokenHash(),
                e.getDevice(),
                e.getIpAddress(),
                e.getCreatedAt(),
                e.getLastActivityAt(),
                e.getExpiresAt(),
                e.getRevokedAt()
        );
    }
}
