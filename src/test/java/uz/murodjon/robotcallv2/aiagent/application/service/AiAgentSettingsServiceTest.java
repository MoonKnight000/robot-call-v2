package uz.murodjon.robotcallv2.aiagent.application.service;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentAdvancedDto;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentLimitsDto;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentPronunciationDto;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentScenarioDto;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AiAgentRepository;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AiAgentVersionRepository;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentLimits;
import uz.murodjon.robotcallv2.aiagent.domain.entity.PronunciationRule;
import uz.murodjon.robotcallv2.aiagent.domain.enums.InterruptionSensitivity;
import uz.murodjon.robotcallv2.aiagent.domain.enums.ScenarioMode;
import uz.murodjon.robotcallv2.aiagent.domain.enums.VoicemailAction;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelConfigUseCase;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModelConfig;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.shared.exception.AppException;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.SipTrunkUseCase;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AiAgentSettingsServiceTest {

    private static final long COMPANY_ID = 1L;
    private static final long AGENT_ID = 7L;
    /** Limits 600/30/15/10/1000; audio stored, no PII stripping, transcript kept 90 days; no transfer queue. */
    private static final AiAgent AGENT = AiAgentFixtures.agent(AGENT_ID, COMPANY_ID, 3L, "uz-UZ", null).withVersion(4);

    private AiAgentRepository repository;
    private AiModelConfigUseCase aiModelConfigUseCase;
    private AuditUseCase auditUseCase;
    private AiAgentSettingsService service;

    @BeforeEach
    void setUp() {
        repository = mock(AiAgentRepository.class);
        aiModelConfigUseCase = mock(AiModelConfigUseCase.class);
        auditUseCase = mock(AuditUseCase.class);
        AiAgentDrafts drafts = new AiAgentDrafts(repository, mock(AiAgentVersionRepository.class),
                aiModelConfigUseCase, mock(SipTrunkUseCase.class), mock(CompanyConfigUseCase.class));
        service = new AiAgentSettingsService(drafts, auditUseCase);
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(AGENT);
        when(repository.update(eq(COMPANY_ID), eq(AGENT_ID), any())).thenReturn(true);
    }

    @Test
    void aSectionOfAnotherCompanysAgentIsNotFound() {
        assertRefused(() -> service.findLimits(2L, AGENT_ID), NotFoundException.class, ErrorCode.AI_AGENT_NOT_FOUND);
        assertRefused(() -> service.updateLimits(2L, AGENT_ID, new AgentLimitsDto(20, null, null, null, null)),
                NotFoundException.class, ErrorCode.AI_AGENT_NOT_FOUND);
        verify(repository, never()).update(anyLong(), anyLong(), any());
    }

    @Test
    void updateLimitsKeepsWhatTheRequestLeftOut() {
        AgentLimitsDto result = service.updateLimits(COMPANY_ID, AGENT_ID, new AgentLimitsDto(20, null, null, null, null));

        AiAgent written = written();
        assertThat(written.limits()).isEqualTo(new AiAgentLimits(600, 30, 15, 20, 1000));
        assertThat(written.version()).isEqualTo(4L);
        assertThat(result.concurrentCallsLimit()).isEqualTo(20);
        assertThat(result.dailyCallsLimit()).isEqualTo(1000);
        verify(auditUseCase).record(COMPANY_ID, "AI_AGENT_LIMITS_UPDATE", "ai_agent", String.valueOf(AGENT_ID), "Test agent");
    }

    /** A section save is checked exactly like a whole-agent save, company call cap included. */
    @Test
    void aLimitAboveTheCompanyCallCapIsRefused() {
        when(aiModelConfigUseCase.findByCompanyId(COMPANY_ID))
                .thenReturn(AiModelConfig.overrides(null, null, null, 900, null));

        assertRefused(() -> service.updateLimits(COMPANY_ID, AGENT_ID, new AgentLimitsDto(null, null, 1200, null, null)),
                ValidationException.class, ErrorCode.AI_AGENT_LIMIT_EXCEEDS_COMPANY_CAP);
        verify(repository, never()).update(anyLong(), anyLong(), any());
    }

    @Test
    void aSectionSavedOverAnotherEditIs409() {
        when(repository.update(eq(COMPANY_ID), eq(AGENT_ID), any())).thenReturn(false);

        assertRefused(() -> service.updateLimits(COMPANY_ID, AGENT_ID, new AgentLimitsDto(20, null, null, null, null)),
                ConflictException.class, ErrorCode.ENTITY_VERSION_CONFLICT);
        assertThat(written().version()).isEqualTo(4L);
        verifyNoInteractions(auditUseCase);
    }

    /** Stripping personal data while keeping the recording keeps the very thing the setting removes. */
    @Test
    void updateAdvancedRefusesZeroPiiWhileTheAudioIsStillStored() {
        assertRefused(() -> service.updateAdvanced(COMPANY_ID, AGENT_ID, advanced(true, null, null)),
                ValidationException.class, ErrorCode.ZERO_PII_STORE_AUDIO_CONFLICT);
        verify(repository, never()).update(anyLong(), anyLong(), any());
    }

    @Test
    void updateAdvancedAcceptsZeroPiiWhenTheSameSaveStopsStoringAudio() {
        service.updateAdvanced(COMPANY_ID, AGENT_ID, advanced(true, false, null));

        AiAgent written = written();
        assertThat(written.dataPolicy().zeroPiiRetention()).isTrue();
        assertThat(written.dataPolicy().storeCallAudio()).isFalse();
        assertThat(written.dataPolicy().conversationRetentionDays()).isEqualTo(90);
    }

    @Test
    void updateAdvancedNamingAnotherCompanysQueueIsNotFound() {
        when(repository.existsCallQueueByCompanyIdAndId(COMPANY_ID, 99L)).thenReturn(false);

        assertRefused(() -> service.updateAdvanced(COMPANY_ID, AGENT_ID, advanced(null, null, 99L)),
                NotFoundException.class, ErrorCode.CALL_QUEUE_NOT_FOUND);
        verify(repository, never()).update(anyLong(), anyLong(), any());
    }

    @Test
    void updateAdvancedSetsTheOwnQueueAndKeepsTheRestOfTheCallBehaviour() {
        when(repository.existsCallQueueByCompanyIdAndId(COMPANY_ID, 5L)).thenReturn(true);

        service.updateAdvanced(COMPANY_ID, AGENT_ID, advanced(null, null, 5L));

        AiAgent written = written();
        assertThat(written.callBehaviour().transferQueueId()).isEqualTo(5L);
        assertThat(written.callBehaviour().voicemailAction()).isEqualTo(VoicemailAction.LEAVE_MESSAGE);
        assertThat(written.callBehaviour().interruptionSensitivity()).isEqualTo(InterruptionSensitivity.MEDIUM);
        assertThat(written.callBehaviour().endpointingDelayMs()).isEqualTo(700);
        assertThat(written.dataPolicy()).isEqualTo(AGENT.dataPolicy());
        verify(auditUseCase).record(COMPANY_ID, "AI_AGENT_ADVANCED_UPDATE", "ai_agent", String.valueOf(AGENT_ID), "Test agent");
    }

    @Test
    void updateScenarioWithoutStagesSpeaksOneConversationStage() {
        service.updateScenario(COMPANY_ID, AGENT_ID,
                new AgentScenarioDto(null, " Assalomu alaykum ", " Siz yordamchisiz ", null, null, null, null));

        AiAgent written = written();
        assertThat(written.script().mode()).isEqualTo(ScenarioMode.PROMPT);
        assertThat(written.script().firstMessage()).isEqualTo("Assalomu alaykum");
        assertThat(written.script().systemPrompt()).isEqualTo("Siz yordamchisiz");
        assertThat(written.script().scenarioId()).isEqualTo(3L);
        assertThat(written.script().definition().stages()).extracting(StageDef::id).containsExactly("CONVERSATION");
    }

    @Test
    void updatePronunciationWithoutRulesClearsThem() {
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID))
                .thenReturn(AGENT.withPronunciationRules(List.of(new PronunciationRule("Uysot", "Uy-sot", false, null))));

        AgentPronunciationDto result = service.updatePronunciation(COMPANY_ID, AGENT_ID, new AgentPronunciationDto(null));

        assertThat(written().pronunciationRules()).isEmpty();
        assertThat(result.rules()).isEmpty();
    }

    @Test
    void findScenarioOfAnAgentWithoutAnEmbeddedScenarioIsEmpty() {
        AgentScenarioDto scenario = service.findScenario(COMPANY_ID, AGENT_ID);

        assertThat(scenario.scenarioMode()).isEqualTo(ScenarioMode.PROMPT);
        assertThat(scenario.stages()).isEmpty();
        assertThat(scenario.guardrails()).isEmpty();
        assertThat(scenario.factSchema()).isEmpty();
        assertThat(scenario.disclosureText()).isNull();
    }

    private AiAgent written() {
        ArgumentCaptor<AiAgent> written = ArgumentCaptor.forClass(AiAgent.class);
        verify(repository).update(eq(COMPANY_ID), eq(AGENT_ID), written.capture());
        return written.getValue();
    }

    /** Only the data-policy pair and the transfer queue; every other field left as the agent has it. */
    private static AgentAdvancedDto advanced(Boolean zeroPiiRetention, Boolean storeCallAudio, Long transferQueueId) {
        return new AgentAdvancedDto(
                null, null, null, storeCallAudio, zeroPiiRetention,
                null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, transferQueueId, null, null);
    }

    private static <E extends AppException> void assertRefused(ThrowingCallable call, Class<E> type, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(type, e -> assertThat(e.code()).isEqualTo(code));
    }
}
