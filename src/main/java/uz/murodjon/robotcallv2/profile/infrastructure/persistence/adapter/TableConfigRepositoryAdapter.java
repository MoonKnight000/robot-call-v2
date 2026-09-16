package uz.murodjon.robotcallv2.profile.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.profile.application.port.output.TableConfigRepository;
import uz.murodjon.robotcallv2.profile.domain.entity.TableConfig;
import uz.murodjon.robotcallv2.profile.infrastructure.persistence.entity.TableConfigEntity;
import uz.murodjon.robotcallv2.profile.infrastructure.persistence.mapper.TableConfigEntityMapper;
import uz.murodjon.robotcallv2.profile.infrastructure.persistence.repository.TableConfigJpaRepository;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.entity.UserEntity;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.repository.UserJpaRepository;

import java.time.Instant;

@Component
public class TableConfigRepositoryAdapter implements TableConfigRepository {

    private final TableConfigJpaRepository tableConfigJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final CompanyJpaRepository companyJpaRepository;

    public TableConfigRepositoryAdapter(TableConfigJpaRepository tableConfigJpaRepository,
                                        UserJpaRepository userJpaRepository,
                                        CompanyJpaRepository companyJpaRepository) {
        this.tableConfigJpaRepository = tableConfigJpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.companyJpaRepository = companyJpaRepository;
    }

    @Override
    public TableConfig findByUserIdAndConfigKey(long userId, String configKey) {
        return tableConfigJpaRepository.findByUserIdAndConfigKey(userId, configKey)
                .map(TableConfigEntityMapper::toTableConfig).orElse(null);
    }

    @Override
    @Transactional
    public TableConfig upsert(long companyId, TableConfig config) {
        UserEntity user = userJpaRepository.getReferenceById(config.userId());
        CompanyEntity comp = companyJpaRepository.getReferenceById(companyId);

        TableConfigEntity entity = tableConfigJpaRepository.findByUserIdAndConfigKey(config.userId(), config.configKey())
                .orElseGet(TableConfigEntity::new);
        entity.setCompany(comp);
        entity.setUser(user);
        entity.setConfigKey(config.configKey());
        entity.setConfigValue(config.configValue());
        entity.setUpdatedAt(Instant.now());
        return TableConfigEntityMapper.toTableConfig(tableConfigJpaRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteByUserIdAndConfigKey(long userId, String configKey) {
        tableConfigJpaRepository.deleteByUserIdAndConfigKey(userId, configKey);
    }
}
