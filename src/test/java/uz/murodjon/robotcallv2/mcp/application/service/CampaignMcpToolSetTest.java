package uz.murodjon.robotcallv2.mcp.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignLimitsDto;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignRow;
import uz.murodjon.robotcallv2.campaign.application.dto.UpdateCampaignRequest;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignVariantUseCase;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.shared.exception.AppException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CampaignMcpToolSetTest {

    private static final long COMPANY_ID = 1L;

    private final ObjectMapper json = new ObjectMapper();
    private final CampaignUseCase campaignUseCase = mock(CampaignUseCase.class);
    private final CampaignTargetUseCase campaignTargetUseCase = mock(CampaignTargetUseCase.class);
    private final CampaignMcpToolSet toolSet = new CampaignMcpToolSet(campaignUseCase, campaignTargetUseCase,
            mock(CampaignVariantUseCase.class), Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void aDraftsOnlyCallerMayNotChangeARunningCampaign() throws Exception {
        when(campaignUseCase.requireCampaignRow(COMPANY_ID, 5L)).thenReturn(row(CampaignStatus.ACTIVE));

        assertThatThrownBy(() -> call("update_campaign_draft", """
                {"campaignId": 5, "name": "Yangi"}""", true))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.MCP_CAMPAIGN_NOT_DRAFT);
        assertThatThrownBy(() -> call("import_campaign_targets", """
                {"campaignId": 5, "csv": "phone\\n+998901234567\\n"}""", true))
                .isInstanceOf(AppException.class);
        verify(campaignUseCase, never()).updateCampaign(anyLong(), anyLong(), any());
        verify(campaignTargetUseCase, never()).importTargetsCsv(anyLong(), anyLong(), anyString());
    }

    @Test
    void anUpdateKeepsOmittedFieldsAndMergesAGroupFieldByField() throws Exception {
        when(campaignUseCase.requireCampaignRow(COMPANY_ID, 5L)).thenReturn(row(CampaignStatus.DRAFT));

        call("update_campaign_draft", """
                {"campaignId": 5, "limits": {"dailyCallCap": 300}}""", true);

        ArgumentCaptor<UpdateCampaignRequest> request = ArgumentCaptor.forClass(UpdateCampaignRequest.class);
        verify(campaignUseCase).updateCampaign(eq(COMPANY_ID), eq(5L), request.capture());
        assertThat(request.getValue().name()).isEqualTo("Qarzdorlar");
        assertThat(request.getValue().aiAgentId()).isEqualTo(4L);
        assertThat(request.getValue().limits().dailyCallCap()).isEqualTo(300);
        assertThat(request.getValue().limits().maxConcurrentCalls()).isEqualTo(5);
        assertThat(request.getValue().version()).isEqualTo(3L);
    }

    @Test
    void thereIsNoToolThatLaunchesACampaign() {
        assertThat(toolSet.findTools()).extracting(McpToolDefinition::name)
                .noneMatch(name -> name.contains("start") || name.contains("launch") || name.contains("resume"));
    }

    private Object call(String name, String arguments, boolean draftOnly) throws Exception {
        McpToolDefinition tool = toolSet.findTools().stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
        return tool.handler().call(COMPANY_ID, name, (ObjectNode) json.readTree(arguments), draftOnly);
    }

    private static CampaignRow row(CampaignStatus status) {
        return new CampaignRow(5L, "Qarzdorlar", status, 4L, "Aziza", COMPANY_ID, null, null, 5,
                null, null, new CampaignLimitsDto(5, 100, null, null, null), null, null, null, null, null, null,
                null, null, null, null, 3L);
    }
}
