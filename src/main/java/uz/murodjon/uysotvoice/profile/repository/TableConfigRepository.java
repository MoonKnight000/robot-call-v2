package uz.murodjon.uysotvoice.profile.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.profile.entity.TableConfigEntity;

import java.time.Instant;

/** JPA-backed DAO for {@code user_table_config} (backend-uchun-talablar.md §1). */
@Repository
public class TableConfigRepository {

    private final TableConfigJpaRepository jpa;
    private final CurrentCompany company;

    public TableConfigRepository(TableConfigJpaRepository jpa, CurrentCompany company) {
        this.jpa = jpa;
        this.company = company;
    }

    public String find(long userId, String configKey) {
        return jpa.findByUserIdAndConfigKey(userId, configKey).map(TableConfigEntity::getConfigValue).orElse(null);
    }

    /**
     * Upserts — one row per (user, key). {@code configValueJson == null} deletes the
     * row instead of writing {@code NULL} into the {@code NOT NULL} JSON column, which
     * doubles as "reset to default" for the caller.
     */
    public String save(long userId, String configKey, String configValueJson) {
        if (configValueJson == null) {
            jpa.deleteByUserIdAndConfigKey(userId, configKey);
            return null;
        }
        TableConfigEntity entity = jpa.findByUserIdAndConfigKey(userId, configKey).orElseGet(TableConfigEntity::new);
        entity.setCompanyId(company.id());
        entity.setUserId(userId);
        entity.setConfigKey(configKey);
        entity.setConfigValue(configValueJson);
        entity.setUpdatedAt(Instant.now());
        return jpa.save(entity).getConfigValue();
    }
}
