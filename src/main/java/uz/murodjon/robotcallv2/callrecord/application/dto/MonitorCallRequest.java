package uz.murodjon.robotcallv2.callrecord.application.dto;

import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.callrecord.domain.enums.MonitorMode;

/** Body of POST /api/calls/{channelId}/monitor. */
public record MonitorCallRequest(@NotNull MonitorMode mode) {
}
