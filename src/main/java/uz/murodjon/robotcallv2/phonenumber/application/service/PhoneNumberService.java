package uz.murodjon.robotcallv2.phonenumber.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.callflow.application.port.input.CallFlowUseCase;
import uz.murodjon.robotcallv2.phonenumber.application.dto.*;
import uz.murodjon.robotcallv2.phonenumber.application.mapper.PhoneNumberMapper;
import uz.murodjon.robotcallv2.phonenumber.application.port.input.PhoneNumberUseCase;
import uz.murodjon.robotcallv2.phonenumber.application.port.output.PhoneNumberRepository;
import uz.murodjon.robotcallv2.phonenumber.application.port.output.SipProbePort;
import uz.murodjon.robotcallv2.phonenumber.domain.entity.PhoneNumber;
import uz.murodjon.robotcallv2.phonenumber.domain.entity.PhoneNumberFilter;
import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberStatus;
import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberType;
import uz.murodjon.robotcallv2.phonenumber.domain.service.PhoneNumberValidator;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.SipTrunkUseCase;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PhoneNumberService implements PhoneNumberUseCase {

    private static final Logger log = LoggerFactory.getLogger(PhoneNumberService.class);

    private final PhoneNumberRepository phoneNumberRepository;
    private final SipProbePort sipProbePort;
    private final AiAgentUseCase aiAgentUseCase;
    private final CallFlowUseCase callFlowUseCase;
    private final SipTrunkUseCase sipTrunkUseCase;

    public PhoneNumberService(PhoneNumberRepository phoneNumberRepository,
                              SipProbePort sipProbePort,
                              AiAgentUseCase aiAgentUseCase,
                              CallFlowUseCase callFlowUseCase,
                              SipTrunkUseCase sipTrunkUseCase) {
        this.phoneNumberRepository = phoneNumberRepository;
        this.sipProbePort = sipProbePort;
        this.aiAgentUseCase = aiAgentUseCase;
        this.callFlowUseCase = callFlowUseCase;
        this.sipTrunkUseCase = sipTrunkUseCase;
    }

    @Override
    @Transactional(readOnly = true)
    public PageableData<PhoneNumberRow> findMarketplaceNumbers(PhoneNumberFilter filter) {
        return phoneNumberRepository.findAvailablePage(filter).map(number -> PhoneNumberMapper.toRow(number, null, null));
    }

    @Override
    @Transactional
    public PhoneNumberRow purchaseNumber(long companyId, PurchaseNumberRequest request) {
        PhoneNumber number = requireNumber(request.poolNumberId());
        if (number.status() != PhoneNumberStatus.AVAILABLE) {
            throw new ConflictException(ErrorCode.PHONE_NUMBER_ALREADY_PURCHASED, number.phoneNumber());
        }
        PhoneNumberValidator.validatePurchase(number, request.allocatedChannels());
        String agentName = request.inboundAgentId() != null
                ? aiAgentUseCase.requireAgent(companyId, request.inboundAgentId()).name() : null;
        String flowName = request.inboundCallFlowId() != null
                ? callFlowUseCase.requirePublishedFlow(companyId, request.inboundCallFlowId()).name() : null;

        Instant now = Instant.now();
        PhoneNumber saved = phoneNumberRepository.save(number.toBuilder()
                .companyId(companyId)
                .nickname(request.nickname() != null && !request.nickname().isBlank()
                        ? request.nickname().trim() : number.phoneNumber())
                .status(PhoneNumberStatus.ASSIGNED)
                .allocatedChannels(request.allocatedChannels())
                .inboundAgentId(request.inboundAgentId())
                .inboundCallFlowId(request.inboundCallFlowId())
                .autoRenew(request.autoRenew() == null || request.autoRenew())
                .purchasedAt(now)
                .expiresAt(now.plus(30, ChronoUnit.DAYS))
                .build());
        log.info("Company {} purchased phone number {} with {} channels", companyId, saved.phoneNumber(), saved.allocatedChannels());
        return PhoneNumberMapper.toRow(saved, agentName, flowName);
    }

    /**
     * A number the company brings on its own trunk: assigned to it at once, nothing to pay the
     * platform. The trunk must be the company's own — never another company's or the platform's.
     */
    @Override
    @Transactional
    public PhoneNumberRow createByoNumber(long companyId, CreateByoNumberRequest request) {
        String phone = PhoneNumbers.require(request.phoneNumber());
        sipTrunkUseCase.requireTrunk(companyId, request.trunkId());
        if (phoneNumberRepository.existsByPhoneNumber(phone)) {
            throw new ConflictException(ErrorCode.PHONE_NUMBER_ALREADY_EXISTS, phone);
        }
        PhoneNumber saved = phoneNumberRepository.save(PhoneNumber.builder()
                .companyId(companyId)
                .phoneNumber(phone)
                .nickname(request.nickname() != null && !request.nickname().isBlank()
                        ? request.nickname().trim() : phone)
                .type(PhoneNumberType.BYO_SIP_TRUNK)
                .status(PhoneNumberStatus.ASSIGNED)
                .providerTrunkId(request.trunkId())
                .autoRenew(false)
                .build());
        log.info("Company {} added BYON phone number {} on trunk {}", companyId, saved.phoneNumber(), request.trunkId());
        return PhoneNumberMapper.toRow(saved, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public PageableData<PhoneNumberRow> findCompanyNumbers(long companyId, PhoneNumberFilter filter) {
        PageableData<PhoneNumber> page = phoneNumberRepository.findPageByCompanyId(companyId, filter);
        Set<Long> agentIds = page.data().stream().map(PhoneNumber::inboundAgentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> flowIds = page.data().stream().map(PhoneNumber::inboundCallFlowId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> agentNames = agentIds.isEmpty() ? Map.of() : aiAgentUseCase.findNamesByIds(agentIds);
        Map<Long, String> flowNames = callFlowUseCase.findNamesByIds(companyId, flowIds);
        // Map.of().get(null) throws, hence the explicit null checks.
        return page.map(number -> PhoneNumberMapper.toRow(number,
                number.inboundAgentId() != null ? agentNames.get(number.inboundAgentId()) : null,
                number.inboundCallFlowId() != null ? flowNames.get(number.inboundCallFlowId()) : null));
    }

    @Override
    @Transactional
    public PhoneNumberRow updateInboundRouting(long companyId, long id, UpdateInboundRoutingRequest request) {
        PhoneNumber number = requireOwnedNumber(companyId, id);
        String agentName = request.inboundAgentId() != null
                ? aiAgentUseCase.requireAgent(companyId, request.inboundAgentId()).name() : null;
        String flowName = request.inboundCallFlowId() != null
                ? callFlowUseCase.requirePublishedFlow(companyId, request.inboundCallFlowId()).name() : null;

        PhoneNumber saved = phoneNumberRepository.save(number.toBuilder()
                .nickname(request.nickname() != null ? request.nickname().trim() : number.nickname())
                .inboundAgentId(request.inboundAgentId())
                .inboundCallFlowId(request.inboundCallFlowId())
                .autoRenew(request.autoRenew() != null ? request.autoRenew() : number.autoRenew())
                .build());
        log.info("Updated inbound routing for phone number {} (company {})", saved.phoneNumber(), companyId);
        return PhoneNumberMapper.toRow(saved, agentName, flowName);
    }

    /** A marketplace number's trunk is the platform's (set when it was added to the pool), so only BYON moves. */
    @Override
    @Transactional
    public PhoneNumberRow updateTrunk(long companyId, long id, UpdatePhoneNumberTrunkRequest request) {
        PhoneNumber number = requireOwnedNumber(companyId, id);
        if (number.type() != PhoneNumberType.BYO_SIP_TRUNK) {
            throw new ConflictException(ErrorCode.PHONE_NUMBER_TRUNK_PLATFORM_MANAGED, number.phoneNumber());
        }
        sipTrunkUseCase.requireTrunk(companyId, request.trunkId());
        PhoneNumber saved = phoneNumberRepository.save(number.toBuilder()
                .providerTrunkId(request.trunkId())
                .build());
        log.info("Phone number {} (company {}) moved to trunk {}", saved.phoneNumber(), companyId, request.trunkId());
        String agentName = saved.inboundAgentId() != null
                ? aiAgentUseCase.findNamesByIds(Set.of(saved.inboundAgentId())).get(saved.inboundAgentId()) : null;
        String flowName = saved.inboundCallFlowId() != null
                ? callFlowUseCase.findNamesByIds(companyId, Set.of(saved.inboundCallFlowId())).get(saved.inboundCallFlowId())
                : null;
        return PhoneNumberMapper.toRow(saved, agentName, flowName);
    }

    @Override
    @Transactional(readOnly = true)
    public PhoneNumber findCompanyNumber(long companyId, long id) {
        return phoneNumberRepository.findById(id)
                .filter(number -> number.isAssignedToCompany(companyId))
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public PhoneNumber findAssignedByPhoneNumber(String did) {
        String phone = PhoneNumbers.normalize(did);
        if (phone == null) {
            return null;
        }
        return phoneNumberRepository.findByPhoneNumber(phone)
                .filter(number -> number.companyId() != null && number.status() == PhoneNumberStatus.ASSIGNED)
                .orElse(null);
    }

    @Override
    @Transactional
    public void releaseNumber(long companyId, long id) {
        PhoneNumber number = requireOwnedNumber(companyId, id);
        if (number.type() == PhoneNumberType.MARKETPLACE_PURCHASED) {
            phoneNumberRepository.save(number.toBuilder()
                    .companyId(null)
                    .nickname(null)
                    .status(PhoneNumberStatus.AVAILABLE)
                    .allocatedChannels(0)
                    .inboundAgentId(null)
                    .inboundCallFlowId(null)
                    .autoRenew(false)
                    .purchasedAt(null)
                    .expiresAt(null)
                    .build());
            log.info("Released marketplace phone number {} back to pool", number.phoneNumber());
        } else {
            phoneNumberRepository.deleteById(id);
            log.info("Deleted BYON phone number {} for company {}", number.phoneNumber(), companyId);
        }
    }

    @Override
    public TestSipConnectionResponse testSipConnection(TestSipConnectionRequest request) {
        int port = request.port() != null && request.port() > 0 ? request.port() : 5060;
        String transport = request.transport() != null ? request.transport().trim().toUpperCase() : "TCP";
        return PhoneNumberMapper.toTestSipConnectionResponse(sipProbePort.probe(request.host(), port, transport));
    }

    private PhoneNumber requireNumber(long id) {
        return phoneNumberRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PHONE_NUMBER_NOT_FOUND, id));
    }

    /** Another company's number, or one back in the pool, reads as not found. */
    private PhoneNumber requireOwnedNumber(long companyId, long id) {
        PhoneNumber number = requireNumber(id);
        if (!number.isAssignedToCompany(companyId)) {
            throw new NotFoundException(ErrorCode.PHONE_NUMBER_NOT_FOUND, id);
        }
        return number;
    }
}
