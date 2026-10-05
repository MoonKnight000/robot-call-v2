package uz.murodjon.robotcallv2.pack.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.aiagent.application.dto.AiAgentRow;
import uz.murodjon.robotcallv2.aiagent.application.dto.CreateAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AgentEvaluationUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentTestCase;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.conversion.application.dto.ConversionGoalRequest;
import uz.murodjon.robotcallv2.conversion.application.port.input.ConversionUseCase;
import uz.murodjon.robotcallv2.conversion.domain.entity.ConversionGoal;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.CreateKnowledgeSourceItem;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.CreateKnowledgeSourceRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeSourceRow;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeSourceUseCase;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceType;
import uz.murodjon.robotcallv2.pack.application.dto.InstallPackRequest;
import uz.murodjon.robotcallv2.pack.application.dto.PackInstallResponse;
import uz.murodjon.robotcallv2.pack.application.port.output.PackGeneratorPort;
import uz.murodjon.robotcallv2.pack.application.mapper.PackMapper;
import uz.murodjon.robotcallv2.pack.domain.entity.PackSecret;
import uz.murodjon.robotcallv2.pack.domain.entity.VerticalPack;
import uz.murodjon.robotcallv2.pack.infrastructure.adapter.ClasspathJsonPackCatalogAdapter;
import uz.murodjon.robotcallv2.secret.application.dto.SecretRow;
import uz.murodjon.robotcallv2.secret.application.port.input.SecretUseCase;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.tool.application.dto.ToolRow;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolImportUseCase;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolUseCase;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Runs the shipped online-shop pack against mocked feature use cases. */
class PackServiceTest {

    private static final long COMPANY_ID = 7L;
    private static final long AGENT_ID = 42L;
    private static final long TOOL_ID = 5L;

    private final ClasspathJsonPackCatalogAdapter packCatalog = new ClasspathJsonPackCatalogAdapter(new ObjectMapper());
    private final VerticalPack ecommerce = packCatalog.findByKey("ecommerce");

    private AiAgentUseCase aiAgentUseCase;
    private ToolUseCase toolUseCase;
    private KnowledgeSourceUseCase knowledgeSourceUseCase;
    private ConversionUseCase conversionUseCase;
    private SecretUseCase secretUseCase;
    private PackService service;

    @BeforeEach
    void setUp() {
        aiAgentUseCase = mock(AiAgentUseCase.class);
        toolUseCase = mock(ToolUseCase.class);
        knowledgeSourceUseCase = mock(KnowledgeSourceUseCase.class);
        AgentEvaluationUseCase agentEvaluationUseCase = mock(AgentEvaluationUseCase.class);
        conversionUseCase = mock(ConversionUseCase.class);
        secretUseCase = mock(SecretUseCase.class);
        service = new PackService(packCatalog, new PackMapper(), aiAgentUseCase, toolUseCase, knowledgeSourceUseCase,
                agentEvaluationUseCase, conversionUseCase, secretUseCase, mock(AuditUseCase.class),
                mock(ToolImportUseCase.class), mock(CompanyConfigUseCase.class), mock(PackGeneratorPort.class));

        AiAgentRow agent = mock(AiAgentRow.class);
        when(agent.id()).thenReturn(AGENT_ID);
        when(aiAgentUseCase.createAgent(eq(COMPANY_ID), any())).thenReturn(agent);
        ToolRow tool = mock(ToolRow.class);
        when(tool.id()).thenReturn(TOOL_ID);
        when(toolUseCase.createTool(eq(COMPANY_ID), any())).thenReturn(tool);
        when(agentEvaluationUseCase.createTestCase(eq(COMPANY_ID), eq(AGENT_ID), any())).thenReturn(
                new AgentTestCase(9L, COMPANY_ID, AGENT_ID, "case", "persona", null, null, null, null,
                        null, null, 0, null));
        when(conversionUseCase.upsertGoal(eq(COMPANY_ID), any())).thenAnswer(invocation -> {
            ConversionGoalRequest request = invocation.getArgument(1);
            return goal(request.goalKey());
        });
    }

    @Test
    void anUnknownPackIsNotFoundAndCreatesNothing() {
        assertThatThrownBy(() -> service.installPack(COMPANY_ID, "no-such-pack", null))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(aiAgentUseCase, toolUseCase);
    }

