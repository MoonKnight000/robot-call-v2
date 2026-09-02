package uz.murodjon.robotcallv2.callrecord.application.dto;

public record SayResponse(
        String channelId,
        String text,
        String language,
        String playbackId
) {
}
