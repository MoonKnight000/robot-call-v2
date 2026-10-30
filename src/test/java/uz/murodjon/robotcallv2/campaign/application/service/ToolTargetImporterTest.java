package uz.murodjon.robotcallv2.campaign.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.campaign.application.dto.ParsedTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetSource;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetSourceProvider;
import uz.murodjon.robotcallv2.integration.application.port.input.IntegrationToolUseCase;
import uz.murodjon.robotcallv2.shared.csv.CsvRowError;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.tool.application.dto.ToolTestResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolTargetImporterTest {

    private static final long COMPANY = 7;

    private final IntegrationToolUseCase integrationToolUseCase = mock(IntegrationToolUseCase.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ToolTargetImporter importer = new ToolTargetImporter(integrationToolUseCase,
            new TargetApiImporter(mock(uz.murodjon.robotcallv2.campaign.application.port.output.TargetSourceFetcherPort.class),
                    objectMapper),
            objectMapper);

    private static TargetSource source() {
        return new TargetSource(5, TargetSourceProvider.TOOL, null, null, null, null, null, "data.items", "phone",
                "leadId", "lang", true, true, true, List.of(), "list_debtors", Map.of("status", "OVERDUE"),
                null, null, null);
    }

    @Test
    void theToolsRawAnswerIsReadLikeAnyOtherSource() {
        Object raw = Map.of("data", Map.of("items", List.of(
                Map.of("phone", "+998901234567", "leadId", 42, "lang", "uz", "debt", 1500),
                Map.of("name", "no phone here"))));
        when(integrationToolUseCase.runEnabledReadTool(COMPANY, "list_debtors", Map.of("status", "OVERDUE")))
                .thenReturn(new ToolTestResponse(Map.of(), raw, null));
        List<CsvRowError> errors = new ArrayList<>();

        List<ParsedTarget> targets = importer.fetchTargets(COMPANY, source(), errors);

        assertThat(targets).hasSize(1);
        assertThat(targets.get(0).phone()).isEqualTo("+998901234567");
        assertThat(targets.get(0).clientId()).isEqualTo(42);
        assertThat(targets.get(0).language()).isEqualTo("uz");
        assertThat(targets.get(0).contextJson()).contains("\"debt\":1500").doesNotContain("phone");
        assertThat(errors).hasSize(1);
    }

    @Test
    void aToolThatAnsweredNothingFailsTheSync() {
        when(integrationToolUseCase.runEnabledReadTool(COMPANY, "list_debtors", Map.of("status", "OVERDUE")))
                .thenReturn(new ToolTestResponse(Map.of(), null, null));

        assertThatThrownBy(() -> importer.fetchTargets(COMPANY, source(), new ArrayList<>()))
                .isInstanceOf(ExternalServiceException.class);
    }
}
