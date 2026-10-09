package uz.murodjon.robotcallv2.live.application.port.output;

import uz.murodjon.robotcallv2.live.domain.entity.LiveRelayEvent;

import java.util.function.Consumer;

/**
 * Carries live events between the instances of a cluster, so a supervisor's stream sees a
 * call whichever instance runs it.
 */
public interface LiveEventRelayPort {

    /** To every other instance. Never blocks — media threads publish. */
    void publish(LiveRelayEvent event);

    /** {@code consumer} receives every event another instance published. */
    void subscribe(Consumer<LiveRelayEvent> consumer);
}
