package uz.murodjon.robotcallv2.campaign.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import uz.murodjon.robotcallv2.campaign.application.dto.BatchCallRequest;
import uz.murodjon.robotcallv2.campaign.application.dto.BatchCallResponse;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignStatusResponse;
import uz.murodjon.robotcallv2.campaign.application.dto.CreateCampaignRequest;
import uz.murodjon.robotcallv2.campaign.application.dto.CreateCampaignResponse;
import uz.murodjon.robotcallv2.campaign.application.dto.TargetImportResult;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignType;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BatchCallServiceTest {

    private static final long COMPANY_ID = 1L;
    private static final long CAMPAIGN_ID = 12L;
    private static final String CSV = "phone\n+998901234567\n";

    private CampaignUseCase campaignUseCase;
    private CampaignTargetUseCase campaignTargetUseCase;
    private BatchCallService service;

    @BeforeEach
    void setUp() {
        campaignUseCase = mock(CampaignUseCase.class);
        campaignTargetUseCase = mock(CampaignTargetUseCase.class);
        service = new BatchCallService(campaignUseCase, campaignTargetUseCase);
        when(campaignUseCase.createCampaign(eq(COMPANY_ID), any()))
                .thenReturn(new CreateCampaignResponse(CAMPAIGN_ID, CampaignStatus.DRAFT));
    }

    @Test
    void createsImportsAndStartsInThatOrder() {
        TargetImportResult imported = new TargetImportResult(CAMPAIGN_ID, 1, List.of(501L), 0, 0, List.of(), List.of());
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
        assertThat(created.getValue().type()).isEqualTo(CampaignType.DEBT_COLLECTION);
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
        return new BatchCallRequest("Oktabr", CampaignType.DEBT_COLLECTION, 3L, CSV, null, immediate);
    }
}
