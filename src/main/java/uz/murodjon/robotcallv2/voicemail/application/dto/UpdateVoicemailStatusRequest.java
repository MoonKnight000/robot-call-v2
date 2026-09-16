package uz.murodjon.robotcallv2.voicemail.application.dto;

import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.voicemail.domain.enums.VoicemailStatus;

/** Body of PUT /api/voicemails/{id}/status. */
public record UpdateVoicemailStatusRequest(@NotNull VoicemailStatus status) {
}
