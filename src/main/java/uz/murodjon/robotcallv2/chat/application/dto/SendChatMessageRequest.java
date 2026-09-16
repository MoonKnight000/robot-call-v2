package uz.murodjon.robotcallv2.chat.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * What a visitor typed.
 *
 * @param text the message; capped because an open endpoint is where a novel gets pasted,
 *             and every character of it is paid for in model tokens
 */
public record SendChatMessageRequest(@NotBlank @Size(max = 2000) String text) {
}
