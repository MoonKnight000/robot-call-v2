package uz.murodjon.robotcallv2.partner.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.company.application.dto.CreateCompanyRequest;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.partner.application.dto.BrandingResponse;
import uz.murodjon.robotcallv2.partner.application.dto.CreatePartnerClientRequest;
import uz.murodjon.robotcallv2.partner.application.dto.CreatePartnerClientResponse;
import uz.murodjon.robotcallv2.partner.application.dto.UpdatePartnerBrandingRequest;
import uz.murodjon.robotcallv2.partner.application.mapper.PartnerMapper;
import uz.murodjon.robotcallv2.partner.application.port.output.PartnerRepository;
import uz.murodjon.robotcallv2.partner.domain.entity.PartnerBranding;
import uz.murodjon.robotcallv2.partner.domain.entity.PartnerClient;
import uz.murodjon.robotcallv2.shared.exception.AppException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserResponse;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class PartnerServiceTest {

    private static final long PARTNER_ID = 10L;
    private static final long CLIENT_ID = 20L;
    private static final PartnerBranding BRAND = new PartnerBranding(PARTNER_ID, "SmartCall", "app.smartcall.uz",
            "https://smartcall.uz/logo.png", "#0055FF", "help@smartcall.uz", "+998712000000", Instant.EPOCH);

    private PartnerRepository repository;
    private CompanyUseCase companies;
    private UserUseCase users;
    private PartnerService service;

    @BeforeEach
    void setUp() {
        repository = mock(PartnerRepository.class);
        companies = mock(CompanyUseCase.class);
        users = mock(UserUseCase.class);
        service = new PartnerService(repository, new PartnerMapper(), companies, users, mock(AuditUseCase.class));
    }

    @Test
    void aClientSeesItsPartnersBrand() {
        when(repository.findClientByClientCompanyId(CLIENT_ID)).thenReturn(new PartnerClient(PARTNER_ID, CLIENT_ID, Instant.EPOCH));
        when(repository.findBrandingByCompanyId(PARTNER_ID)).thenReturn(BRAND);

        BrandingResponse branding = service.findBrandingByCompanyId(CLIENT_ID);

        assertThat(branding.productName()).isEqualTo("SmartCall");
        assertThat(branding.primaryColor()).isEqualTo("#0055FF");
    }

    @Test
    void aDirectCustomerSeesOurOwnBrand() {
        assertThat(service.findBrandingByCompanyId(CLIENT_ID)).isNull();
    }

    @Test
    void theLoginPageFindsTheBrandByHostIgnoringCase() {
        when(repository.findBrandingByDomain("app.smartcall.uz")).thenReturn(BRAND);

        assertThat(service.findBrandingByDomain(" App.SmartCall.uz ").productName()).isEqualTo("SmartCall");
    }

    @Test
    void onlyAPartnerSignsUpClients() {
        assertRefused(() -> service.createClient(CLIENT_ID, clientRequest()), ErrorCode.PARTNER_REQUIRED);
        verify(companies, never()).create(any());
    }

    @Test
    void aPartnersClientIsLinkedToItAndItsTokenReturned() {
        when(repository.findBrandingByCompanyId(PARTNER_ID)).thenReturn(BRAND);
        when(users.findByUsername("aziz")).thenReturn(Optional.empty());
        when(companies.create(new CreateCompanyRequest("Do'kon"))).thenReturn(
                new Company(CLIENT_ID, "Do'kon", null, null, null, null, null));
        when(users.inviteFirstAdmin(CLIENT_ID, "Aziz", "aziz", "aziz@dokon.uz"))
                .thenReturn(new InviteUserResponse(1L, "aziz@dokon.uz", "token-1", Instant.EPOCH));

        CreatePartnerClientResponse response = service.createClient(PARTNER_ID, clientRequest());

        verify(repository).createClient(PARTNER_ID, CLIENT_ID);
        assertThat(response.companyId()).isEqualTo(CLIENT_ID);
        assertThat(response.inviteToken()).isEqualTo("token-1");
    }

    @Test
    void anotherPartnersDomainIsRefused() {
        when(companies.findById(PARTNER_ID)).thenReturn(new Company(PARTNER_ID, "P", null, null, null, null, null));
        when(repository.existsBrandingByDomainAndCompanyIdNot("app.smartcall.uz", PARTNER_ID)).thenReturn(true);

        assertRefused(() -> service.upsertBrandingByPlatform(PARTNER_ID, brandingRequest()), ErrorCode.PARTNER_DOMAIN_TAKEN);
        verify(repository, never()).upsertBranding(anyLong(), any());
    }

    @Test
    void aClientCannotBecomeAPartner() {
        when(companies.findById(CLIENT_ID)).thenReturn(new Company(CLIENT_ID, "C", null, null, null, null, null));
        when(repository.findClientByClientCompanyId(CLIENT_ID)).thenReturn(new PartnerClient(PARTNER_ID, CLIENT_ID, Instant.EPOCH));

        assertRefused(() -> service.upsertBrandingByPlatform(CLIENT_ID, brandingRequest()), ErrorCode.PARTNER_CLIENT_IS_PARTNER);
    }

    private static CreatePartnerClientRequest clientRequest() {
        return new CreatePartnerClientRequest("Do'kon", "Aziz", "aziz", "aziz@dokon.uz");
    }

    private static UpdatePartnerBrandingRequest brandingRequest() {
        return new UpdatePartnerBrandingRequest("SmartCall", "App.SmartCall.uz", null, "#0055FF", null, null);
    }

    private static void assertRefused(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(AppException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
