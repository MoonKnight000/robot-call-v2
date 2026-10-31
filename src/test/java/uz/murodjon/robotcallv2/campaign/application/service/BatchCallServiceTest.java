package uz.murodjon.robotcallv2.campaign.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.murodjon.robotcallv2.campaign.application.dto.BatchCallRequest;
import uz.murodjon.robotcallv2.campaign.application.dto.BatchCallResponse;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignStatusResponse;
import uz.murodjon.robotcallv2.campaign.application.dto.CreateCampaignRequest;
import uz.murodjon.robotcallv2.campaign.application.dto.CreateCampaignResponse;
import uz.murodjon.robotcallv2.campaign.application.dto.TargetImportResult;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchCallServiceTest {

    private static final long COMPANY_ID = 1L;
    private static final long CAMPAIGN_ID = 42L;
    private static final String CSV = "phone\n+998901234567\n";

    @Mock
    private CampaignUseCase campaignUseCase;
    @Mock
    private CampaignTargetUseCase campaignTargetUseCase;

    private BatchCallService service;

    @BeforeEach
    void setUp() {
        service = new BatchCallService(campaignUseCase, campaignTargetUseCase);
    }

    @Test
    void happyPathRunsCreateThenImportThenStart() {
        TargetImportResult imported = new TargetImportResult(1, 0, 0, List.of());
        when(campaignUseCase.createCampaign(eq(COMPANY_ID), any()))
                .thenReturn(new CreateCampaignResponse(CAMPAIGN_ID, "Oktabr", CampaignStatus.DRAFT));
        when(campaignTargetUseCase.importTargetsCsv(COMPANY_ID, CAMPAIGN_ID, CSV)).thenReturn(imported);
        when(campaignUseCase.start(COMPANY_ID, CAMPAIGN_ID, true))
                .thenReturn(new CampaignStatusResponse(CAMPAIGN_ID, CampaignStatus.ACTIVE));

        BatchCallResponse response = service.startBatchCall(COMPANY_ID, request(true));

        InOrder order = inOrder(campaignUseCase, campaignTargetUseCase);
        ArgumentCaptor<CreateCampaignRequest> created = ArgumentCaptor.forClass(CreateCampaignRequest.class);
        order.verify(campaignUseCase).createCampaign(eq(COMPANY_ID), created.capture());
        order.verify(campaignTargetUseCase).importTargetsCsv(COMPANY_ID, CAMPAIGN_ID, CSV);
        order.verify(campaignUseCase).start(COMPANY_ID, CAMPAIGN_ID, true);
        assertThat(created.getValue().name()).isEqualTo("Oktabr");
        assertThat(created.getValue().aiAgentId()).isEqualTo(3L);
        assertThat(created.getValue().dialing()).isNull();
        assertThat(response).isEqualTo(new BatchCallResponse(CAMPAIGN_ID, imported, CampaignStatus.ACTIVE));
    }

    /** The transaction rolls the campaign back; the refusal itself reaches the caller unchanged. */
    @Test
    void aRefusedStartIsThrownAsIs() {
        when(campaignUseCase.start(COMPANY_ID, CAMPAIGN_ID, false))
                .thenThrow(new ConflictException(ErrorCode.CAMPAIGN_HAS_NO_TARGETS, CAMPAIGN_ID));

        assertThatThrownBy(() -> service.startBatchCall(COMPANY_ID, request(false)))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CAMPAIGN_HAS_NO_TARGETS));
    }

    private static BatchCallRequest request(boolean immediate) {
        return new BatchCallRequest("Oktabr", 3L, CSV, null, null, null, null, null, null, immediate, true);
    }
}
