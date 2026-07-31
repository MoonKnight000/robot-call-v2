package uz.murodjon.uysotvoice.campaign.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record AddTargetRequest(@Positive long clientId, @NotBlank String phone, String language, JsonNode contextData) {
}
