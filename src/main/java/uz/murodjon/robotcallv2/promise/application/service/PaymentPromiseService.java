package uz.murodjon.robotcallv2.promise.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.promise.application.dto.UpdatePaymentPromiseSettingsRequest;
import uz.murodjon.robotcallv2.promise.application.mapper.PaymentPromiseMapper;
import uz.murodjon.robotcallv2.promise.application.port.input.PaymentPromiseUseCase;
import uz.murodjon.robotcallv2.promise.application.port.output.PaymentPromiseRepository;
import uz.murodjon.robotcallv2.promise.application.port.output.PaymentPromiseSettingsRepository;
import uz.murodjon.robotcallv2.promise.domain.entity.*;
import uz.murodjon.robotcallv2.promise.domain.enums.PaymentPromiseStatus;
import uz.murodjon.robotcallv2.promise.domain.service.PaymentPromiseValidator;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

@Service
public class PaymentPromiseService implements PaymentPromiseUseCase {

    private final PaymentPromiseRepository repository;
    private final PaymentPromiseSettingsRepository settingsRepository;
    private final PaymentPromiseFollowUp followUp;
    private final PaymentPromiseMapper mapper;
    private final Clock clock;

    public PaymentPromiseService(PaymentPromiseRepository repository,
                                 PaymentPromiseSettingsRepository settingsRepository,
                                 PaymentPromiseFollowUp followUp, PaymentPromiseMapper mapper, Clock clock) {
        this.repository = repository;
        this.settingsRepository = settingsRepository;
        this.followUp = followUp;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public PageableData<PaymentPromise> filterPromises(long companyId, PaymentPromiseFilter filter) {
        return repository.findPageByCompanyId(companyId, filter);
    }

    @Override
    public PaymentPromise findPromise(long companyId, long id) {
        PaymentPromise promise = repository.findByCompanyIdAndId(companyId, id);
        if (promise == null) {
            throw new NotFoundException(ErrorCode.PAYMENT_PROMISE_NOT_FOUND, id);
        }
        return promise;
    }

    @Override
    public PaymentPromiseStats findStats(long companyId, LocalDate from, LocalDate to) {
        PaymentPromiseValidator.validateStatsRange(from, to);
        return repository.findStatsByCompanyId(companyId, from, to);
    }

    @Override
    public PaymentPromise updateStatus(long companyId, long id, PaymentPromiseStatus status) {
        PaymentPromiseValidator.validateManualStatus(status);
        PaymentPromise promise = findPromise(companyId, id);
        Instant now = clock.instant();
        boolean changed = status == PaymentPromiseStatus.BROKEN
                ? followUp.breakPromise(promise, followUp.findSettings(companyId), now)
                : repository.updateStatusIfOpen(id, status, now);
        if (!changed) {
            throw new ConflictException(ErrorCode.PAYMENT_PROMISE_NOT_OPEN, id, findPromise(companyId, id).status());
        }
        return findPromise(companyId, id);
    }

    @Override
    public PaymentPromiseSettings findSettings(long companyId) {
        return followUp.findSettings(companyId);
    }

    @Override
    public PaymentPromiseSettings updateSettings(long companyId, UpdatePaymentPromiseSettingsRequest request) {
        PaymentPromiseSettings settings = mapper.toPaymentPromiseSettings(companyId, request);
        PaymentPromiseValidator.validateSettings(settings);
        return settingsRepository.upsert(companyId, settings);
    }

    @Override
    public Map<String, PaymentPromiseTally> findTalliesByPhones(long companyId, Collection<String> phones, Instant since) {
        return phones.isEmpty() ? Map.of() : repository.findTalliesByPhones(companyId, phones, since);
    }
}
