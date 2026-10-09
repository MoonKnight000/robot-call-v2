package uz.murodjon.robotcallv2.wrapup.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * How the operator closes a call ({@code POST /api/calls/{callId}/wrap-up}).
 *
 * @param callbackAt when to call the client again — required when the code asks for it,
 *                   and always in the future
 */
public record WrapUpCallRequest(
        @NotNull Long wrapUpCodeId,
        @Size(max = 2000) String note,
        Instant callbackAt) {
}
