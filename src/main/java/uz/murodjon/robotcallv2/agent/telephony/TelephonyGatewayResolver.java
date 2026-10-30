package uz.murodjon.robotcallv2.agent.telephony;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.dialog.TelephonyEngine;

import java.util.List;

/**
 * Picks the {@link TelephonyGateway} for a call — the one place that knows which engine a
 * company uses and which engine a call id belongs to.
 */
@Component
public class TelephonyGatewayResolver {

    private final List<TelephonyGateway> gateways;
    private final TelephonyGateway fallback;

    public TelephonyGatewayResolver(List<TelephonyGateway> gateways) {
        this.gateways = List.copyOf(gateways);
        this.fallback = findByEngine(TelephonyEngine.ASTERISK);
    }

    /** The gateway of {@code engine}; every engine of {@link TelephonyEngine} has one. */
    public TelephonyGateway findByEngine(TelephonyEngine engine) {
        return gateways.stream()
                .filter(gateway -> gateway.findEngine() == engine)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no TelephonyGateway is registered for " + engine));
    }

    /** The gateway of the engine that owns {@code callId}; Asterisk when no other engine claims it. */
    public TelephonyGateway findByCallId(String callId) {
        return gateways.stream()
                .filter(gateway -> gateway.owns(callId))
                .findFirst()
                .orElse(fallback);
    }
}
