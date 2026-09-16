package uz.murodjon.robotcallv2.aiagent.application.dto;

import jakarta.validation.constraints.Size;

/** @param note what this version changes, shown in the history; optional */
public record PublishAiAgentRequest(
        @Size(max = 500) String note
) {
}
