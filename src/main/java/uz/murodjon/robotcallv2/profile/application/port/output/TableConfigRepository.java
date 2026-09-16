package uz.murodjon.robotcallv2.profile.application.port.output;

import uz.murodjon.robotcallv2.profile.domain.entity.TableConfig;

public interface TableConfigRepository {

    /** Null when the user has not saved a layout under this key. */
    TableConfig findByUserIdAndConfigKey(long userId, String configKey);

    TableConfig upsert(long companyId, TableConfig config);

    void deleteByUserIdAndConfigKey(long userId, String configKey);
}
