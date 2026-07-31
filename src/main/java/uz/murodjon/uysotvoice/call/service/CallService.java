package uz.murodjon.uysotvoice.call.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.agent.ari.AriService;
import uz.murodjon.uysotvoice.call.dto.CallOriginateResponse;
import uz.murodjon.uysotvoice.call.dto.LiveCallRow;
import uz.murodjon.uysotvoice.call.dto.PlayResponse;
import uz.murodjon.uysotvoice.call.dto.SayResponse;

import java.util.List;

/**
 * The manual-call API's own service ({@code /api/calls}) — the operator actions for
 * placing a call by hand, watching what is live, and making a channel play or say
 * something.
 *
 * <p>It exists so the controller has a service in its own package to talk to instead of
 * reaching into {@code agent/}. {@link AriService} is the voice pipeline's stateful core,
 * driven mostly by Stasis events; this is the small slice of it the REST API is allowed to
 * reach, and the seam to put per-request concerns behind if the manual API ever grows
 * authorization or rate limiting of its own.
 */
@Service
public class CallService {

    private final AriService ari;

    public CallService(AriService ari) {
        this.ari = ari;
    }

    /** Calls currently up, for the live view. */
    public List<LiveCallRow> liveCalls() {
        return ari.liveCalls();
    }

    /** Dial {@code number} by hand, outside any campaign. */
    public CallOriginateResponse originate(String number) {
        return ari.originateManualCall(number);
    }

    /** Play a recorded file into a live channel. */
    public PlayResponse play(String channelId, String file) {
        return ari.playRecording(channelId, file);
    }

    /** Synthesize {@code text} and speak it into a live channel. */
    public SayResponse say(String channelId, String text, String language, String voice) {
        return ari.say(channelId, text, language, voice);
    }
}
