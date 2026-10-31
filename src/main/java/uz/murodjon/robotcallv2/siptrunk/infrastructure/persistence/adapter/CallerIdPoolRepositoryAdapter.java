package uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.phonenumber.infrastructure.persistence.entity.PhoneNumberEntity;
import uz.murodjon.robotcallv2.phonenumber.infrastructure.persistence.repository.PhoneNumberJpaRepository;
import uz.murodjon.robotcallv2.siptrunk.application.port.output.CallerIdPoolRepository;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.CallerIdPool;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.CallerIdPoolNumber;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.CallerIdNumberStatus;
import uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.entity.CallerIdPoolEntity;
import uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.entity.CallerIdPoolNumberEntity;
import uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.mapper.CallerIdPoolEntityMapper;
import uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.repository.CallerIdPoolJpaRepository;
import uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.repository.CallerIdPoolNumberJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class CallerIdPoolRepositoryAdapter implements CallerIdPoolRepository {

    private final CallerIdPoolJpaRepository callerIdPoolJpaRepository;
    private final CallerIdPoolNumberJpaRepository callerIdPoolNumberJpaRepository;
    private final CallerIdPoolEntityMapper callerIdPoolEntityMapper;
    private final CompanyJpaRepository companyJpaRepository;
    private final PhoneNumberJpaRepository phoneNumberJpaRepository;

    public CallerIdPoolRepositoryAdapter(CallerIdPoolJpaRepository callerIdPoolJpaRepository,
                                         CallerIdPoolNumberJpaRepository callerIdPoolNumberJpaRepository,
                                         CallerIdPoolEntityMapper callerIdPoolEntityMapper,
                                         CompanyJpaRepository companyJpaRepository,
                                         PhoneNumberJpaRepository phoneNumberJpaRepository) {
        this.callerIdPoolJpaRepository = callerIdPoolJpaRepository;
        this.callerIdPoolNumberJpaRepository = callerIdPoolNumberJpaRepository;
        this.callerIdPoolEntityMapper = callerIdPoolEntityMapper;
        this.companyJpaRepository = companyJpaRepository;
        this.phoneNumberJpaRepository = phoneNumberJpaRepository;
    }

    @Override
    @Transactional
    public CallerIdPool save(long companyId, CallerIdPool pool) {
        CallerIdPoolEntity entity;
        if (pool.id() > 0) {
            entity = callerIdPoolJpaRepository.findByIdAndCompanyId(pool.id(), companyId)
                    .orElseGet(CallerIdPoolEntity::new);
        } else {
            entity = new CallerIdPoolEntity();
            entity.setCompany(companyJpaRepository.getReferenceById(companyId));
            entity.setCreatedAt(Instant.now());
        }
        entity.setName(pool.name());
        entity.setStrategy(pool.strategy());
        entity.setDescription(pool.description());
        entity.setUpdatedAt(Instant.now());

        CallerIdPoolEntity saved = callerIdPoolJpaRepository.save(entity);
        return callerIdPoolEntityMapper.toCallerIdPool(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CallerIdPool> findById(long companyId, long id) {
        return callerIdPoolJpaRepository.findByIdAndCompanyIdWithNumbers(id, companyId)
                .map(callerIdPoolEntityMapper::toCallerIdPool);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CallerIdPool> findAllByCompanyId(long companyId) {
        return callerIdPoolJpaRepository.findAllByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(callerIdPoolEntityMapper::toCallerIdPool)
                .toList();
    }

    @Override
    @Transactional
    public void delete(long companyId, long id) {
        callerIdPoolJpaRepository.findByIdAndCompanyId(id, companyId)
                .ifPresent(callerIdPoolJpaRepository::delete);
    }

    @Override
    @Transactional
    public CallerIdPoolNumber addNumber(long companyId, long poolId, String phone, String label) {
        CallerIdPoolEntity pool = callerIdPoolJpaRepository.findByIdAndCompanyId(poolId, companyId)
                .orElse(null);
        if (pool == null) {
            return null;
        }
        PhoneNumberEntity phoneNumber = phoneNumberJpaRepository.findByPhoneNumberAndCompanyId(phone, companyId)
                .orElse(null);
        if (phoneNumber == null) {
            return null;
        }
        CallerIdPoolNumberEntity numberEntity = callerIdPoolNumberJpaRepository
                .findByPoolIdAndPhone(poolId, phone)
                .orElseGet(() -> {
                    CallerIdPoolNumberEntity created = new CallerIdPoolNumberEntity();
                    created.setPool(pool);
                    created.setPhone(phone);
                    created.setCreatedAt(Instant.now());
                    return created;
                });
        numberEntity.setPhoneNumber(phoneNumber);
        numberEntity.setLabel(label);
        numberEntity.setStatus(CallerIdNumberStatus.ACTIVE);
        numberEntity.setCoolingUntil(null);

        CallerIdPoolNumberEntity saved = callerIdPoolNumberJpaRepository.save(numberEntity);
        return callerIdPoolEntityMapper.toCallerIdPoolNumber(saved);
    }

    @Override
    @Transactional
    public void removeNumber(long companyId, long poolId, long numberId) {
        callerIdPoolJpaRepository.findByIdAndCompanyId(poolId, companyId).ifPresent(pool ->
                callerIdPoolNumberJpaRepository.findByIdAndPoolId(numberId, poolId)
                        .ifPresent(callerIdPoolNumberJpaRepository::delete));
    }

    @Override
    @Transactional
    public void updateNumberStatus(long companyId, long poolId, long numberId, String status, Instant coolingUntil) {
        callerIdPoolJpaRepository.findByIdAndCompanyId(poolId, companyId).ifPresent(pool ->
                callerIdPoolNumberJpaRepository.findByIdAndPoolId(numberId, poolId).ifPresent(entity -> {
                    try {
                        entity.setStatus(CallerIdNumberStatus.valueOf(status));
                    } catch (IllegalArgumentException ignored) {
                    }
                    entity.setCoolingUntil(coolingUntil);
                    callerIdPoolNumberJpaRepository.save(entity);
                }));
    }

    @Override
    @Transactional
    public void recordNumberUsage(long poolId, long numberId, Instant usedAt) {
        callerIdPoolNumberJpaRepository.incrementUsage(numberId, usedAt);
    }

    @Override
    @Transactional
    public void recordNumberAnswer(long poolId, String phone) {
        callerIdPoolNumberJpaRepository.incrementAnswer(poolId, phone);
    }
}
