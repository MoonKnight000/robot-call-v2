package uz.murodjon.robotcallv2.operator.application.port.input;

import uz.murodjon.robotcallv2.operator.application.dto.*;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorFilter;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorPresence;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipAccount;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.stream.EventSink;
import uz.murodjon.robotcallv2.shared.stream.EventSubscription;

public interface OperatorAccountUseCase {

    OperatorSipAccountRow saveSipAccount(long companyId, long userId, SaveOperatorSipAccountRequest request);

    SipCredentialsResponse resetSipPassword(long companyId, long userId);

    void deleteSipAccount(long companyId, long userId);

    PageableData<OperatorRow> listOperators(long companyId, OperatorFilter filter);

    SipCredentialsResponse findMyCredentials(long companyId);

    OperatorPresence findMyStatus(long companyId);

    OperatorPresence updateMyStatus(long companyId, UpdateOperatorStatusRequest request);

    /** The signed-in operator's panel events, until the subscription is cancelled. */
    EventSubscription subscribeMyEvents(long companyId, EventSink sink);

    /** The user's SIP account, or null — what the call path checks before ringing an operator. */
    OperatorSipAccount findSipAccount(long companyId, long userId);

    /** The user a PJSIP endpoint ({@code op-<userId>}) belongs to, or null for any other endpoint. */
    Long findUserIdByEndpoint(String endpointName);

    /** The account behind a PJSIP endpoint ({@code op-<userId>}), in whichever company; null for any other endpoint. */
    OperatorSipAccount findSipAccountByEndpoint(String endpointName);
}
