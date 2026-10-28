package uz.murodjon.robotcallv2.inbound.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import uz.murodjon.robotcallv2.inbound.domain.enums.InboundAfterHoursAction;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

import java.time.LocalTime;

public record CreateInboundRouteRequest(
        @NotBlank @Pattern(regexp = PhoneNumbers.E164_REGEX) String didNumber,
        Long aiAgentId,
        InboundAfterHoursAction afterHoursAction,
        String afterHoursDestination,

        @Schema(type = "string", pattern = DateTimeProperties.TIME_PATTERN, example = "09:00:00")
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN)
        LocalTime businessHoursStart,
        @Schema(type = "string", pattern = DateTimeProperties.TIME_PATTERN, example = "18:00:00")
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN)
        LocalTime businessHoursEnd,
        String fallbackMessage,
        Long callbackCampaignId,
        Long scheduleId,
        Long callFlowId
) {
}
