package uz.murodjon.robotcallv2.live.application.dto;

/** Acknowledgement for a whisper or takeover on a live channel. */
public record LiveControlResponse(String channelId, String status) {

    public static LiveControlResponse whisperInjected(String channelId) {
        return new LiveControlResponse(channelId, "WHISPER_INJECTED");
    }

    public static LiveControlResponse takeoverTriggered(String channelId) {
        return new LiveControlResponse(channelId, "TAKEOVER_TRIGGERED");
    }
}
