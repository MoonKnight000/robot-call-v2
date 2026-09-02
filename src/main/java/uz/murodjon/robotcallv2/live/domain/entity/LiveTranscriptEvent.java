package uz.murodjon.robotcallv2.live.domain.entity;

/**
 * One transcript line, pushed the moment it is spoken/recognized, for the "Jonli
 * transkript" fade-in on a live-call card (§10.3, §11.8).
 *
 * @param channelId   the live call this line belongs to
 * @param role        {@code "CLIENT"} or {@code "AGENT"}, matching {@code call_transcript.role}
 * @param text        the recognized/spoken text
 * @param dialogState the FSM state the call was in when this line was produced
 */
public record LiveTranscriptEvent(String channelId, String role, String text, String dialogState) {
}

