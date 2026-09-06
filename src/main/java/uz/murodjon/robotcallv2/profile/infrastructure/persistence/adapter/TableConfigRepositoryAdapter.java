package uz.murodjon.robotcallv2.profile.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.profile.application.port.output.TableConfigRepository;
import uz.murodjon.robotcallv2.profile.infrastructure.persistence.entity.TableConfigEntity;
import uz.murodjon.robotcallv2.profile.infrastructure.persistence.repository.TableConfigJpaRepository;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
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
    public String find(long userId, String configKey) {
        return tableConfigJpaRepository.findByUserIdAndConfigKey(userId, configKey).map(TableConfigEntity::getConfigValue).orElse(null);
    }

    @Override
    @Transactional
    public String save(long companyId, long userId, String configKey, String configValueJson) {
        if (configValueJson == null) {
            tableConfigJpaRepository.deleteByUserIdAndConfigKey(userId, configKey);
            return null;
        }
        UserEntity user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, userId));
        CompanyEntity comp = companyJpaRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMPANY_NOT_FOUND, companyId));

        TableConfigEntity entity = tableConfigJpaRepository.findByUserIdAndConfigKey(userId, configKey).orElseGet(TableConfigEntity::new);
        entity.setCompany(comp);
        entity.setUser(user);
        entity.setConfigKey(configKey);
        entity.setConfigValue(configValueJson);
        entity.setUpdatedAt(Instant.now());
        return tableConfigJpaRepository.save(entity).getConfigValue();
    }
}
