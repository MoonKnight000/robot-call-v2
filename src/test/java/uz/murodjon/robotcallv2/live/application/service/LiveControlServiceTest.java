package uz.murodjon.robotcallv2.live.application.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.dialog.LiveCallControlHub;
import uz.murodjon.robotcallv2.agent.dialog.MultiAgentRouter;
import uz.murodjon.robotcallv2.live.application.dto.LiveControlResponse;
import uz.murodjon.robotcallv2.live.application.dto.SwitchScenarioRequest;
import uz.murodjon.robotcallv2.live.application.dto.TakeoverRequest;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorAccountUseCase;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipAccount;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;
import uz.murodjon.robotcallv2.user.domain.entity.User;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LiveControlServiceTest {

    private static final long COMPANY = 7L;
    private static final ScenarioDefinition ORDER_CONFIRM = new ScenarioDefinition(
            List.of(new StageDef("CONFIRM", "Confirm the order", List.of(), List.of())),
            List.of(), List.of(), List.of(), "Order bot", List.of(), null);

    private final LiveCallControlHub hub = mock(LiveCallControlHub.class);
    private final MultiAgentRouter router = mock(MultiAgentRouter.class);
    private final ScenarioUseCase scenarios = mock(ScenarioUseCase.class);
    private final AriService ari = mock(AriService.class);
    private final UserUseCase users = mock(UserUseCase.class);
    private final OperatorAccountUseCase accounts = mock(OperatorAccountUseCase.class);
    private final LiveControlService service = new LiveControlService(hub, router, scenarios, ari, users, accounts);

    @Test
    void switchesALiveCallToAnActiveScenarioOfTheCompany() {
        when(scenarios.requireScenario(COMPANY, 2L)).thenReturn(scenario(true, ORDER_CONFIRM));

        LiveControlResponse response = service.switchScenario(COMPANY, "chan-1", new SwitchScenarioRequest(2L));

        verify(ari).requireLiveCallOf(COMPANY, "chan-1");
        verify(router).routeToScenario("chan-1", ORDER_CONFIRM);
        assertThat(response.status()).isEqualTo("SCENARIO_SWITCHED");
    }

    @Test
    void refusesAnInactiveScenario() {
        when(scenarios.requireScenario(COMPANY, 2L)).thenReturn(scenario(false, ORDER_CONFIRM));

        assertThatThrownBy(() -> service.switchScenario(COMPANY, "chan-1", new SwitchScenarioRequest(2L)))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(router);
    }

    @Test
    void refusesAScenarioWithoutStages() {
        ScenarioDefinition empty = new ScenarioDefinition(List.of(), List.of(), List.of(), List.of(),
                "Empty", List.of(), null);
        when(scenarios.requireScenario(COMPANY, 2L)).thenReturn(scenario(true, empty));

        assertThatThrownBy(() -> service.switchScenario(COMPANY, "chan-1", new SwitchScenarioRequest(2L)))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(router);
    }

    @Test
    void leavesTheScenarioAloneWhenTheCallIsNotLiveHere() {
        doThrow(new ConflictException(ErrorCode.CALL_ON_OTHER_INSTANCE, "chan-1", "node-2"))
                .when(ari).requireLiveCallOf(COMPANY, "chan-1");

        assertThatThrownBy(() -> service.switchScenario(COMPANY, "chan-1", new SwitchScenarioRequest(2L)))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(scenarios, router);
    }

    @Test
    void takeoverRingsTheOperatorBehindTheExtension() {
        when(users.findBySipExtension(COMPANY, "105")).thenReturn(user(12L));
        when(accounts.findSipAccount(COMPANY, 12L)).thenReturn(sipAccount(12L));

        service.takeoverCall(COMPANY, "chan-1", new TakeoverRequest("105"));

        verify(ari).transferToOperator("chan-1", "op-12");
    }

    @Test
    void takeoverRefusesAnExtensionNoOperatorOfTheCompanyHas() {
        // The lookup is by company, so another tenant's 105 is simply not found here.
        assertThatThrownBy(() -> service.takeoverCall(COMPANY, "chan-1", new TakeoverRequest("105")))
                .isInstanceOf(NotFoundException.class);

        verify(ari, never()).transferToOperator(any(), any());
    }

    @Test
    void takeoverRefusesAUserWithoutASipAccount() {
        when(users.findBySipExtension(COMPANY, "105")).thenReturn(user(12L));

        assertThatThrownBy(() -> service.takeoverCall(COMPANY, "chan-1", new TakeoverRequest("105")))
                .isInstanceOf(NotFoundException.class);

        verify(ari, never()).transferToOperator(any(), any());
    }

    @Test
    void takeoverWithoutAnExtensionGoesWhereTheAgentTransfers() {
        service.takeoverCall(COMPANY, "chan-1", null);

        verify(ari).transferToOperator("chan-1", null);
        verifyNoInteractions(users, accounts);
    }

    private static Scenario scenario(boolean active, ScenarioDefinition definition) {
        return new Scenario(2L, "order-confirm", 1, "Order confirmation", null, false, active, definition,
                Instant.now(), null);
    }

    private static User user(long id) {
        return new User(id, COMPANY, "Operator", "operator" + id, null, null, 3L, "OPERATOR", "Operator",
                UserStatus.ACTIVE, null, null, null, null, null, Instant.now(), null, null, null, null, "105");
    }

    private static OperatorSipAccount sipAccount(long userId) {
        return new OperatorSipAccount(userId, COMPANY, OperatorSipAccount.endpointNameFor(userId), "encrypted",
                true, null, Instant.now(), null);
    }
}
