package uz.murodjon.robotcallv2.live.application.dto;

/** Acknowledgement for a whisper, takeover or scenario switch on a live channel. */
public record LiveControlResponse(String channelId, String status) {

    public static LiveControlResponse whisperInjected(String channelId) {
        return new LiveControlResponse(channelId, "WHISPER_INJECTED");
    }

    public static LiveControlResponse takeoverTriggered(String channelId) {
        return new LiveControlResponse(channelId, "TAKEOVER_TRIGGERED");
    }

    public static LiveControlResponse scenarioSwitched(String channelId) {
        return new LiveControlResponse(channelId, "SCENARIO_SWITCHED");
    }
}
