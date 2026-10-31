package uz.murodjon.robotcallv2.siptrunk.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.siptrunk.application.dto.*;
import uz.murodjon.robotcallv2.siptrunk.application.mapper.CallerIdPoolMapper;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.CallerIdPoolUseCase;
import uz.murodjon.robotcallv2.siptrunk.application.port.output.CallerIdPoolRepository;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.CallerIdPool;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.CallerIdPoolNumber;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.CallerIdNumberStatus;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.CallerIdStrategy;
import uz.murodjon.robotcallv2.siptrunk.domain.service.CallerIdPoolValidator;
import uz.murodjon.robotcallv2.siptrunk.domain.service.CallerIdSelector;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class CallerIdPoolService implements CallerIdPoolUseCase {

    private static final Logger log = LoggerFactory.getLogger(CallerIdPoolService.class);

    private final CallerIdPoolRepository callerIdPoolRepository;
    private final CallerIdPoolMapper callerIdPoolMapper;

    public CallerIdPoolService(CallerIdPoolRepository callerIdPoolRepository,
                               CallerIdPoolMapper callerIdPoolMapper) {
        this.callerIdPoolRepository = callerIdPoolRepository;
        this.callerIdPoolMapper = callerIdPoolMapper;
    }

    @Override
    @Transactional
    public CallerIdPoolDetail createPool(long companyId, CreateCallerIdPoolRequest request) {
        CallerIdPoolValidator.validatePoolName(request.name());
        CallerIdPool pool = new CallerIdPool(
                0L,
                companyId,
                request.name().trim(),
                request.strategy() != null ? request.strategy() : CallerIdStrategy.ROUND_ROBIN,
                request.description(),
                List.of(),
                Instant.now(),
                Instant.now()
        );
        CallerIdPool saved = callerIdPoolRepository.save(companyId, pool);
        if (request.initialPhones() != null) {
            for (String phone : request.initialPhones()) {
                if (phone != null && !phone.isBlank()) {
                    addRegisteredNumber(companyId, saved.id(), phone, null);
                }
            }
        }
        return getPool(companyId, saved.id());
    }

    @Override
    @Transactional
    public CallerIdPoolDetail updatePool(long companyId, long poolId, UpdateCallerIdPoolRequest request) {
        CallerIdPool existing = callerIdPoolRepository.findById(companyId, poolId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CALLER_ID_POOL_NOT_FOUND, poolId));

        CallerIdPoolValidator.validatePoolName(request.name());
        CallerIdPool updated = new CallerIdPool(
                existing.id(),
                companyId,
                request.name().trim(),
                request.strategy() != null ? request.strategy() : existing.strategy(),
                request.description(),
                existing.numbers(),
                existing.createdAt(),
                Instant.now()
        );
        CallerIdPool saved = callerIdPoolRepository.save(companyId, updated);
        return getPool(companyId, saved.id());
    }

    @Override
    @Transactional(readOnly = true)
    public CallerIdPoolDetail getPool(long companyId, long poolId) {
        CallerIdPool pool = callerIdPoolRepository.findById(companyId, poolId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CALLER_ID_POOL_NOT_FOUND, poolId));
        return callerIdPoolMapper.toDetail(pool);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CallerIdPoolRow> listPools(long companyId) {
        return callerIdPoolRepository.findAllByCompanyId(companyId).stream()
                .map(callerIdPoolMapper::toRow)
                .toList();
    }

    @Override
    @Transactional
    public void deletePool(long companyId, long poolId) {
        callerIdPoolRepository.findById(companyId, poolId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CALLER_ID_POOL_NOT_FOUND, poolId));
        callerIdPoolRepository.delete(companyId, poolId);
    }

    @Override
    @Transactional
    public CallerIdNumberRow addNumber(long companyId, long poolId, AddCallerIdNumberRequest request) {
        callerIdPoolRepository.findById(companyId, poolId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CALLER_ID_POOL_NOT_FOUND, poolId));

        return callerIdPoolMapper.toNumberRow(addRegisteredNumber(companyId, poolId, request.phone(), request.label()));
    }

    /**
     * Only one of the company's own registered phone numbers joins a pool: its trunk is the one its
     * calls go out on, which free text could not say. The pool is the caller's to have checked.
     */
    private CallerIdPoolNumber addRegisteredNumber(long companyId, long poolId, String rawPhone, String label) {
        String phone = PhoneNumbers.require(rawPhone);
        CallerIdPoolNumber number = callerIdPoolRepository.addNumber(companyId, poolId, phone, label);
        if (number == null) {
            throw new ConflictException(ErrorCode.CALLER_ID_NUMBER_NOT_REGISTERED, phone);
        }
        return number;
    }

    @Override
    @Transactional
    public void removeNumber(long companyId, long poolId, long numberId) {
        callerIdPoolRepository.findById(companyId, poolId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CALLER_ID_POOL_NOT_FOUND, poolId));
        callerIdPoolRepository.removeNumber(companyId, poolId, numberId);
    }

    @Override
    @Transactional
    public void setNumberCooling(long companyId, long poolId, long numberId, int coolingHours) {
        callerIdPoolRepository.findById(companyId, poolId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CALLER_ID_POOL_NOT_FOUND, poolId));
        Instant until = Instant.now().plus(Duration.ofHours(Math.max(1, coolingHours)));
        callerIdPoolRepository.updateNumberStatus(companyId, poolId, numberId, CallerIdNumberStatus.COOLING_DOWN.name(), until);
    }

    @Override
    @Transactional
    public void activateNumber(long companyId, long poolId, long numberId) {
        callerIdPoolRepository.findById(companyId, poolId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CALLER_ID_POOL_NOT_FOUND, poolId));
        callerIdPoolRepository.updateNumberStatus(companyId, poolId, numberId, CallerIdNumberStatus.ACTIVE.name(), null);
    }

    @Override
    @Transactional
    public void disableNumber(long companyId, long poolId, long numberId) {
        callerIdPoolRepository.findById(companyId, poolId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CALLER_ID_POOL_NOT_FOUND, poolId));
        callerIdPoolRepository.updateNumberStatus(companyId, poolId, numberId, CallerIdNumberStatus.DISABLED.name(), null);
    }

    @Override
    @Transactional
    public CallerIdPoolNumber acquireCallerIdOnTrunks(long companyId, long poolId, String calleePhone,
                                                      Set<Long> trunkIds) {
        CallerIdPool pool = callerIdPoolRepository.findById(companyId, poolId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CALLER_ID_POOL_NOT_FOUND, poolId));

        Optional<CallerIdPoolNumber> selected =
                CallerIdSelector.selectOnTrunks(pool.numbers(), pool.strategy(), Instant.now(), calleePhone, trunkIds);
        if (selected.isEmpty()) {
            return null;
        }

        CallerIdPoolNumber chosen = selected.get();
        callerIdPoolRepository.recordNumberUsage(pool.id(), chosen.id(), Instant.now());
        log.debug("Acquired caller ID {} (trunk {}) from pool {} using strategy {}", chosen.phone(), chosen.trunkId(),
                pool.name(), pool.strategy());
        return chosen;
    }

    @Override
    @Transactional
    public void recordCallAnswer(long poolId, String phone) {
        if (phone != null && !phone.isBlank()) {
            callerIdPoolRepository.recordNumberAnswer(poolId, phone);
        }
    }
}
