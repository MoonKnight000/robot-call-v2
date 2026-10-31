package uz.murodjon.robotcallv2.crm.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.crm.application.dto.UpdateCrmSettingsRequest;
import uz.murodjon.robotcallv2.crm.application.port.output.CrmSettingsRepository;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmSettings;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CrmSettingsServiceTest {

    private static final long COMPANY_ID = 42L;

    private CrmSettingsRepository repository;
    private AuditUseCase auditUseCase;
    private CrmSettingsService service;

    @BeforeEach
    void setUp() {
        repository = mock(CrmSettingsRepository.class);
        auditUseCase = mock(AuditUseCase.class);
        service = new CrmSettingsService(repository, auditUseCase);
    }

    @Test
    void findByCompanyIdReturnsDefaultsWhenNoneSaved() {
        when(repository.findByCompanyId(COMPANY_ID)).thenReturn(Optional.empty());

        CrmSettings result = service.findByCompanyId(COMPANY_ID);

        assertThat(result.companyId()).isEqualTo(COMPANY_ID);
        assertThat(result.aiEmployeeId()).isNull();
        assertThat(result.taskTypeId()).isNull();
        assertThat(result.statusMappings()).isEmpty();
        assertThat(result.meetingBookedStatusId()).isNull();
        assertThat(result.updatedAt()).isNull();
    }

    @Test
    void findByCompanyIdReturnsExistingSettings() {
        CrmSettings existing = new CrmSettings(COMPANY_ID, 101, 5,
                Map.of("MEETING_BOOKED", 201, "NEW_LEAD", 202), Instant.now());
        when(repository.findByCompanyId(COMPANY_ID)).thenReturn(Optional.of(existing));

        CrmSettings result = service.findByCompanyId(COMPANY_ID);

        assertThat(result.companyId()).isEqualTo(COMPANY_ID);
        assertThat(result.aiEmployeeId()).isEqualTo(101);
        assertThat(result.taskTypeId()).isEqualTo(5);
        assertThat(result.meetingBookedStatusId()).isEqualTo(201);
        assertThat(result.newLeadStatusId()).isEqualTo(202);
        assertThat(result.statusFor(Disposition.REFUSED)).isNull();
    }

    @Test
    void updateByCompanyIdSavesMergedMappingsAndAudits() {
        UpdateCrmSettingsRequest request = new UpdateCrmSettingsRequest(
                101,
                201,
                null,
                203,
                null,
                205,
                9,
                Map.of("FAILED", 999, "CUSTOM_OUTCOME", 888));

        CrmSettings savedReturn = new CrmSettings(COMPANY_ID, 101, 9,
                Map.of("MEETING_BOOKED", 201, "NOT_INTERESTED", 203, "NEW_LEAD", 205,
                        "FAILED", 999, "CUSTOM_OUTCOME", 888), Instant.now());

        when(repository.upsert(eq(COMPANY_ID), any())).thenReturn(savedReturn);

        CrmSettings result = service.updateByCompanyId(COMPANY_ID, request);

        ArgumentCaptor<CrmSettings> captor = ArgumentCaptor.forClass(CrmSettings.class);
        verify(repository).upsert(eq(COMPANY_ID), captor.capture());
        CrmSettings captured = captor.getValue();

        assertThat(captured.aiEmployeeId()).isEqualTo(101);
        assertThat(captured.taskTypeId()).isEqualTo(9);
        assertThat(captured.statusMappings())
                .containsEntry("MEETING_BOOKED", 201)
                .containsEntry("NOT_INTERESTED", 203)
                .containsEntry("NEW_LEAD", 205)
                .containsEntry("FAILED", 999)
                .containsEntry("CUSTOM_OUTCOME", 888);

        verify(auditUseCase).record(COMPANY_ID, "CRM_SETTINGS_UPDATE", "crm_settings", String.valueOf(COMPANY_ID), null);
        assertThat(result).isSameAs(savedReturn);
    }

    @Test
    void dispositionFallbacksWorkCorrectly() {
        CrmSettings settings = new CrmSettings(COMPANY_ID, null, null,
                Map.of(
                        "MEETING_BOOKED", 11,
                        "PROMISE_TO_PAY", 12,
                        "NOT_INTERESTED", 13,
                        "CALLBACK_REQUESTED", 14,
                        "NEW_LEAD", 15
                ), null);

        assertThat(settings.statusFor(Disposition.PROMISE_TO_PAY)).isEqualTo(12);
        assertThat(settings.statusFor(Disposition.REFUSED)).isEqualTo(13);
        assertThat(settings.statusFor(Disposition.DO_NOT_CALL)).isEqualTo(13);
        assertThat(settings.statusFor(Disposition.NO_ANSWER)).isEqualTo(14);
        assertThat(settings.statusFor(Disposition.CALLBACK_REQUESTED)).isEqualTo(14);
        assertThat(settings.statusFor(Disposition.CARRIER_REJECTED)).isEqualTo(14);
        assertThat(settings.statusFor(Disposition.COMPLETED)).isNull();
    }
}
