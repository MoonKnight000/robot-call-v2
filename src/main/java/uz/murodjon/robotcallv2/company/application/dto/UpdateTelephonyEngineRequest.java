package uz.murodjon.robotcallv2.company.application.dto;

import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.shared.dialog.TelephonyEngine;

/**
 * The media stack the platform runs a company's calls on. Platform-only: the company adds its
 * own trunks, but neither sees nor chooses the engine under them.
 */
public record UpdateTelephonyEngineRequest(@NotNull TelephonyEngine telephonyEngine) {
}
