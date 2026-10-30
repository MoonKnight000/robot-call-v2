package uz.murodjon.robotcallv2.agent.livekit;

import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunkCredentials;

import java.util.function.Supplier;

/**
 * One way out for an outbound LiveKit call: a trunk to ring over.
 *
 * @param credentials the company's own trunk, sent inline with the call; null is the platform's
 *                    LiveKit trunk ({@code LIVEKIT_SIP_TRUNK_ID})
 * @param callerId    asked when this route is tried, not before: a caller id taken from a pool
 *                    is spent only by a route that is actually dialled
 */
public record LiveKitRoute(SipTrunkCredentials credentials, Supplier<String> callerId) {
}
