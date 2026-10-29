package uz.murodjon.robotcallv2.aiagent.application.service;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeProviderRegistry;
import uz.murodjon.robotcallv2.agent.stt.SttProviderSelector;
import uz.murodjon.robotcallv2.agent.tts.TtsProviderSelector;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentCallBehaviourDto;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentDataPolicyDto;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentScriptDto;
import uz.murodjon.robotcallv2.aiagent.application.dto.AiAgentRow;
import uz.murodjon.robotcallv2.aiagent.application.dto.CreateAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.application.dto.UpdateAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentCatalogUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AgentRolloutRepository;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AiAgentRepository;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AiAgentVersionRepository;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentVersion;
import uz.murodjon.robotcallv2.aiagent.domain.entity.EngineProfile;
import uz.murodjon.robotcallv2.aiagent.domain.entity.TemplateDefaults;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentTemplate;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentVersionStatus;
import uz.murodjon.robotcallv2.aiagent.domain.event.AiAgentClonedEvent;
import uz.murodjon.robotcallv2.aiagent.domain.service.AiAgentSnapshot;
import uz.murodjon.robotcallv2.aiagent.domain.service.AiAgentTemplates;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelConfigUseCase;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelUseCase;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.company.domain.enums.Language;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;
import uz.murodjon.robotcallv2.shared.exception.*;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.SipTrunkUseCase;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;
import uz.murodjon.robotcallv2.voice.application.port.input.TtsVoiceUseCase;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AiAgentServiceTest {

    private static final long COMPANY_ID = 1L;
    private static final long AGENT_ID = 7L;

    private AiAgentRepository repository;
    private AiAgentVersionRepository versionRepository;
    private CompanyConfigUseCase companyConfigUseCase;
    private AuditUseCase auditUseCase;
    private CurrentUser currentUser;
    private ApplicationEventPublisher eventPublisher;
    private AiAgentCatalogUseCase catalogUseCase;
    private AiAgentService service;

    @BeforeEach
    void setUp() {
        repository = mock(AiAgentRepository.class);
        versionRepository = mock(AiAgentVersionRepository.class);
        companyConfigUseCase = mock(CompanyConfigUseCase.class);
        auditUseCase = mock(AuditUseCase.class);
        currentUser = mock(CurrentUser.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        catalogUseCase = mock(AiAgentCatalogUseCase.class);
        AiAgentDrafts drafts = new AiAgentDrafts(repository, versionRepository,
                mock(AiModelConfigUseCase.class), mock(SipTrunkUseCase.class), companyConfigUseCase);
        AiAgentEngineGuard engineGuard = new AiAgentEngineGuard(mock(SttProviderSelector.class),
                mock(TtsProviderSelector.class), mock(RealtimeProviderRegistry.class), mock(TtsVoiceUseCase.class),
                mock(AiModelUseCase.class), companyConfigUseCase);
        service = new AiAgentService(repository, versionRepository, drafts, engineGuard, mock(ScenarioUseCase.class),
                companyConfigUseCase, mock(UserUseCase.class), auditUseCase, currentUser, eventPublisher,
                mock(AgentRolloutRepository.class), catalogUseCase);
        when(companyConfigUseCase.resolveLanguage(eq(COMPANY_ID), any())).thenReturn("uz-UZ");
    }

    private static AiAgent agent() {
        return AiAgentFixtures.agent(AGENT_ID, COMPANY_ID, 3L, "uz-UZ", null);
    }

    /** A template fills in only what the form left empty; with no engine picked the agent runs CASCADE. */
    @Test
    void createAgentFillsTheOpeningAndPromptFromTheChosenTemplate() {
        when(repository.create(any())).thenReturn(AGENT_ID);
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(agent());

        service.createAgent(COMPANY_ID, createRequest(AgentTemplate.BUSINESS, null, null, null));

        ArgumentCaptor<AiAgent> created = ArgumentCaptor.forClass(AiAgent.class);
        verify(repository).create(created.capture());
        TemplateDefaults preset = AiAgentTemplates.getPreset(AgentTemplate.BUSINESS, "uz-UZ");
        AiAgent agent = created.getValue();
        assertThat(agent.name()).isEqualTo("Sotuv agenti");
        assertThat(agent.script().firstMessage()).isEqualTo(preset.firstMessage());
        assertThat(agent.script().systemPrompt()).isEqualTo(preset.systemPrompt());
        assertThat(agent.dataNeeded()).isEqualTo(preset.dataNeeded());
        assertThat(agent.speechEngine().mode()).isEqualTo(PipelineMode.CASCADE);
        assertThat(agent.persona()).isEqualTo(AgentPersona.AI_ASSISTANT);
        assertThat(agent.version()).isZero();
        verify(auditUseCase).record(COMPANY_ID, "AI_AGENT_CREATE", "ai_agent", String.valueOf(AGENT_ID), "Sotuv agenti");
    }

    /** A human-like persona skips the §11.1 disclosure, so it is the company's to allow, not the editor's. */
    @Test
    void createAgentAsAPersonIsRefusedWhenTheCompanyIsNotAllowedOne() {
        when(companyConfigUseCase.find(COMPANY_ID)).thenReturn(CompanyConfig.defaults());

        assertRefused(() -> service.createAgent(COMPANY_ID, createRequest(null, AgentPersona.HUMAN_LIKE, null, null)),
                ConflictException.class, ErrorCode.AI_AGENT_HUMAN_LIKE_NOT_ALLOWED);
        verify(repository, never()).create(any());
    }

    @Test
    void createAgentAsAPersonIsSavedOnceThePlatformAllowedIt() {
        when(companyConfigUseCase.find(COMPANY_ID)).thenReturn(CompanyConfig.settings(LocalTime.of(9, 0),
                LocalTime.of(20, 0), "Asia/Tashkent", Language.UZ_UZ, List.of(Language.UZ_UZ), null, 3, 1, true, true));
        when(repository.create(any())).thenReturn(AGENT_ID);
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(agent());

        service.createAgent(COMPANY_ID, createRequest(null, AgentPersona.HUMAN_LIKE, null, null));

        ArgumentCaptor<AiAgent> created = ArgumentCaptor.forClass(AiAgent.class);
        verify(repository).create(created.capture());
        assertThat(created.getValue().persona()).isEqualTo(AgentPersona.HUMAN_LIKE);
        assertThat(created.getValue().disclosureEnabled()).isFalse();
    }

    @Test
    void createAgentNamingAnotherCompanysTransferQueueIsNotFound() {
        when(repository.existsCallQueueByCompanyIdAndId(COMPANY_ID, 99L)).thenReturn(false);

        assertRefused(() -> service.createAgent(COMPANY_ID, createRequest(null, null, 99L, null)),
                NotFoundException.class, ErrorCode.CALL_QUEUE_NOT_FOUND);
        verify(repository, never()).create(any());
    }

    @Test
    void updateAgentSavedBySomeoneElseSinceItWasReadIs409() {
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(agent().withVersion(5));
        when(repository.update(eq(COMPANY_ID), eq(AGENT_ID), any())).thenReturn(false);

        assertRefused(() -> service.updateAgent(COMPANY_ID, AGENT_ID, updateRequest("Yangi nom", 4L)),
                ConflictException.class, ErrorCode.ENTITY_VERSION_CONFLICT);
        ArgumentCaptor<AiAgent> written = ArgumentCaptor.forClass(AiAgent.class);
        verify(repository).update(eq(COMPANY_ID), eq(AGENT_ID), written.capture());
        assertThat(written.getValue().version()).isEqualTo(4L);
        verifyNoInteractions(auditUseCase);
    }

    /** A client that predates the version field edits against the version just read. */
    @Test
    void updateAgentWithoutAVersionEditsAgainstTheOneJustRead() {
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(agent().withVersion(5));
        when(repository.update(eq(COMPANY_ID), eq(AGENT_ID), any())).thenReturn(true);

        service.updateAgent(COMPANY_ID, AGENT_ID, updateRequest(" Yangi nom ", null));

        ArgumentCaptor<AiAgent> written = ArgumentCaptor.forClass(AiAgent.class);
        verify(repository).update(eq(COMPANY_ID), eq(AGENT_ID), written.capture());
        assertThat(written.getValue().version()).isEqualTo(5L);
        assertThat(written.getValue().name()).isEqualTo("Yangi nom");
        assertThat(written.getValue().script()).isEqualTo(agent().script());
        verify(auditUseCase).record(COMPANY_ID, "AI_AGENT_UPDATE", "ai_agent", String.valueOf(AGENT_ID), "Yangi nom");
    }

    /** The simple form shows the profile the agent's engine choices match, and none once tuned by hand. */
    @Test
    void findAgentRowNamesTheEngineProfileTheAgentMatches() {
        AiAgent agent = agent();
        EngineProfile matching = new EngineProfile("balanced", "Muvozanatli", null,
                agent.speechEngine().sttProvider(), agent.speechEngine().sttModel(), agent.llmModel(),
                agent.fastLlmModel(), agent.temperature(), agent.maxOutputTokens());
        EngineProfile other = new EngineProfile("quality", "Sifatli", null,
                "google", "latest_long", "other-model", "other-fast", 0.9, 999);
        when(catalogUseCase.findEngineProfiles()).thenReturn(List.of(other, matching));
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(agent);

        assertThat(service.findAgentRow(COMPANY_ID, AGENT_ID).engineProfileId()).isEqualTo("balanced");

        when(catalogUseCase.findEngineProfiles()).thenReturn(List.of(other));
        assertThat(service.findAgentRow(COMPANY_ID, AGENT_ID).engineProfileId()).isNull();
    }

    @Test
    void updateAgentOfAnotherCompanyIsNotFound() {
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(agent());

        assertRefused(() -> service.updateAgent(2L, AGENT_ID, updateRequest("Begona", null)),
                NotFoundException.class, ErrorCode.AI_AGENT_NOT_FOUND);
        verify(repository, never()).update(anyLong(), anyLong(), any());
    }

    @Test
    void deleteAgentStillUsedByACampaignIsRefused() {
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(agent());
        when(repository.countCampaignsUsing(AGENT_ID)).thenReturn(2L);

        assertRefused(() -> service.deleteAgent(COMPANY_ID, AGENT_ID), ConflictException.class, ErrorCode.AI_AGENT_IN_USE);
        verify(repository, never()).delete(anyLong(), anyLong());
    }

    @Test
    void deleteAgentStillAnsweringAnInboundRouteIsRefused() {
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(agent());
        when(repository.countInboundRoutesUsing(AGENT_ID)).thenReturn(1L);

        assertRefused(() -> service.deleteAgent(COMPANY_ID, AGENT_ID), ConflictException.class, ErrorCode.AI_AGENT_IN_USE);
        verify(repository, never()).delete(anyLong(), anyLong());
    }

    /** The tool links live in the tool feature, which copies them on hearing the event. */
    @Test
    void cloneAgentCopiesItUnpublishedAndTellsTheOtherFeatures() {
        long copyId = 21L;
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(agent().withPublishedVersion(3).withVersion(8));
        when(repository.findByCompanyIdAndId(COMPANY_ID, copyId))
                .thenReturn(AiAgentFixtures.agent(copyId, COMPANY_ID, 3L, "uz-UZ", null));
        when(repository.create(any())).thenReturn(copyId);
        when(currentUser.id()).thenReturn(Optional.of(5L));

        AiAgentRow row = service.cloneAgent(COMPANY_ID, AGENT_ID);

        ArgumentCaptor<AiAgent> copy = ArgumentCaptor.forClass(AiAgent.class);
        verify(repository).create(copy.capture());
        assertThat(copy.getValue().name()).isEqualTo("Test agent (nusxa)");
        assertThat(copy.getValue().id()).isZero();
        assertThat(copy.getValue().publishedVersion()).isNull();
        assertThat(copy.getValue().version()).isZero();
        assertThat(copy.getValue().createdBy()).isEqualTo(5L);
        verify(eventPublisher).publishEvent(new AiAgentClonedEvent(COMPANY_ID, AGENT_ID, copyId));
        assertThat(row.id()).isEqualTo(copyId);
    }

    /** The lock counter moves on every save; a draft saved again with nothing changed still matches what went live. */
    @Test
    void findAgentRowRightAfterPublishHasNoDraftChangesEvenOnceSavedAgain() {
        AiAgent draft = agent().withVersion(3);
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(draft);
        when(versionRepository.findPublished(COMPANY_ID, AGENT_ID))
                .thenReturn(published(1, AiAgentSnapshot.fromJson(AiAgentSnapshot.toJson(draft.withVersion(2)))));

        AiAgentRow row = service.findAgentRow(COMPANY_ID, AGENT_ID);

        assertThat(row.publishedVersion()).isEqualTo(1);
        assertThat(row.hasDraftChanges()).isFalse();
        assertThat(row.version()).isEqualTo(3L);
    }

    @Test
    void findAgentRowAfterAnEditSincePublishHasDraftChanges() {
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID))
                .thenReturn(agent().withFirstMessage("Yangi salom").withVersion(4));
        when(versionRepository.findPublished(COMPANY_ID, AGENT_ID)).thenReturn(published(1, agent()));

        assertThat(service.findAgentRow(COMPANY_ID, AGENT_ID).hasDraftChanges()).isTrue();
    }

    /**
     * Stripping personal data while the recording is kept is refused when the agent is
     * created too, not only on the advanced screen: the recording defaults to kept.
     */
    @Test
    void createAgentStrippingPersonalDataWhileKeepingTheRecordingIsRefused() {
        assertRefused(() -> service.createAgent(COMPANY_ID, createRequest(null, null, null, true)),
                ValidationException.class, ErrorCode.ZERO_PII_STORE_AUDIO_CONFLICT);
        verify(repository, never()).create(any());
    }

    private static AiAgentVersion published(int number, AiAgent snapshot) {
        return new AiAgentVersion(100L + number, AGENT_ID, COMPANY_ID, number, AgentVersionStatus.PUBLISHED,
                snapshot.withPublishedVersion(number), null, Instant.parse("2026-09-01T10:00:00Z"), null);
    }

    @Test
    void createAgentWithoutEngineChoicesTakesTheBalancedEngineProfile() {
        EngineProfile balanced = new EngineProfile("balanced", "Muvozanatli", null,
                "yandex", "general", "gemini-3.8-flash", "gemini-3.5-flash-lite", 0.3, 300);
        when(catalogUseCase.findEngineProfiles()).thenReturn(List.of(balanced));
        when(repository.create(any())).thenReturn(AGENT_ID);
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(agent());

        service.createAgent(COMPANY_ID, createRequest(null, null, null, null));

        ArgumentCaptor<AiAgent> created = ArgumentCaptor.forClass(AiAgent.class);
        verify(repository).create(created.capture());
        AiAgent agent = created.getValue();
        assertThat(agent.llmModel()).isEqualTo("gemini-3.8-flash");
        assertThat(agent.fastLlmModel()).isEqualTo("gemini-3.5-flash-lite");
        assertThat(agent.speechEngine().sttProvider()).isEqualTo("yandex");
        assertThat(agent.speechEngine().sttModel()).isEqualTo("general");
    }

    private static CreateAiAgentRequest createRequest(AgentTemplate templateId, AgentPersona persona, Long transferQueueId,
                                                      Boolean zeroPiiRetention) {
        return new CreateAiAgentRequest(
                " Sotuv agenti ", null, null, null, null, persona, null,
                new AgentScriptDto(templateId, null, null, null, null, null),
                null, null,
                null, null, null, null,
                null,
                new AgentCallBehaviourDto(null, null, null, null, null, null, null, null, transferQueueId, null, null, null),
                null,
                new AgentDataPolicyDto(zeroPiiRetention, null, null, null),
                null,
                null, null,
                null, null,
                null, null);
    }

    /** Only the name and the lock version; everything else left as the agent has it. */
    private static UpdateAiAgentRequest updateRequest(String name, Long version) {
        return new UpdateAiAgentRequest(
                name, null, null, null, null, null, null,
                null, null, null,
                null, null, null, null,
                null, null, null, null,
                null,
                null, null,
                null, null,
                null, null,
                version);
    }

    private static <E extends AppException> void assertRefused(ThrowingCallable call, Class<E> type, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(type, e -> assertThat(e.code()).isEqualTo(code));
    }
}
