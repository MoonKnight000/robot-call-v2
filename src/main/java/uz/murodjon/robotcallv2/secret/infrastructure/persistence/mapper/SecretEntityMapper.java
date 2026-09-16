package uz.murodjon.robotcallv2.secret.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.secret.domain.entity.Secret;
import uz.murodjon.robotcallv2.secret.infrastructure.persistence.entity.SecretEntity;

@Component
public class SecretEntityMapper {

    public Secret toSecret(SecretEntity entity) {
        return new Secret(
                entity.getId(),
                entity.getCompanyId(),
                entity.getKey(),
                entity.getValue(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
