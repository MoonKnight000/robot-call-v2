package uz.murodjon.robotcallv2.live.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.dialog.LiveCallControlHub;
import uz.murodjon.robotcallv2.agent.dialog.MultiAgentRouter;
import uz.murodjon.robotcallv2.live.application.dto.LiveControlResponse;
import uz.murodjon.robotcallv2.live.application.dto.SwitchScenarioRequest;
import uz.murodjon.robotcallv2.live.application.dto.TakeoverRequest;
import uz.murodjon.robotcallv2.live.application.dto.WhisperRequest;
import uz.murodjon.robotcallv2.live.application.port.input.LiveControlUseCase;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorAccountUseCase;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipAccount;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;
import uz.murodjon.robotcallv2.user.domain.entity.User;

/**
 * Operator supervision of a call that is still running: whisper guidance to the bot, a
 * takeover that hands the channel to a human, or a switch to another scenario. Only for
 * the caller's own company's calls — any other channel id is answered as if there were
 * no such call.
 */
@Service
public class LiveControlService implements LiveControlUseCase {

    private final LiveCallControlHub liveCallControlHub;
    private final MultiAgentRouter multiAgentRouter;
    private final ScenarioUseCase scenarioUseCase;
    private final AriService ari;
    private final UserUseCase userUseCase;
    private final OperatorAccountUseCase operatorAccountUseCase;

    public LiveControlService(LiveCallControlHub liveCallControlHub, MultiAgentRouter multiAgentRouter,
                              ScenarioUseCase scenarioUseCase, AriService ari, UserUseCase userUseCase,
                              OperatorAccountUseCase operatorAccountUseCase) {
        this.liveCallControlHub = liveCallControlHub;
        this.multiAgentRouter = multiAgentRouter;
        this.scenarioUseCase = scenarioUseCase;
        this.ari = ari;
        this.userUseCase = userUseCase;
        this.operatorAccountUseCase = operatorAccountUseCase;
    }

    @Override
    public LiveControlResponse injectWhisper(long companyId, String channelId, WhisperRequest request) {
        ari.requireLiveCallOf(companyId, channelId);
        liveCallControlHub.injectWhisper(channelId, request.instruction());
        return LiveControlResponse.whisperInjected(channelId);
    }

    @Override
    public LiveControlResponse takeoverCall(long companyId, String channelId, TakeoverRequest request) {
        ari.requireLiveCallOf(companyId, channelId);
        String extension = request == null ? null : request.extension();
        String operatorEndpoint = extension == null || extension.isBlank()
                ? null : requireOperatorEndpoint(companyId, extension.trim());
        ari.transferToOperator(channelId, operatorEndpoint);
        return LiveControlResponse.takeoverTriggered(channelId);
    }

    @Override
    public LiveControlResponse switchScenario(long companyId, String channelId, SwitchScenarioRequest request) {
        ari.requireLiveCallOf(companyId, channelId);
        Scenario target = scenarioUseCase.requireScenario(companyId, request.scenarioId());
        if (!target.active()) {
            throw new ConflictException(ErrorCode.SCENARIO_NOT_ACTIVE, target.id());
        }
        ScenarioDefinition definition = target.definition();
        if (definition == null || definition.stages() == null || definition.stages().isEmpty()) {
            throw new ValidationException(ErrorCode.SCENARIO_HAS_NO_STAGES, target.id());
        }
        multiAgentRouter.routeToScenario(channelId, definition);
        return LiveControlResponse.scenarioSwitched(channelId);
    }

    /**
     * The SIP endpoint of the operator behind {@code extension}. Only an operator of this
     * company with a SIP account qualifies: the extension used to go into the dial string
     * as it came, so any digits reached whatever endpoint Asterisk knew by that name —
     * another tenant's phone included — while this company's operators, whose endpoints
     * are {@code op-<userId>}, could not be reached by their extension at all.
     */
    private String requireOperatorEndpoint(long companyId, String extension) {
        User operator = userUseCase.findBySipExtension(companyId, extension);
        OperatorSipAccount account = operator == null
                ? null : operatorAccountUseCase.findSipAccount(companyId, operator.id());
        if (account == null) {
            throw new NotFoundException(ErrorCode.OPERATOR_EXTENSION_NOT_FOUND, extension);
        }
        return account.endpointName();
    }
}
