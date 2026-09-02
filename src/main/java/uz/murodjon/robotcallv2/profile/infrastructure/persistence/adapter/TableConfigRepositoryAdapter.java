package uz.murodjon.robotcallv2.profile.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
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

    private final TableConfigJpaRepository jpa;
    private final UserJpaRepository userJpa;
    private final CompanyJpaRepository companyJpa;
    private final CurrentCompany company;

    public TableConfigRepositoryAdapter(TableConfigJpaRepository jpa,
                                        UserJpaRepository userJpa,
                                        CompanyJpaRepository companyJpa,
                                        CurrentCompany company) {
        this.jpa = jpa;
        this.userJpa = userJpa;
        this.companyJpa = companyJpa;
        this.company = company;
    }

    @Override
    public String find(long userId, String configKey) {
        return jpa.findByUserIdAndConfigKey(userId, configKey).map(TableConfigEntity::getConfigValue).orElse(null);
    }

    @Override
    @Transactional
    public String save(long userId, String configKey, String configValueJson) {
        if (configValueJson == null) {
            jpa.deleteByUserIdAndConfigKey(userId, configKey);
            return null;
        }
        UserEntity user = userJpa.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, userId));
        CompanyEntity comp = companyJpa.findById(company.id())
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMPANY_NOT_FOUND, company.id()));

        TableConfigEntity entity = jpa.findByUserIdAndConfigKey(userId, configKey).orElseGet(TableConfigEntity::new);
        entity.setCompany(comp);
        entity.setUser(user);
        entity.setConfigKey(configKey);
        entity.setConfigValue(configValueJson);
        entity.setUpdatedAt(Instant.now());
        return jpa.save(entity).getConfigValue();
    }
}
