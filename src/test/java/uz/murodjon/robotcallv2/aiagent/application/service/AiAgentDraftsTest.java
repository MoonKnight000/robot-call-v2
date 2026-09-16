package uz.murodjon.robotcallv2.aiagent.application.service;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AiAgentRepository;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AiAgentVersionRepository;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentWebhookConfig;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentLimits;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelConfigUseCase;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModelConfig;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;
import uz.murodjon.robotcallv2.shared.exception.AppException;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.SipTrunkUseCase;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AiAgentDraftsTest {

    private static final long COMPANY_ID = 1L;
    private static final long AGENT_ID = 7L;

    private AiAgentRepository repository;
    private AiAgentVersionRepository versionRepository;
    private AiModelConfigUseCase aiModelConfigUseCase;
    private SipTrunkUseCase sipTrunkUseCase;
    private CompanyConfigUseCase companyConfigUseCase;
    private AiAgentDrafts drafts;

    @BeforeEach
    void setUp() {
        repository = mock(AiAgentRepository.class);
        versionRepository = mock(AiAgentVersionRepository.class);
        aiModelConfigUseCase = mock(AiModelConfigUseCase.class);
        sipTrunkUseCase = mock(SipTrunkUseCase.class);
        companyConfigUseCase = mock(CompanyConfigUseCase.class);
        drafts = new AiAgentDrafts(repository, versionRepository, aiModelConfigUseCase, sipTrunkUseCase,
                companyConfigUseCase);
    }

    @Test
    void strippingPersonalDataWhileKeepingTheRecordingIsRefused() {
        assertThatThrownBy(() -> AiAgentDrafts.requireCompatibleDataPolicy(true, true))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ZERO_PII_STORE_AUDIO_CONFLICT));
        assertThatCode(() -> AiAgentDrafts.requireCompatibleDataPolicy(true, false)).doesNotThrowAnyException();
        assertThatCode(() -> AiAgentDrafts.requireCompatibleDataPolicy(false, true)).doesNotThrowAnyException();
    }

    /** Only a human-like persona is the company's to allow; the assistant persona never asks. */
    @Test
    void aHumanLikePersonaIsRefusedWhenTheCompanyMayNotUseOne() {
        when(companyConfigUseCase.find(COMPANY_ID)).thenReturn(CompanyConfig.defaults());

        assertThatCode(() -> drafts.requirePersonaAllowed(COMPANY_ID, AgentPersona.AI_ASSISTANT))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> drafts.requirePersonaAllowed(COMPANY_ID, AgentPersona.HUMAN_LIKE))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AI_AGENT_HUMAN_LIKE_NOT_ALLOWED));
    }

    /** Allows calls of up to 600 seconds and dials over no trunk of its own. */
    private static AiAgent agent() {
        return AiAgentFixtures.agent(AGENT_ID, COMPANY_ID, 3L, "uz-UZ", null);
    }

    private static AgentWebhookConfig webhook(String url) {
        return new AgentWebhookConfig(url, "POST", null, null, null, null);
    }

    @Test
    void anAgentOfAnotherCompanyIsNotFound() {
        when(repository.findByCompanyIdAndId(COMPANY_ID, AGENT_ID)).thenReturn(agent());

        assertThat(drafts.requireAgent(COMPANY_ID, AGENT_ID)).isEqualTo(agent());
        assertRefused(() -> drafts.requireAgent(2L, AGENT_ID), NotFoundException.class, ErrorCode.AI_AGENT_NOT_FOUND);
    }

    @Test
    void aVersionThatWasNeverPublishedIsNotFound() {
        assertRefused(() -> drafts.requireVersion(COMPANY_ID, AGENT_ID, 4),
                NotFoundException.class, ErrorCode.AI_AGENT_VERSION_NOT_FOUND);
    }

    @Test
    void updateWritesTheDraftAtTheVersionItWasRead() {
        when(repository.update(eq(COMPANY_ID), eq(AGENT_ID), any())).thenReturn(true);

        drafts.update(COMPANY_ID, AGENT_ID, agent().withVersion(5));

        verify(repository).update(COMPANY_ID, AGENT_ID, agent().withVersion(5));
    }

    @Test
    void updateOverASaveMadeSinceTheReadIs409() {
        when(repository.update(eq(COMPANY_ID), eq(AGENT_ID), any())).thenReturn(false);

        assertRefused(() -> drafts.update(COMPANY_ID, AGENT_ID, agent().withVersion(5)),
                ConflictException.class, ErrorCode.ENTITY_VERSION_CONFLICT);
    }

    @Test
    void aDurationAboveTheCompanyCallCapIsRefusedBeforeAnythingIsWritten() {
        when(aiModelConfigUseCase.findByCompanyId(COMPANY_ID))
                .thenReturn(AiModelConfig.overrides(null, null, null, 300, null));

        assertRefused(() -> drafts.update(COMPANY_ID, AGENT_ID, agent()),
                ValidationException.class, ErrorCode.AI_AGENT_LIMIT_EXCEEDS_COMPANY_CAP);
        verify(repository, never()).update(anyLong(), anyLong(), any());
    }

    @Test
    void aCompanyWithoutACallCapAcceptsTheAgentsOwnDuration() {
        AiAgent agent = agent().withLimits(new AiAgentLimits(7200, null, null, null, null));

        assertThat(drafts.validate(COMPANY_ID, agent)).isSameAs(agent);
    }

    /** The trunk ids are stored as sent, so an agent could otherwise name another tenant's trunk. */
    @Test
    void everyTrunkTheAgentMayDialOverHasToBeTheCompanysOwn() {
        when(sipTrunkUseCase.requireTrunk(COMPANY_ID, 9L))
                .thenThrow(new NotFoundException(ErrorCode.SIP_TRUNK_NOT_FOUND, 9L));
        AiAgent agent = AiAgentFixtures.withSipTrunkIds(agent(), Set.of(9L));

        assertRefused(() -> drafts.update(COMPANY_ID, AGENT_ID, agent),
                NotFoundException.class, ErrorCode.SIP_TRUNK_NOT_FOUND);
        verify(repository, never()).update(anyLong(), anyLong(), any());
    }

    @Test
    void aTransferQueueOfAnotherCompanyIsNotFound() {
        when(repository.existsCallQueueByCompanyIdAndId(COMPANY_ID, 5L)).thenReturn(true);

        assertThatCode(() -> drafts.requireOwnQueue(COMPANY_ID, 5L)).doesNotThrowAnyException();
        assertRefused(() -> drafts.requireOwnQueue(COMPANY_ID, 99L),
                NotFoundException.class, ErrorCode.CALL_QUEUE_NOT_FOUND);
    }

    @Test
    void noTransferQueueIsNothingToLookUp() {
        drafts.requireOwnQueue(COMPANY_ID, null);

        verifyNoInteractions(repository);
    }

    @Test
    void aWebhookAimedAtThisNetworkIsRefusedWhileTheFormIsOpen() {
        assertRefused(() -> AiAgentDrafts.requireCallableWebhookUrls(webhook("http://127.0.0.1:8080/hook"), null),
                ValidationException.class, ErrorCode.WEBHOOK_URL_INVALID);
        assertRefused(() -> AiAgentDrafts.requireCallableWebhookUrls(null, webhook("http://10.0.0.5/after-call")),
                ValidationException.class, ErrorCode.WEBHOOK_URL_INVALID);
    }

    /** A {@code {{secrets.KEY}}} address is not a URL yet; the executor screens what it resolves to. */
    @Test
    void aWebhookStillHoldingASecretPlaceholderIsLeftToDeliveryTime() {
        assertThatCode(() -> AiAgentDrafts.requireCallableWebhookUrls(
                webhook("{{secrets.CRM_URL}}/hook"), webhook(" "))).doesNotThrowAnyException();
    }

    private static <E extends AppException> void assertRefused(ThrowingCallable call, Class<E> type, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(type, e -> assertThat(e.code()).isEqualTo(code));
    }
}
