package uz.murodjon.robotcallv2.live.application.port.input;

import uz.murodjon.robotcallv2.live.domain.enums.LiveEventType;
import uz.murodjon.robotcallv2.shared.stream.EventSink;
import uz.murodjon.robotcallv2.shared.stream.EventSubscription;

public interface LiveUseCase {

    /** A stream that carries only {@code companyId}'s events, until the subscription is cancelled. */
    EventSubscription subscribe(long companyId, EventSink sink);

    /** To every stream of {@code companyId}; nothing to anyone else. Never blocks — media threads call it. */
    void publish(long companyId, LiveEventType type, Object payload);
}
