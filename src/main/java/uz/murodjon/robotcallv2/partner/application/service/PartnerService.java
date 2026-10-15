package uz.murodjon.robotcallv2.partner.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.company.application.dto.CreateCompanyRequest;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.partner.application.dto.BrandingResponse;
import uz.murodjon.robotcallv2.partner.application.dto.CreatePartnerClientRequest;
import uz.murodjon.robotcallv2.partner.application.dto.CreatePartnerClientResponse;
import uz.murodjon.robotcallv2.partner.application.dto.PartnerClientRow;
import uz.murodjon.robotcallv2.partner.application.dto.UpdatePartnerBrandingRequest;
import uz.murodjon.robotcallv2.partner.application.mapper.PartnerMapper;
import uz.murodjon.robotcallv2.partner.application.port.input.PartnerUseCase;
import uz.murodjon.robotcallv2.partner.application.port.output.PartnerRepository;
import uz.murodjon.robotcallv2.partner.domain.entity.PartnerBranding;
import uz.murodjon.robotcallv2.partner.domain.entity.PartnerClient;
import uz.murodjon.robotcallv2.partner.domain.entity.PartnerClientFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserResponse;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;

import java.util.Locale;

/**
 * Partners resell the platform under their own brand: the platform enrols a company as a
 * partner, the partner signs up its clients, and those clients' panels show the partner's
 * name, logo and colours. One level only — a partner is never another partner's client.
 */
@Service
public class PartnerService implements PartnerUseCase {

    private final PartnerRepository partnerRepository;
    private final PartnerMapper partnerMapper;
    private final CompanyUseCase companyUseCase;
    private final UserUseCase userUseCase;
    private final AuditUseCase auditUseCase;

    public PartnerService(PartnerRepository partnerRepository, PartnerMapper partnerMapper,
                          CompanyUseCase companyUseCase, UserUseCase userUseCase, AuditUseCase auditUseCase) {
        this.partnerRepository = partnerRepository;
        this.partnerMapper = partnerMapper;
        this.companyUseCase = companyUseCase;
        this.userUseCase = userUseCase;
        this.auditUseCase = auditUseCase;
    }

    @Override
    public PartnerBranding upsertBrandingByPlatform(long companyId, UpdatePartnerBrandingRequest request) {
        if (companyUseCase.findById(companyId) == null) {
            throw new NotFoundException(ErrorCode.COMPANY_NOT_FOUND, companyId);
        }
        if (partnerRepository.findClientByClientCompanyId(companyId) != null) {
            throw new ConflictException(ErrorCode.PARTNER_CLIENT_IS_PARTNER);
        }
        return saveBranding(companyId, request);
    }

    @Override
    public PartnerBranding findOwnBranding(long companyId) {
        return requirePartner(companyId);
    }

    @Override
    public PartnerBranding updateOwnBranding(long companyId, UpdatePartnerBrandingRequest request) {
        requirePartner(companyId);
        return saveBranding(companyId, request);
    }

    @Override
    public BrandingResponse findBrandingByCompanyId(long companyId) {
        PartnerBranding own = partnerRepository.findBrandingByCompanyId(companyId);
        if (own != null) {
            return partnerMapper.toBrandingResponse(own);
        }
        PartnerClient client = partnerRepository.findClientByClientCompanyId(companyId);
        return client == null ? null
                : partnerMapper.toBrandingResponse(partnerRepository.findBrandingByCompanyId(client.partnerCompanyId()));
    }

    @Override
    public BrandingResponse findBrandingByDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        return partnerMapper.toBrandingResponse(
                partnerRepository.findBrandingByDomain(domain.trim().toLowerCase(Locale.ROOT)));
    }

    /**
     * The client company, its first administrator and the partner link in one transaction — a
     * taken email or username leaves no half-made tenant behind.
     */
    @Override
    @Transactional
    public CreatePartnerClientResponse createClient(long partnerCompanyId, CreatePartnerClientRequest request) {
        requirePartner(partnerCompanyId);
        if (userUseCase.existsByEmail(request.email())) {
            throw new ConflictException(ErrorCode.USER_EMAIL_TAKEN, request.email());
        }
        if (userUseCase.findByUsername(request.username()).isPresent()) {
            throw new ConflictException(ErrorCode.USER_USERNAME_TAKEN, request.username());
        }
        Company company = companyUseCase.create(new CreateCompanyRequest(request.companyName()));
        partnerRepository.createClient(partnerCompanyId, company.id());
        InviteUserResponse invite = userUseCase.inviteFirstAdmin(
                company.id(), request.adminName(), request.username(), request.email());
        auditUseCase.record(partnerCompanyId, "PARTNER_CLIENT_CREATE", "company", String.valueOf(company.id()),
                request.companyName());
        return new CreatePartnerClientResponse(company.id(), invite.email(), invite.inviteToken(), invite.expiresAt());
    }

    @Override
    public PageableData<PartnerClientRow> findClients(long partnerCompanyId, PartnerClientFilter filter) {
        requirePartner(partnerCompanyId);
        PageableData<PartnerClient> page = partnerRepository.findClientPage(partnerCompanyId, filter);
        return PageableData.of(page.data().stream()
                        .map(client -> partnerMapper.toPartnerClientRow(client, companyUseCase.findById(client.clientCompanyId())))
                        .toList(),
                filter.pageOrDefault(), filter.sizeOrDefault(), page.totalElements());
    }

    private PartnerBranding saveBranding(long companyId, UpdatePartnerBrandingRequest request) {
        PartnerBranding branding = partnerMapper.toPartnerBranding(request);
        if (branding.domain() != null
                && partnerRepository.existsBrandingByDomainAndCompanyIdNot(branding.domain(), companyId)) {
            throw new ConflictException(ErrorCode.PARTNER_DOMAIN_TAKEN, branding.domain());
        }
        PartnerBranding saved = partnerRepository.upsertBranding(companyId, branding);
        auditUseCase.record(companyId, "PARTNER_BRANDING_UPDATE", "partner_branding", String.valueOf(companyId),
                saved.productName() + (saved.domain() != null ? " @ " + saved.domain() : ""));
        return saved;
    }

    private PartnerBranding requirePartner(long companyId) {
        PartnerBranding branding = partnerRepository.findBrandingByCompanyId(companyId);
        if (branding == null) {
            throw new ForbiddenException(ErrorCode.PARTNER_REQUIRED, companyId);
        }
        return branding;
    }
}
