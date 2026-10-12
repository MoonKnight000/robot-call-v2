package uz.murodjon.robotcallv2.wrapup.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallAttempt;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorPresenceUseCase;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.wrapup.application.dto.WrapUpCallRequest;
import uz.murodjon.robotcallv2.wrapup.application.port.output.CallWrapUpRepository;
import uz.murodjon.robotcallv2.wrapup.application.port.output.WrapUpCodeRepository;
import uz.murodjon.robotcallv2.wrapup.domain.entity.CallWrapUp;
import uz.murodjon.robotcallv2.wrapup.domain.entity.WrapUpCode;
import uz.murodjon.robotcallv2.wrapup.domain.enums.WrapUpOutcome;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WrapUpServiceTest {

    private static final long COMPANY_ID = 1L;
    private static final long CALL_ID = 42L;
    private static final long OPERATOR_ID = 9L;
    private static final long CODE_ID = 5L;
    private static final long TARGET_ID = 300L;
    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");

    private WrapUpCodeRepository codes;
    private CallWrapUpRepository wrapUps;
    private CallAttemptUseCase calls;
    private CampaignTargetUseCase targets;
    private OperatorPresenceUseCase presence;
    private CurrentUser currentUser;
    private WrapUpService service;

    @BeforeEach
    void setUp() {
        codes = mock(WrapUpCodeRepository.class);
        wrapUps = mock(CallWrapUpRepository.class);
        calls = mock(CallAttemptUseCase.class);
        targets = mock(CampaignTargetUseCase.class);
        presence = mock(OperatorPresenceUseCase.class);
        currentUser = mock(CurrentUser.class);
        service = new WrapUpService(codes, wrapUps, calls, targets, presence, currentUser, mock(AuditUseCase.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(currentUser.id()).thenReturn(Optional.of(OPERATOR_ID));
        when(codes.find(COMPANY_ID, CODE_ID)).thenReturn(
                new WrapUpCode(CODE_ID, COMPANY_ID, "Sotildi", WrapUpOutcome.DONE, false, true, NOW));
        when(wrapUps.upsert(anyLong(), any())).thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void operatorClosesTheirCallAndIsFreedFromWrapUp() {
        when(calls.findAttemptByCompanyIdAndId(COMPANY_ID, CALL_ID)).thenReturn(call(OPERATOR_ID));

        service.recordCallWrapUp(COMPANY_ID, CALL_ID, new WrapUpCallRequest(CODE_ID, "  mamnun  ", null));

        ArgumentCaptor<CallWrapUp> written = ArgumentCaptor.forClass(CallWrapUp.class);
        verify(wrapUps).upsert(eq(COMPANY_ID), written.capture());
        assertThat(written.getValue().codeName()).isEqualTo("Sotildi");
        assertThat(written.getValue().outcome()).isEqualTo(WrapUpOutcome.DONE);
        assertThat(written.getValue().operatorUserId()).isEqualTo(OPERATOR_ID);
        assertThat(written.getValue().note()).isEqualTo("mamnun");
        verify(presence).finishWrapUp(OPERATOR_ID);
        verifyNoInteractions(targets);
    }

    /** A preview card's call settles its campaign target the way the code says. */
    @Test
    void campaignCallSettlesItsTarget() {
        Instant callbackAt = NOW.plusSeconds(3600);
        when(codes.find(COMPANY_ID, 6L)).thenReturn(
                new WrapUpCode(6L, COMPANY_ID, "Qayta qo'ng'iroq", WrapUpOutcome.RETRY, true, true, NOW));
        when(calls.findAttemptByCompanyIdAndId(COMPANY_ID, CALL_ID)).thenReturn(call(TARGET_ID, OPERATOR_ID));

        service.recordCallWrapUp(COMPANY_ID, CALL_ID, new WrapUpCallRequest(6L, null, callbackAt));

        verify(targets).settleByOperator(COMPANY_ID, TARGET_ID, TargetStatus.PENDING, callbackAt);
    }

    @Test
    void anotherOperatorsCallCannotBeWrappedUp() {
        when(calls.findAttemptByCompanyIdAndId(COMPANY_ID, CALL_ID)).thenReturn(call(77L));

        assertThatThrownBy(() -> service.recordCallWrapUp(COMPANY_ID, CALL_ID, new WrapUpCallRequest(CODE_ID, null, null)))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(wrapUps, presence);
    }

    /** Another company's call looks the same as no call at all. */
    @Test
    void unknownCallIsNotFound() {
        assertThatThrownBy(() -> service.recordCallWrapUp(COMPANY_ID, CALL_ID, new WrapUpCallRequest(CODE_ID, null, null)))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(wrapUps, presence);
    }

    private static CallAttempt call(Long operatorUserId) {
        return call(CallAttempt.NO_TARGET, operatorUserId);
    }

    private static CallAttempt call(long targetId, Long operatorUserId) {
        return new CallAttempt(CALL_ID, COMPANY_ID, targetId, "+998901110001", null, "PJSIP/x", "uz-UZ",
                NOW, NOW, NOW, 60, null, null, null, null, NOW, 0, null, operatorUserId, null);
    }
}
