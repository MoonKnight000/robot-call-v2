package uz.murodjon.robotcallv2.profile.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateCallColumnsRequest(
        @NotNull List<String> columns
) {
}
