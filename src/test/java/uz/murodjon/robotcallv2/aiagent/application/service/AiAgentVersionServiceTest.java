package uz.murodjon.robotcallv2.aiagent.application.service;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentRolloutRow;
import uz.murodjon.robotcallv2.aiagent.application.dto.AiAgentVersionRow;
import uz.murodjon.robotcallv2.aiagent.application.dto.PublishAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.application.dto.StartAgentRolloutRequest;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.output.*;
import uz.murodjon.robotcallv2.aiagent.domain.entity.*;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentEvalMode;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentEvalRunStatus;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentVersionStatus;
import uz.murodjon.robotcallv2.aiagent.domain.service.AiAgentSnapshot;
import uz.murodjon.robotcallv2.aiagent.infrastructure.config.AgentEvalProperties;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelConfigUseCase;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;
import uz.murodjon.robotcallv2.shared.exception.*;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.SipTrunkUseCase;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiAgentVersionServiceTest {

    private static final long COMPANY_ID = 1L;
    private static final long AGENT_ID = 7L;
    private static final long PUBLISHER_ID = 5L;

    private AiAgentRepository repository;
    private AiAgentVersionRepository versionRepository;
    private AgentRolloutRepository rolloutRepository;
    private UserUseCase userUseCase;
    private AuditUseCase auditUseCase;
    private CompanyConfigUseCase companyConfigUseCase;
    private AgentTestCaseRepository testCaseRepository;
    private AgentEvalRepository evalRepository;
    private AiAgentDrafts drafts;
    private CurrentUser currentUser;
    private AiAgentVersionService service;

    @BeforeEach
    void setUp() {
        repository = mock(AiAgentRepository.class);
        versionRepository = mock(AiAgentVersionRepository.class);
        rolloutRepository = mock(AgentRolloutRepository.class);
        userUseCase = mock(UserUseCase.class);
        auditUseCase = mock(AuditUseCase.class);
        companyConfigUseCase = mock(CompanyConfigUseCase.class);
        testCaseRepository = mock(AgentTestCaseRepository.class);
        evalRepository = mock(AgentEvalRepository.class);
        currentUser = mock(CurrentUser.class);
        when(currentUser.id()).thenReturn(Optional.of(PUBLISHER_ID));
        drafts = new AiAgentDrafts(repository, versionRepository,
                mock(AiModelConfigUseCase.class), mock(SipTrunkUseCase.class), companyConfigUseCase);
        service = serviceWithGate(null);
        when(userUseCase.findNamesByIds(COMPANY_ID, List.of(PUBLISHER_ID))).thenReturn(Map.of(PUBLISHER_ID, "Aziz"));
    }

    private AiAgentVersionService serviceWithGate(AgentEvalMode gate) {
        return new AiAgentVersionService(versionRepository, drafts, userUseCase, auditUseCase, currentUser,
                testCaseRepository, evalRepository, mock(AiAgentUseCase.class), new AgentEvalProperties(gate),
                rolloutRepository);
    }

    /** A finished draft run over {@code agent}'s configuration (no scenario resolved — the mock returns null). */
    private static AgentEvalRun draftRun(AiAgent agent, AgentEvalMode mode, int passed, int total) {
        return new AgentEvalRun(1L, COMPANY_ID, AGENT_ID, null, mode, AiAgentSnapshot.fingerprint(agent, null),
                AgentEvalRunStatus.DONE, total, passed, null, null, Instant.now(), Instant.now(), 1);
    }

    @Test
    void withTheGateOnADraftThatNeverPassedAnEvaluationIsNotPublished() {
        AiAgent draft = agent();
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(draft);
        when(testCaseRepository.findByAgent(COMPANY_ID, AGENT_ID)).thenReturn(List.of(mock(AgentTestCase.class)));
        when(evalRepository.findRuns(eq(COMPANY_ID), eq(AGENT_ID), anyInt()))
                .thenReturn(List.of(draftRun(draft, AgentEvalMode.TEXT, 4, 5)));

        assertRefused(() -> serviceWithGate(AgentEvalMode.TEXT).publishAgent(COMPANY_ID, AGENT_ID, null),
                ConflictException.class, ErrorCode.AI_AGENT_EVAL_REQUIRED);
        verify(versionRepository, never()).publish(any());
    }

    @Test
    void aVoiceGateDoesNotAcceptATextRun() {
        AiAgent draft = agent();
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(draft);
        when(testCaseRepository.findByAgent(COMPANY_ID, AGENT_ID)).thenReturn(List.of(mock(AgentTestCase.class)));
        when(evalRepository.findRuns(eq(COMPANY_ID), eq(AGENT_ID), anyInt()))
                .thenReturn(List.of(draftRun(draft, AgentEvalMode.TEXT, 5, 5)));

        assertRefused(() -> serviceWithGate(AgentEvalMode.VOICE).publishAgent(COMPANY_ID, AGENT_ID, null),
                ConflictException.class, ErrorCode.AI_AGENT_EVAL_REQUIRED);
    }

    @Test
    void aRunOverAnEarlierConfigurationDoesNotCount() {
        AiAgent draft = agent();
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(draft);
        when(testCaseRepository.findByAgent(COMPANY_ID, AGENT_ID)).thenReturn(List.of(mock(AgentTestCase.class)));
        when(evalRepository.findRuns(eq(COMPANY_ID), eq(AGENT_ID), anyInt()))
                .thenReturn(List.of(draftRun(draft.withEnabled(true).withFirstMessage("Eski salom"), AgentEvalMode.VOICE, 5, 5)));

        assertRefused(() -> serviceWithGate(AgentEvalMode.VOICE).publishAgent(COMPANY_ID, AGENT_ID, null),
                ConflictException.class, ErrorCode.AI_AGENT_EVAL_REQUIRED);
    }

    @Test
    void aPassingVoiceRunOfTheCurrentDraftLetsItGoLive() {
        AiAgent draft = agent();
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(draft);
        when(testCaseRepository.findByAgent(COMPANY_ID, AGENT_ID)).thenReturn(List.of(mock(AgentTestCase.class)));
        when(evalRepository.findRuns(eq(COMPANY_ID), eq(AGENT_ID), anyInt()))
                .thenReturn(List.of(draftRun(draft, AgentEvalMode.VOICE, 5, 5)));
        when(versionRepository.publish(any())).thenReturn(stored(1, draft, null, PUBLISHER_ID));

        assertThat(serviceWithGate(AgentEvalMode.VOICE).publishAgent(COMPANY_ID, AGENT_ID, null).version()).isEqualTo(1);
    }

    private static AiAgent agent() {
        return AiAgentFixtures.agent(AGENT_ID, COMPANY_ID, 3L, "uz-UZ", null);
    }

    /** A version as the adapter hands it back: number and timestamp filled in, the lock counter gone. */
    private static AiAgentVersion stored(int number, AiAgent snapshot, String note, Long publishedBy) {
        return new AiAgentVersion(100L + number, AGENT_ID, COMPANY_ID, number, AgentVersionStatus.PUBLISHED,
                snapshot.withPublishedVersion(number).withVersion(0), note, Instant.parse("2026-09-01T10:00:00Z"),
                publishedBy);
    }

    @Test
    void publishAgentStoresTheDraftAsItsFirstVersion() {
        AiAgent draft = agent().withVersion(4);
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(draft);
        when(versionRepository.publish(any())).thenReturn(stored(1, draft, "Birinchi", PUBLISHER_ID));

        AiAgentVersionRow row = service.publishAgent(COMPANY_ID, AGENT_ID, new PublishAiAgentRequest("  Birinchi  "));

        ArgumentCaptor<AiAgentVersion> written = ArgumentCaptor.forClass(AiAgentVersion.class);
        verify(versionRepository).publish(written.capture());
        assertThat(written.getValue().snapshot()).isEqualTo(draft);
        assertThat(written.getValue().note()).isEqualTo("Birinchi");
        assertThat(written.getValue().publishedBy()).isEqualTo(PUBLISHER_ID);
        assertThat(written.getValue().status()).isEqualTo(AgentVersionStatus.PUBLISHED);
        assertThat(row.version()).isEqualTo(1);
        assertThat(row.publishedByName()).isEqualTo("Aziz");
        assertThat(row.calls()).isZero();
        verify(auditUseCase).record(COMPANY_ID, "AI_AGENT_PUBLISH", "ai_agent", String.valueOf(AGENT_ID), "v1");
    }

    /** Saved five times since, nothing changed: the lock counter is not part of what a version stores. */
    @Test
    void publishingADraftSavedAgainWithNothingChangedIs409() {
        AiAgent draft = agent().withVersion(9);
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(draft);
        when(versionRepository.findPublished(COMPANY_ID, AGENT_ID)).thenReturn(stored(2, draft.withVersion(4), null, PUBLISHER_ID));

        assertRefused(() -> service.publishAgent(COMPANY_ID, AGENT_ID, null),
                ConflictException.class, ErrorCode.AI_AGENT_NOTHING_TO_PUBLISH);
        verify(versionRepository, never()).publish(any());
    }

    @Test
    void theOnOffSwitchIsNotVersionedSoTurningItOffIsNothingToPublish() {
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(agent().withEnabled(false));
        when(versionRepository.findPublished(COMPANY_ID, AGENT_ID)).thenReturn(stored(1, agent(), null, PUBLISHER_ID));

        assertRefused(() -> service.publishAgent(COMPANY_ID, AGENT_ID, null),
                ConflictException.class, ErrorCode.AI_AGENT_NOTHING_TO_PUBLISH);
    }

    @Test
    void aChangedDraftGoesLiveAsTheNextNumber() {
        AiAgent draft = agent().withFirstMessage("Yangi salom");
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(draft);
        when(versionRepository.findPublished(COMPANY_ID, AGENT_ID)).thenReturn(stored(2, agent(), null, PUBLISHER_ID));
        when(versionRepository.publish(any())).thenReturn(stored(3, draft, null, PUBLISHER_ID));

        AiAgentVersionRow row = service.publishAgent(COMPANY_ID, AGENT_ID, null);

        assertThat(row.version()).isEqualTo(3);
        verify(versionRepository).publish(argThat(v -> v.note() == null && v.snapshot().equals(draft)));
    }

    /**
     * The old snapshot becomes the draft again, written at the draft row's lock version and
     * with the draft's on/off switch, and goes live under a new number — history is append-only.
     */
    @Test
    void rollbackRestoresTheOldSnapshotAsTheDraftAndPublishesItAsANewNumber() {
        AiAgent draft = agent().withFirstMessage("Yangi salom").withEnabled(false).withVersion(6);
        AiAgent old = agent().withFirstMessage("Eski salom");
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(draft);
        when(versionRepository.findByAgentIdAndVersion(COMPANY_ID, AGENT_ID, 2)).thenReturn(stored(2, old, "v2", null));
        when(repository.update(eq(COMPANY_ID), eq(AGENT_ID), any())).thenReturn(true);
        when(versionRepository.publish(any())).thenReturn(stored(4, old, "rollback to v2", PUBLISHER_ID));

        AiAgentVersionRow row = service.rollbackAgent(COMPANY_ID, AGENT_ID, 2);

        ArgumentCaptor<AiAgent> restored = ArgumentCaptor.forClass(AiAgent.class);
        verify(repository).update(eq(COMPANY_ID), eq(AGENT_ID), restored.capture());
        assertThat(restored.getValue().script().firstMessage()).isEqualTo("Eski salom");
        assertThat(restored.getValue().version()).isEqualTo(6L);
        assertThat(restored.getValue().publishedVersion()).isNull();
        assertThat(restored.getValue().enabled()).isFalse();
        ArgumentCaptor<AiAgentVersion> published = ArgumentCaptor.forClass(AiAgentVersion.class);
        verify(versionRepository).publish(published.capture());
        assertThat(published.getValue().snapshot()).isEqualTo(restored.getValue());
        assertThat(published.getValue().note()).isEqualTo("rollback to v2");
        assertThat(row.version()).isEqualTo(4);
        verify(auditUseCase).record(COMPANY_ID, "AI_AGENT_ROLLBACK", "ai_agent", String.valueOf(AGENT_ID), "v2 -> v4");
    }

    @Test
    void rollbackOverAnEditMadeMeanwhileIs409AndPublishesNothing() {
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(agent().withVersion(6));
        when(versionRepository.findByAgentIdAndVersion(COMPANY_ID, AGENT_ID, 2)).thenReturn(stored(2, agent(), null, null));
        when(repository.update(eq(COMPANY_ID), eq(AGENT_ID), any())).thenReturn(false);

        assertRefused(() -> service.rollbackAgent(COMPANY_ID, AGENT_ID, 2),
                ConflictException.class, ErrorCode.ENTITY_VERSION_CONFLICT);
        verify(versionRepository, never()).publish(any());
    }

    /** The company may have withdrawn the human-like persona since that version went live. */
    @Test
    void rollbackToAHumanLikeVersionIsRefusedOnceTheCompanyMayNoLongerUseOne() {
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(agent().withVersion(6));
        when(versionRepository.findByAgentIdAndVersion(COMPANY_ID, AGENT_ID, 2))
                .thenReturn(stored(2, AiAgentFixtures.withPersona(agent(), AgentPersona.HUMAN_LIKE), null, null));
        when(companyConfigUseCase.find(COMPANY_ID)).thenReturn(CompanyConfig.defaults());

        assertRefused(() -> service.rollbackAgent(COMPANY_ID, AGENT_ID, 2),
                ConflictException.class, ErrorCode.AI_AGENT_HUMAN_LIKE_NOT_ALLOWED);
        verify(repository, never()).update(anyLong(), anyLong(), any());
        verify(versionRepository, never()).publish(any());
    }

    @Test
    void rollbackToAVersionThatDoesNotExistIsNotFound() {
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(agent());

        assertRefused(() -> service.rollbackAgent(COMPANY_ID, AGENT_ID, 9),
                NotFoundException.class, ErrorCode.AI_AGENT_VERSION_NOT_FOUND);
        verify(repository, never()).update(anyLong(), anyLong(), any());
    }

    @Test
    void publishingAnotherCompanysAgentIsNotFound() {
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(agent());

        assertRefused(() -> service.publishAgent(2L, AGENT_ID, null), NotFoundException.class, ErrorCode.AI_AGENT_NOT_FOUND);
        verify(versionRepository, never()).publish(any());
    }

    @Test
    void findVersionsNamesThePublisherAndCountsCallsPerVersion() {
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(agent());
        when(versionRepository.findByAgentId(COMPANY_ID, AGENT_ID)).thenReturn(List.of(
                stored(2, agent(), "Ikkinchi", PUBLISHER_ID), stored(1, agent(), null, null)));
        when(userUseCase.findNamesByIds(COMPANY_ID, Set.of(PUBLISHER_ID))).thenReturn(Map.of(PUBLISHER_ID, "Aziz"));
        when(versionRepository.findCallStats(COMPANY_ID, AGENT_ID)).thenReturn(List.of(new AgentVersionStats(2, 10, 3)));

        assertThat(service.findVersions(COMPANY_ID, AGENT_ID))
                .extracting(AiAgentVersionRow::version, AiAgentVersionRow::publishedByName,
                        AiAgentVersionRow::calls, AiAgentVersionRow::conversions)
                .containsExactly(tuple(2, "Aziz", 10L, 3L), tuple(1, null, 0L, 0L));
    }

    // --- rollout

    @Test
    void aPublishIsRefusedWhileARolloutRuns() {
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(agent());
        when(rolloutRepository.findByAgentId(COMPANY_ID, AGENT_ID)).thenReturn(rollout(2, 10));

        assertRefused(() -> service.publishAgent(COMPANY_ID, AGENT_ID, null),
                ConflictException.class, ErrorCode.AGENT_ROLLOUT_ACTIVE);
        verify(versionRepository, never()).publish(any());
    }

    @Test
    void aRolloutPublishesTheDraftAsACandidateBesideTheLiveVersion() {
        AiAgent draft = agent().withFirstMessage("Yangi salom");
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(draft);
        when(versionRepository.findPublished(COMPANY_ID, AGENT_ID)).thenReturn(stored(1, agent(), null, PUBLISHER_ID));
        when(versionRepository.createCandidate(any())).thenReturn(stored(2, draft, "yangi salom", PUBLISHER_ID));
        when(rolloutRepository.create(any())).thenReturn(rollout(2, 10));
        when(versionRepository.findCallStatsSince(eq(COMPANY_ID), eq(AGENT_ID), any()))
                .thenReturn(List.of(new AgentVersionStats(1, 90, 27), new AgentVersionStats(2, 10, 4)));

        AgentRolloutRow row = service.startRollout(COMPANY_ID, AGENT_ID,
                new StartAgentRolloutRequest("yangi salom", 10, List.of("90 123 45 67")));

        assertThat(row.liveVersion()).isEqualTo(1);
        assertThat(row.candidateVersion()).isEqualTo(2);
        assertThat(row.candidate().conversionRate()).isEqualTo(40.0);
        verify(versionRepository, never()).publish(any());
        ArgumentCaptor<AgentRollout> created = ArgumentCaptor.forClass(AgentRollout.class);
        verify(rolloutRepository).create(created.capture());
        assertThat(created.getValue().stagingPhones()).containsExactly("+998901234567");
    }

    @Test
    void aRolloutThatSendsNobodyToTheCandidateIsRefused() {
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(agent());

        assertRefused(() -> service.startRollout(COMPANY_ID, AGENT_ID, new StartAgentRolloutRequest(null, 0, null)),
                ValidationException.class, ErrorCode.AGENT_ROLLOUT_INVALID);
        verify(versionRepository, never()).createCandidate(any());
    }

    private static AgentRollout rollout(int candidateVersion, int trafficPercent) {
        return new AgentRollout(AGENT_ID, COMPANY_ID, candidateVersion, trafficPercent, List.of(),
                Instant.parse("2026-10-09T10:00:00Z"), PUBLISHER_ID);
    }

    private static <E extends AppException> void assertRefused(ThrowingCallable call, Class<E> type, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(type, e -> assertThat(e.code()).isEqualTo(code));
    }
}