    @Test
    void installCreatesTheAgentAndAttachesEveryToolToIt() {
        PackInstallResponse response = service.installPack(COMPANY_ID, "ecommerce", null);

        ArgumentCaptor<CreateAiAgentRequest> agentRequest = ArgumentCaptor.forClass(CreateAiAgentRequest.class);
        verify(aiAgentUseCase).createAgent(eq(COMPANY_ID), agentRequest.capture());
        assertThat(agentRequest.getValue().scenarioDefinition()).isEqualTo(ecommerce.agent().scenario());
        // Company tools are offered only on the cascade pipeline.
        assertThat(agentRequest.getValue().pipelineMode()).isEqualTo(PipelineMode.CASCADE);

        int toolCount = ecommerce.tools().size();
        verify(toolUseCase, times(toolCount)).attachTool(COMPANY_ID, AGENT_ID, TOOL_ID);
        assertThat(response.agentId()).isEqualTo(AGENT_ID);
        assertThat(response.toolIds()).hasSize(toolCount);
        assertThat(response.testCaseIds()).hasSize(ecommerce.testCases().size());
        assertThat(response.conversionGoalKey()).isEqualTo(ecommerce.conversionGoal().goalKey());
    }

    @Test
    void withoutACatalogUrlNoFeedIsCreated() {
        PackInstallResponse response = service.installPack(COMPANY_ID, "ecommerce", new InstallPackRequest(" ", null));

        assertThat(response.catalogSourceId()).isNull();
        verifyNoInteractions(knowledgeSourceUseCase);
    }

    @Test
    void aCatalogUrlBecomesAFeedOfTheNewAgent() {
        KnowledgeSourceRow source = mock(KnowledgeSourceRow.class);
        when(source.id()).thenReturn(11L);
        when(knowledgeSourceUseCase.createKnowledgeSources(eq(COMPANY_ID), any())).thenReturn(List.of(source));

        PackInstallResponse response = service.installPack(COMPANY_ID, "ecommerce",
                new InstallPackRequest(" https://shop.example.uz/feed.csv ", null));

        ArgumentCaptor<CreateKnowledgeSourceRequest> request = ArgumentCaptor.forClass(CreateKnowledgeSourceRequest.class);
        verify(knowledgeSourceUseCase).createKnowledgeSources(eq(COMPANY_ID), request.capture());
        CreateKnowledgeSourceItem feed = request.getValue().documents().getFirst();
        assertThat(request.getValue().agentId()).isEqualTo(AGENT_ID);
        assertThat(feed.sourceType()).isEqualTo(KnowledgeSourceType.STRUCTURED_FEED);
        assertThat(feed.url()).isEqualTo("https://shop.example.uz/feed.csv");
        assertThat(feed.feedKeyField()).isEqualTo(ecommerce.catalogFeed().feedKeyField());
        assertThat(response.catalogSourceId()).isEqualTo(11L);
    }

    /** The company's own goal keeps its window: other agents' calls are counted against it. */
    @Test
    void anExistingGoalUnderThePacksKeyIsLeftAlone() {
        when(conversionUseCase.findGoals(COMPANY_ID)).thenReturn(List.of(goal(ecommerce.conversionGoal().goalKey())));

        PackInstallResponse response = service.installPack(COMPANY_ID, "ecommerce", null);

        verify(conversionUseCase, never()).upsertGoal(anyLong(), any());
        assertThat(response.conversionGoalKey()).isNull();
    }

    @Test
    void theMissingSecretsAreTheOnesTheCompanyHasNotSet() {
        List<PackSecret> declared = ecommerce.secrets();
        when(secretUseCase.findSecrets(COMPANY_ID)).thenReturn(List.of(
                new SecretRow(1L, COMPANY_ID, declared.getFirst().key(), "******", null, null, null)));

        PackInstallResponse response = service.installPack(COMPANY_ID, "ecommerce", null);

        assertThat(response.missingSecrets()).containsExactlyElementsOf(declared.subList(1, declared.size()));
    }

    private static ConversionGoal goal(String key) {
        return new ConversionGoal(1L, COMPANY_ID, key, "goal", 24, null, true, null, null);
    }
}
