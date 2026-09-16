package uz.murodjon.robotcallv2.profile.infrastructure.persistence.mapper;

import uz.murodjon.robotcallv2.profile.domain.entity.TableConfig;
import uz.murodjon.robotcallv2.profile.infrastructure.persistence.entity.TableConfigEntity;

public final class TableConfigEntityMapper {

    private TableConfigEntityMapper() {
    }

    public static TableConfig toTableConfig(TableConfigEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TableConfig(
                entity.getId(),
                entity.getCompanyId(),
                entity.getUserId(),
                entity.getConfigKey(),
                entity.getConfigValue(),
                entity.getUpdatedAt()
        );
    }
}
