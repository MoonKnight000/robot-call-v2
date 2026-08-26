package uz.murodjon.uysotvoice.engine.dto;

import uz.murodjon.uysotvoice.engine.enums.PipelineMode;

/**
 * {@code PUT /api/settings/engine} body (§11). Full replace: a field left out clears
 * that override back to the process default.
 *
 * @param mode             null means {@link PipelineMode#CASCADE} — the mode every
 *                         company runs until it asks for something else
 * @param sttProvider      read only in {@code CASCADE}; validated against the providers
 *                         this build actually has
 * @param ttsProvider      read only in {@code CASCADE}
 * @param realtimeProvider read only in {@code REALTIME}
 */
public record UpdateEngineConfigRequest(
        PipelineMode mode,
        String sttProvider,
        String ttsProvider,
        String realtimeProvider
) {
}
