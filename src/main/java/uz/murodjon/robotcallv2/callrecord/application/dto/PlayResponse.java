package uz.murodjon.robotcallv2.callrecord.application.dto;

public record PlayResponse(
        String channelId,
        String file,
        String playbackId
) {
}
