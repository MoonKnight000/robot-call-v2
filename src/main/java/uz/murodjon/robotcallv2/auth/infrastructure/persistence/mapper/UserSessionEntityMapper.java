package uz.murodjon.robotcallv2.auth.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.auth.domain.entity.UserSession;
import uz.murodjon.robotcallv2.auth.infrastructure.persistence.entity.UserSessionEntity;

@Component
public class UserSessionEntityMapper {

    public UserSession toUserSession(UserSessionEntity entity) {
        if (entity == null) {
            return null;
        }
        return new UserSession(
                entity.getId(),
                entity.getCompanyId(),
                entity.getUserId(),
                entity.getRefreshTokenHash(),
                entity.getDevice(),
                entity.getIpAddress(),
                entity.getCreatedAt(),
                entity.getLastActivityAt(),
                entity.getExpiresAt(),
                entity.getRevokedAt()
        );
    }
}
