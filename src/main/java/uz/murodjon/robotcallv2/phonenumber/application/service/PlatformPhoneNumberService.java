package uz.murodjon.robotcallv2.phonenumber.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.phonenumber.application.dto.AddPoolNumberRequest;
import uz.murodjon.robotcallv2.phonenumber.application.dto.PhoneNumberRow;
import uz.murodjon.robotcallv2.phonenumber.application.mapper.PhoneNumberMapper;
import uz.murodjon.robotcallv2.phonenumber.application.port.input.PlatformPhoneNumberUseCase;
import uz.murodjon.robotcallv2.phonenumber.application.port.output.PhoneNumberRepository;
import uz.murodjon.robotcallv2.phonenumber.domain.entity.PhoneNumber;
import uz.murodjon.robotcallv2.phonenumber.domain.entity.PhoneNumberFilter;
import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberStatus;
import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberType;
import uz.murodjon.robotcallv2.phonenumber.domain.service.PhoneNumberValidator;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlatformPhoneNumberService implements PlatformPhoneNumberUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlatformPhoneNumberService.class);

    private final PhoneNumberRepository phoneNumberRepository;
    private final CurrentUser currentUser;

    public PlatformPhoneNumberService(PhoneNumberRepository phoneNumberRepository,
                                      CurrentUser currentUser) {
        this.phoneNumberRepository = phoneNumberRepository;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional
    public PhoneNumberRow addPoolNumber(AddPoolNumberRequest request) {
        String phone = PhoneNumbers.require(request.phoneNumber());
        if (phoneNumberRepository.existsByPhoneNumber(phone)) {
            throw new ConflictException(ErrorCode.PHONE_NUMBER_ALREADY_EXISTS, phone);
        }
        Long userId = currentUser.id().orElse(null);
        PhoneNumber number = toPoolNumber(phone, request, userId);
        PhoneNumberValidator.validatePoolItem(number);
        PhoneNumber saved = phoneNumberRepository.save(number);
        log.info("Platform admin {} added pool phone number {}", userId, saved.phoneNumber());
        return PhoneNumberMapper.toRow(saved, null, null);
    }

    @Override
    @Transactional
    public int addBatchPoolNumbers(List<AddPoolNumberRequest> requests) {
        // Everything is checked before the one write: a bad row is skipped here, never left to
        // fail the shared transaction at commit (a number twice in the batch, or already stored).
        Long userId = currentUser.id().orElse(null);
        Map<String, PhoneNumber> numbers = new LinkedHashMap<>();
        for (AddPoolNumberRequest request : requests) {
            try {
                String phone = PhoneNumbers.require(request.phoneNumber());
                PhoneNumber number = toPoolNumber(phone, request, userId);
                PhoneNumberValidator.validatePoolItem(number);
                numbers.putIfAbsent(phone, number);
            } catch (ValidationException e) {
                log.warn("Skipping number {} during batch pool add: {}", request.phoneNumber(), e.getMessage());
            }
        }
        numbers.keySet().removeAll(phoneNumberRepository.findExistingPhoneNumbers(numbers.keySet()));
        phoneNumberRepository.createAll(List.copyOf(numbers.values()));
        log.info("Platform admin {} added {} of {} pool phone numbers", userId, numbers.size(), requests.size());
        return numbers.size();
    }

    /** Category, fees and channel count left null take the PhoneNumber defaults. */
    private static PhoneNumber toPoolNumber(String phone, AddPoolNumberRequest request, Long userId) {
        return PhoneNumber.builder()
                .phoneNumber(phone)
                .type(PhoneNumberType.MARKETPLACE_PURCHASED)
                .category(request.category())
                .status(PhoneNumberStatus.AVAILABLE)
                .monthlyFee(request.monthlyFee())
                .pricePerChannel(request.pricePerChannel())
                .maxSupportedChannels(request.maxSupportedChannels())
                .allocatedChannels(0)
                .providerTrunkId(request.providerTrunkId())
                .createdBy(userId)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageableData<PhoneNumberRow> findAllPoolNumbers(PhoneNumberFilter filter) {
        return phoneNumberRepository.findPage(filter).map(number -> PhoneNumberMapper.toRow(number, null, null));
    }

    @Override
    @Transactional
    public void removePoolNumber(long id) {
        PhoneNumber number = phoneNumberRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PHONE_NUMBER_NOT_FOUND, id));

        if (number.status() == PhoneNumberStatus.ASSIGNED) {
            throw new ConflictException(ErrorCode.PHONE_NUMBER_ALREADY_PURCHASED, number.phoneNumber());
        }

        phoneNumberRepository.deleteById(id);
        log.info("Platform admin removed pool phone number {}", number.phoneNumber());
    }
}
